/*
 * Copyright (C) 2026 Cash App
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.burst.gradle

import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty

abstract class BurstExtension @Inject constructor(objectFactory: ObjectFactory) {
  /**
   * A list of Kotlin source sets by name which will be transformed by the Burst compiler plugin. If
   * not set or empty, all source sets will be transformed.
   */
  val includedSourceSets: SetProperty<String> =
    objectFactory.setProperty(String::class.java).convention(emptySet())
}
