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

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test

class BurstGradlePluginTest {
  @Test
  fun happyPath() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:jvmTest"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val testResults = projectDir.resolve("lib/build/test-results")
    val jvmTestXmlFile = testResults.resolve("jvmTest/TEST-CoffeeTest.xml")

    val testSuite = readTestSuite(jvmTestXmlFile)

    assertThat(testSuite.testCases.map { it.name }).containsExactlyInAnyOrder(
      "test[jvm]",
      "test_Decaf_Oat[jvm]",
      "test_Regular_Milk[jvm]",
      "test_Regular_None[jvm]",
      "test_Decaf_Milk[jvm]",
      "test_Decaf_None[jvm]",
      "test_Double_Milk[jvm]",
      "test_Double_None[jvm]",
      "test_Regular_Oat[jvm]",
      "test_Double_Oat[jvm]",
    )

    val originalTest = testSuite.testCases.single { it.name == "test[jvm]" }
    assertThat(originalTest.skipped).isTrue()

    val sampleVariant = testSuite.testCases.single { it.name == "test_Decaf_Oat[jvm]" }
    assertThat(sampleVariant.skipped).isFalse()
  }

  private fun createRunner(
    projectDir: File,
    vararg taskNames: String,
  ): GradleRunner {
    val gradleRoot = projectDir.resolve("gradle").also { it.mkdir() }
    File("../gradle/wrapper").copyRecursively(gradleRoot.resolve("wrapper"), true)
    File(projectDir, "kotlin-js-store/yarn.lock").delete()
    val arguments = arrayOf("--info", "--stacktrace", "--continue")
    return GradleRunner.create()
      .withProjectDir(projectDir)
      .withDebug(true) // Run in-process.
      .withArguments(*arguments, *taskNames, versionProperty)
      .forwardOutput()
  }

  companion object {
    val SUCCESS_OUTCOMES = listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
    val versionProperty = "-PburstVersion=${System.getProperty("burstVersion")}"
  }
}
