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
package org.apache.geode.internal.logging;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.geode.annotations.internal.MakeImmutable;

/**
 * Defines the common date format for GemFire and provides DateFormat instances.
 */
public class DateFormatter {

  /**
   * The format string used to format the timestamp of GemFire log messages
   */
  public static final String FORMAT_STRING = "yyyy/MM/dd HH:mm:ss.SSS z";

  @MakeImmutable("Data formatters are not thread safe!")
  private static final DateFormat TIME_FORMATTER = createDateFormat();

  /**
   * Creates a SimpleDateFormat using {@link #FORMAT_STRING}.
   *
   * Thread Safety Issue: (From SimpleDateFormat) Date formats are not synchronized. It is
   * recommended to create separate format instances for each thread. If multiple threads access a
   * format concurrently, it must be synchronized externally.
   */
  public static DateFormat createDateFormat() {
    return new SimpleDateFormat(FORMAT_STRING);
  }

  /**
   * Creates a SimpleDateFormat using specified formatString.
   */
  public static DateFormat createDateFormat(final String formatString) {
    return new SimpleDateFormat(formatString);
  }

  /**
   * Gets a String representation of the current time.
   *
   * @return a String representation of the current time.
   */
  public static String getTimeStamp() {
    return formatDate(new Date());
  }

  /**
   * Convert a Date to a timestamp String.
   *
   * @param d a Date to format as a timestamp String.
   * @return a String representation of the current time.
   */
  public static String formatDate(final Date d) {
    try {
      synchronized (TIME_FORMATTER) {
        // Need sync: see bug 21858
        return TIME_FORMATTER.format(d);
      }
    } catch (Exception e1) {
      // Fix bug 21857
      try {
        return d.toString();
      } catch (Exception e2) {
        try {
          return Long.toString(d.getTime());
        } catch (Exception e3) {
          return "timestampFormatFailed";
        }
      }
    }
  }

  /**
   * Do not instantiate this class.
   */
  private DateFormatter() {
    // do not instantiate this class
  }
}
