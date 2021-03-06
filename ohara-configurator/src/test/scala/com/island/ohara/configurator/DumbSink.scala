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

package com.island.ohara.configurator

import com.island.ohara.kafka.connector.{RowSinkConnector, RowSinkRecord, RowSinkTask, TaskConfig}
import scala.collection.JavaConverters._
class DumbSink extends RowSinkConnector {
  private[this] var config: TaskConfig = _
  override protected def _taskClass(): Class[_ <: RowSinkTask] = classOf[DumbSinkTask]
  override protected def _taskConfigs(maxTasks: Int): java.util.List[TaskConfig] = Seq.fill(maxTasks)(config).asJava
  override protected def _start(config: TaskConfig): Unit = {
    this.config = config
    if (config.raw().containsKey("you_should_fail")) throw new IllegalArgumentException
  }
  override protected def _stop(): Unit = {}
}

class DumbSinkTask extends RowSinkTask {
  override protected def _start(config: TaskConfig): Unit = {}

  override protected def _stop(): Unit = {}

  override protected def _put(records: java.util.List[RowSinkRecord]): Unit = {}
}
