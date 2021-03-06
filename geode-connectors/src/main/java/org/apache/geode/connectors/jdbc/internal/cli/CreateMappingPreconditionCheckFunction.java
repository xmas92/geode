/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.connectors.jdbc.internal.cli;

import java.io.ObjectInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.geode.SerializationException;
import org.apache.geode.annotations.Experimental;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.connectors.jdbc.JdbcConnectorException;
import org.apache.geode.connectors.jdbc.internal.SqlHandler.DataSourceFactory;
import org.apache.geode.connectors.jdbc.internal.TableMetaDataManager;
import org.apache.geode.connectors.jdbc.internal.TableMetaDataView;
import org.apache.geode.connectors.jdbc.internal.configuration.FieldMapping;
import org.apache.geode.connectors.jdbc.internal.configuration.RegionMapping;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.jndi.JNDIInvoker;
import org.apache.geode.management.cli.CliFunction;
import org.apache.geode.management.internal.cli.functions.CliFunctionResult;
import org.apache.geode.pdx.PdxWriter;
import org.apache.geode.pdx.ReflectionBasedAutoSerializer;
import org.apache.geode.pdx.internal.PdxField;
import org.apache.geode.pdx.internal.PdxOutputStream;
import org.apache.geode.pdx.internal.PdxType;
import org.apache.geode.pdx.internal.PdxWriterImpl;
import org.apache.geode.pdx.internal.TypeRegistry;

@Experimental
public class CreateMappingPreconditionCheckFunction extends CliFunction<RegionMapping> {

  private transient DataSourceFactory dataSourceFactory;
  private transient ClassFactory classFactory;
  private transient ReflectionBasedAutoSerializerFactory reflectionBasedAutoSerializerFactory;
  private transient PdxWriterFactory pdxWriterFactory;
  private transient TableMetaDataManager tableMetaDataManager;

  CreateMappingPreconditionCheckFunction(DataSourceFactory factory, ClassFactory classFactory,
      ReflectionBasedAutoSerializerFactory reflectionBasedAutoSerializerFactory,
      PdxWriterFactory pdxWriterFactory,
      TableMetaDataManager manager) {
    this.dataSourceFactory = factory;
    this.classFactory = classFactory;
    this.reflectionBasedAutoSerializerFactory = reflectionBasedAutoSerializerFactory;
    this.pdxWriterFactory = pdxWriterFactory;
    this.tableMetaDataManager = manager;
  }

  CreateMappingPreconditionCheckFunction() {
    this(dataSourceName -> JNDIInvoker.getDataSource(dataSourceName),
        className -> Class.forName(className),
        className -> new ReflectionBasedAutoSerializer(className),
        (typeRegistry, object) -> new PdxWriterImpl(typeRegistry, object, new PdxOutputStream()),
        new TableMetaDataManager());
  }

  // used by java during deserialization
  private void readObject(ObjectInputStream stream) {
    this.dataSourceFactory = dataSourceName -> JNDIInvoker.getDataSource(dataSourceName);
    this.classFactory = className -> Class.forName(className);
    this.reflectionBasedAutoSerializerFactory =
        className -> new ReflectionBasedAutoSerializer(className);
    this.pdxWriterFactory =
        (typeRegistry, object) -> new PdxWriterImpl(typeRegistry, object, new PdxOutputStream());
    this.tableMetaDataManager = new TableMetaDataManager();
  }

  @Override
  public CliFunctionResult executeFunction(FunctionContext<RegionMapping> context)
      throws Exception {
    RegionMapping regionMapping = context.getArguments();
    String dataSourceName = regionMapping.getDataSourceName();
    DataSource dataSource = dataSourceFactory.getDataSource(dataSourceName);
    if (dataSource == null) {
      throw new JdbcConnectorException("JDBC data-source named \"" + dataSourceName
          + "\" not found. Create it with gfsh 'create data-source --pooled --name="
          + dataSourceName + "'.");
    }
    InternalCache cache = (InternalCache) context.getCache();
    TypeRegistry typeRegistry = cache.getPdxRegistry();
    PdxType pdxType = getPdxTypeForClass(cache, typeRegistry, regionMapping.getPdxName());
    try (Connection connection = dataSource.getConnection()) {
      TableMetaDataView tableMetaData =
          tableMetaDataManager.getTableMetaDataView(connection, regionMapping);
      // TODO the table name returned in tableMetaData may be different than
      // the table name specified on the command line at this point.
      // Do we want to update the region mapping to hold the "real" table name
      Object[] output = new Object[2];
      ArrayList<FieldMapping> fieldMappings = new ArrayList<>();
      output[1] = fieldMappings;
      Set<String> columnNames = tableMetaData.getColumnNames();
      if (columnNames.size() != pdxType.getFieldCount()) {
        throw new JdbcConnectorException(
            "The table and pdx class must have the same number of columns/fields. But the table has "
                + columnNames.size()
                + " columns and the pdx class has " + pdxType.getFieldCount() + " fields.");
      }
      List<PdxField> pdxFields = pdxType.getFields();
      for (String jdbcName : columnNames) {
        boolean isNullable = tableMetaData.isColumnNullable(jdbcName);
        JDBCType jdbcType = tableMetaData.getColumnDataType(jdbcName);
        FieldMapping fieldMapping =
            createFieldMapping(jdbcName, jdbcType.getName(), isNullable, pdxFields);
        fieldMappings.add(fieldMapping);
      }
      if (regionMapping.getIds() == null || regionMapping.getIds().isEmpty()) {
        List<String> keyColumnNames = tableMetaData.getKeyColumnNames();
        output[0] = String.join(",", keyColumnNames);
      }
      String member = context.getMemberName();
      return new CliFunctionResult(member, output);
    } catch (SQLException e) {
      throw JdbcConnectorException.createException(e);
    }
  }

