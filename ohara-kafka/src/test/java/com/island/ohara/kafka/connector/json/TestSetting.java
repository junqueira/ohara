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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.island.ohara.common.rule.SmallTest;
import com.island.ohara.common.util.CommonUtils;
import java.io.IOException;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class TestSetting extends SmallTest {
  @Test
  public void testEqual() throws IOException {
    Setting config =
        Setting.of(
            SettingDefinition.builder().key(CommonUtils.randomString()).build(),
            SettingValue.of(
                CommonUtils.randomString(), CommonUtils.randomString(), Collections.emptyList()));
    ObjectMapper mapper = new ObjectMapper();
    Assert.assertEquals(
        config,
        mapper.readValue(mapper.writeValueAsString(config), new TypeReference<Setting>() {}));
  }

  @Test
  public void testGetter() {
    SettingDefinition def = SettingDefinition.builder().key(CommonUtils.randomString()).build();
    SettingValue value =
        SettingValue.of(
            CommonUtils.randomString(), CommonUtils.randomString(), Collections.emptyList());
    Setting config = Setting.of(def, value);
    Assert.assertEquals(def, config.definition());
    Assert.assertEquals(value, config.value());
  }

  @Test(expected = NullPointerException.class)
  public void nullDefinition() {
    Setting.of(
        null,
        SettingValue.of(
            CommonUtils.randomString(), CommonUtils.randomString(), Collections.emptyList()));
  }

  @Test(expected = NullPointerException.class)
  public void nullValue() {
    Setting.of(SettingDefinition.builder().key(CommonUtils.randomString()).build(), null);
  }
}
