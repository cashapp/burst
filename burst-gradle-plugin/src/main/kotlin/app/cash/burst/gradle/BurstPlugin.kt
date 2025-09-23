/*
 * Copyright (C) 2024 Cash App
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

import app.cash.burst.gradle.BuildConfig.burstVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.hasPlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_TEST_SOURCE_SET_NAME

@Suppress("unused") // Created reflectively by Gradle.
class BurstPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    var applied = false
    target.afterEvaluate {
      check(applied) { "No suitable Kotlin configuration was found" }
    }

    target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
      applied = true
      target.pluginManager.apply(BurstKotlinPlugin::class.java)

      target.configure<KotlinBaseExtension> {
        sourceSets.getByName(COMMON_TEST_SOURCE_SET_NAME).dependencies {
          implementation("app.cash.burst:burst:$burstVersion")
        }
      }
    }

    target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
      applied = true
      target.pluginManager.apply(BurstKotlinPlugin::class.java)

      target.dependencies {
        add("testImplementation", "app.cash.burst:burst:$burstVersion")
      }
    }

    target.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
      applied = true
      target.pluginManager.apply(BurstKotlinPlugin::class.java)

      target.dependencies {
        add("testImplementation", "app.cash.burst:burst:$burstVersion")
        add("androidTestImplementation", "app.cash.burst:burst:$burstVersion")
      }
    }

    // AGP's built-in Kotlin
    target.pluginManager.withPlugin("com.android.base") {
      if (target.plugins.hasPlugin(KotlinBaseApiPlugin::class)) {
        applied = true
        target.pluginManager.apply(BurstKotlinPlugin::class.java)

        target.dependencies {
          add("testImplementation", "app.cash.burst:burst:$burstVersion")
          add("androidTestImplementation", "app.cash.burst:burst:$burstVersion")
        }
      }
    }
  }
}