  private FieldMapping createFieldMapping(String jdbcName, String jdbcType, boolean jdbcNullable,
      List<PdxField> pdxFields) {
    String pdxName = null;
    String pdxType = null;
    for (PdxField pdxField : pdxFields) {
      if (pdxField.getFieldName().equals(jdbcName)) {
        pdxName = pdxField.getFieldName();
        pdxType = pdxField.getFieldType().name();
        break;
      }
    }
    if (pdxName == null) {
      // look for one inexact match
      for (PdxField pdxField : pdxFields) {
        if (pdxField.getFieldName().equalsIgnoreCase(jdbcName)) {
          if (pdxName != null) {
            throw new JdbcConnectorException(
                "More than one PDX field name matched the column name \"" + jdbcName + "\"");
          }
          pdxName = pdxField.getFieldName();
          pdxType = pdxField.getFieldType().name();
        }
      }
    }
    if (pdxName == null) {
      throw new JdbcConnectorException(
          "No PDX field name matched the column name \"" + jdbcName + "\"");
    }
    return new FieldMapping(pdxName, pdxType, jdbcName, jdbcType, jdbcNullable);
  }

  private PdxType getPdxTypeForClass(InternalCache cache, TypeRegistry typeRegistry,
      String className) {
    Class<?> clazz = loadPdxClass(className);
    PdxType result = typeRegistry.getExistingTypeForClass(clazz);
    if (result != null) {
      return result;
    }
    return generatePdxTypeForClass(cache, typeRegistry, clazz);
  }

  /**
   * Generates and returns a PdxType for the given class.
   * The generated PdxType is also stored in the TypeRegistry.
   *
   * @param cache used to generate pdx type
   * @param clazz the class to generate a PdxType for
   * @return the generated PdxType
   * @throws JdbcConnectorException if a PdxType can not be generated
   */
  private PdxType generatePdxTypeForClass(InternalCache cache, TypeRegistry typeRegistry,
      Class<?> clazz) {
    Object object = createInstance(clazz);
    try {
      cache.registerPdxMetaData(object);
    } catch (SerializationException ex) {
      String className = clazz.getName();
      ReflectionBasedAutoSerializer serializer =
          this.reflectionBasedAutoSerializerFactory.create(className);
      PdxWriter writer = this.pdxWriterFactory.create(typeRegistry, object);
      boolean result = serializer.toData(object, writer);
      if (!result) {
        throw new JdbcConnectorException(
            "Could not generate a PdxType using the ReflectionBasedAutoSerializer for the class  "
                + clazz.getName() + " after failing to register pdx metadata due to "
                + ex.getMessage() + ". Check the server log for details.");
      }
    }
    // serialization will leave the type in the registry
    return typeRegistry.getExistingTypeForClass(clazz);
  }

  private Object createInstance(Class<?> clazz) {
    try {
      Constructor<?> ctor = clazz.getConstructor();
      return ctor.newInstance(new Object[] {});
    } catch (NoSuchMethodException | SecurityException | InstantiationException
        | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
      throw new JdbcConnectorException(
          "Could not generate a PdxType for the class " + clazz.getName()
              + " because it did not have a public zero arg constructor. Details: " + ex);
    }
  }

  private Class<?> loadPdxClass(String className) {
    try {
      return this.classFactory.loadClass(className);
    } catch (ClassNotFoundException ex) {
      throw new JdbcConnectorException(
          "The pdx class \"" + className + "\" could not be loaded because: " + ex);
    }
  }

  public interface ClassFactory {
    public Class loadClass(String className) throws ClassNotFoundException;
  }
  public interface ReflectionBasedAutoSerializerFactory {
    public ReflectionBasedAutoSerializer create(String className);
  }
  public interface PdxWriterFactory {
    public PdxWriter create(TypeRegistry typeRegistry, Object object);
  }
}
