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

package org.apache.geode.management.api;

import org.apache.geode.annotations.Experimental;
import org.apache.geode.cache.configuration.CacheElement;

/**
 * this is responsible for applying and persisting cache configuration changes on locators and/or
 * servers.
 */
@Experimental
public interface ClusterManagementService {

  /**
   * This method will create the element on all the applicable members in the cluster and persist
   * the configuration in the cluster configuration if persistence is enabled.
   *
   * @param config this holds the configuration attributes of the element to be created on the
   *        cluster
   * @param group the server group to which this config applies
   * @throws IllegalArgumentException, NoMemberException, EntityExistsException
   * @see CacheElement
   */
  ClusterManagementResult create(CacheElement config, String group);

  /**
   * This method will delete the element on all the applicable members in the cluster and update the
   * configuration in the cluster configuration if persistence is enabled.
   *
   * @param config this holds the configuration attributes of the element to be deleted on the
   *        cluster
   * @param group the server group to which this config applies
   * @throws IllegalArgumentException, NoMemberException, EntityExistsException
   * @see CacheElement
   */
  ClusterManagementResult delete(CacheElement config, String group);

  /**
   * This method will update the element on all the applicable members in the cluster and persist
   * the updated configuration in the cluster configuration if persistence is enabled.
   *
   * @param config this holds the configuration attributes of the element to be updated on the
   *        cluster
   * @param group the server group to which this config applies
   * @throws IllegalArgumentException, NoMemberException, EntityExistsException
   * @see CacheElement
   */
  ClusterManagementResult update(CacheElement config, String group);

}
