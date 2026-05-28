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
import org.gradle.api.provider.Property

abstract class BurstExtension @Inject constructor(objectFactory: ObjectFactory) {
  /**
   * A filter that determines which Kotlin compilations the Burst compiler plugin is applied to. By
   * default, Burst is applied to all compilations.
   */
  val compilationFilter: Property<BurstCompilationFilter> =
    objectFactory
      .property(BurstCompilationFilter::class.java)
      .convention(BurstCompilationFilter.ALL)
}
