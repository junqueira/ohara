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

package com.island.ohara.client.configurator.v0

import java.util.Objects

/**
  * a collection of query parameter
  */
object Parameters {
  val CLUSTER_NAME: String = "cluster"

  /**
    * CLUSTER is our first query parameter. We introduce this method to append cluster parameter to url.
    */
  def appendTargetCluster(url: String, target: String): String =
    Objects.requireNonNull(url) + s"?$CLUSTER_NAME=${Objects.requireNonNull(target)}"
}
