/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.island.ohara.kafka.connector.json;

import com.island.ohara.common.annotations.Optional;
import com.island.ohara.common.data.Column;
import com.island.ohara.common.util.CommonUtils;
import com.island.ohara.kafka.connector.TaskConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka worker accept json and then unmarshal it to Map[String, String]. In most cases we can't
 * just use string to store our configuration. Hence, we needs a unified way to serialize non-string
 * type to string value. For example, number, list, and table.
 *
 * <p>The output of this formatter includes 1) TaskConfig -- used by Connector and Task 2) Request
 * of creating connector 3) Request of validating connector
 */
public final class ConnectorFormatter {
  // --------------------[KAFKA KEYs]--------------------//
  public static final String NAME_KEY = "name";

  public static ConnectorFormatter of() {
    return new ConnectorFormatter();
  }

  private final Map<String, String> settings = new HashMap<>();

  private ConnectorFormatter() {
    // ohara has custom serializeration so the json converter is useless for ohara
    converterTypeOfKey(ConverterType.NONE);
    converterTypeOfValue(ConverterType.NONE);
  }

  public ConnectorFormatter name(String name) {
    return setting(NAME_KEY, name);
  }

  public ConnectorFormatter setting(String key, String value) {
    CommonUtils.requireNonEmpty(key, () -> "key can't be either empty or null");
    CommonUtils.requireNonEmpty(
        value, () -> "it is illegal to assign empty/null value to key:" + key);
    // Kafka has specific list format so we need to convert the string list ...
    try {
      List<String> ss = StringList.ofJson(value);
      // yep, this value is in json array
      settings.put(key, StringList.toKafkaString(ss));
    } catch (IllegalArgumentException e) {
      settings.put(key, value);
    }
    return this;
  }

  public ConnectorFormatter settings(Map<String, String> settings) {
    settings.forEach(this::setting);
    return this;
  }

  public ConnectorFormatter className(String className) {
    return setting(SettingDefinition.CONNECTOR_CLASS_DEFINITION.key(), className);
  }

  public ConnectorFormatter topicName(String topicName) {
    return topicNames(Collections.singletonList(topicName));
  }

  public ConnectorFormatter topicNames(List<String> topicNames) {
    return setting(
        SettingDefinition.TOPIC_NAMES_DEFINITION.key(), StringList.toKafkaString(topicNames));
  }

  public ConnectorFormatter numberOfTasks(int numberOfTasks) {
    return setting(
        SettingDefinition.NUMBER_OF_TASKS_DEFINITION.key(), String.valueOf(numberOfTasks));
  }

  @Optional("default is ConverterType.NONE")
  public ConnectorFormatter converterTypeOfKey(ConverterType type) {
    return setting(SettingDefinition.KEY_CONVERTER_DEFINITION.key(), type.className());
  }

  @Optional("default is ConverterType.NONE")
  public ConnectorFormatter converterTypeOfValue(ConverterType type) {
    return setting(SettingDefinition.VALUE_CONVERTER_DEFINITION.key(), type.className());
  }

  public ConnectorFormatter propGroups(String key, List<PropGroup> propGroups) {
    return setting(key, PropGroups.toString(propGroups));
  }

  public ConnectorFormatter column(Column column) {
    return columns(Collections.singletonList(column));
  }

  public ConnectorFormatter columns(List<Column> columns) {
    return propGroups(SettingDefinition.COLUMNS_DEFINITION.key(), PropGroups.of(columns));
  }

  public Creation requestOfCreation() {
    return Creation.of(new HashMap<>(settings));
  }

  public Map<String, String> requestOfValidation() {
    return new HashMap<>(settings);
  }

  public TaskConfig taskConfig() {
    return TaskConfig.of(new HashMap<>(settings));
  }
}
