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
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused") // Created reflectively by Gradle.
class BurstPlugin : KotlinCompilerPluginSupportPlugin {
  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = BuildConfig.KOTLIN_PLUGIN_ID

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
    groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
    artifactId = BuildConfig.KOTLIN_PLUGIN_NAME,
    version = BuildConfig.KOTLIN_PLUGIN_VERSION,
  )

  override fun apply(target: Project) {
    super.apply(target)

    // kotlin("multiplatform")
    target.plugins.withType<KotlinMultiplatformPluginWrapper> {
      target.configure<KotlinMultiplatformExtension> {
        sourceSets {
          commonTest {
            dependencies {
              implementation("app.cash.burst:burst:$burstVersion")
            }
          }
        }
      }
    }

    // kotlin("jvm")
    target.plugins.withType<KotlinPluginWrapper> {
      target.dependencies {
        add("testImplementation", "app.cash.burst:burst:$burstVersion")
      }
    }

    // kotlin("android")
    target.plugins.withType<KotlinAndroidPluginWrapper> {
      target.dependencies {
        add("testImplementation", "app.cash.burst:burst:$burstVersion")
        add("androidTestImplementation", "app.cash.burst:burst:$burstVersion")
      }
    }

    // AGP's built-in Kotlin
    target.plugins.withType<KotlinBaseApiPlugin> {
      target.dependencies {
        add("testImplementation", "app.cash.burst:burst:$burstVersion")
        add("androidTestImplementation", "app.cash.burst:burst:$burstVersion")
      }
    }
  }

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>,
  ): Provider<List<SubpluginOption>> {
    return kotlinCompilation.target.project.provider {
      listOf() // No options.
    }
  }
}
