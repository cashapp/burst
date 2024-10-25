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
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isIn
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.presetName
import org.junit.Test

class BurstGradlePluginTest {
  @Test
  fun multiplatformJvm() {
    multiplatform(
      testTaskName = "jvmTest",
      platformName = "jvm",
    )
  }

  @Test
  fun multiplatformJs() {
    multiplatform(
      testTaskName = "jsNodeTest",
      platformName = "js, node",
    )
  }

  @Test
  fun multiplatformNative() {
    // Like 'linuxX64' or 'macosArm64'.
    val platformName = HostManager.host.presetName
    multiplatform(
      testTaskName = "${platformName}Test",
      platformName = platformName,
    )
  }

  private fun multiplatform(
    testTaskName: String,
    platformName: String,
  ) {
    val projectDir = File("src/test/projects/multiplatform")

    val taskName = ":lib:$testTaskName"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(result.task(taskName)!!.outcome).isIn(*SUCCESS_OUTCOMES)

    val testResults = projectDir.resolve("lib/build/test-results")

    // There's no default specialization.
    assertThat(testResults.resolve("$testTaskName/TEST-CoffeeTest.xml").exists()).isFalse()

    // Each test class is executed normally with nothing skipped.
    with(readTestSuite(testResults.resolve("$testTaskName/TEST-CoffeeTest_Regular.xml"))) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "test_Milk[$platformName]",
        "test_None[$platformName]",
        "test_Oat[$platformName]",
      )

      val sampleSpecialization = testCases.single { it.name == "test_Milk[$platformName]" }
      assertThat(sampleSpecialization.skipped).isFalse()
    }
  }

  @Test
  fun functionParameters() {
    val projectDir = File("src/test/projects/functionParameters")

    val taskName = ":lib:test"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(result.task(taskName)!!.outcome).isIn(*SUCCESS_OUTCOMES)

    val testResults = projectDir.resolve("lib/build/test-results")
    val testXmlFile = testResults.resolve("test/TEST-CoffeeTest.xml")

    val testSuite = readTestSuite(testXmlFile)

    assertThat(testSuite.testCases.map { it.name }).containsExactlyInAnyOrder(
      "test_Decaf_Milk",
      "test_Decaf_None",
      "test_Decaf_Oat",
      "test_Double_Milk",
      "test_Double_None",
      "test_Double_Oat",
      "test_Regular_Milk",
      "test_Regular_None",
      "test_Regular_Oat",
    )

    val sampleSpecialization = testSuite.testCases.single { it.name == "test_Regular_Milk" }
    assertThat(sampleSpecialization.skipped).isFalse()
  }

  @Test
  fun abstractClasses() {
    val projectDir = File("src/test/projects/abstractClasses")

    val taskName = ":lib:test"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(result.task(taskName)!!.outcome).isIn(*SUCCESS_OUTCOMES)

    val testResults = projectDir.resolve("lib/build/test-results")
    val testXmlFile = testResults.resolve("test/TEST-CoffeeTest.xml")

    val testSuite = readTestSuite(testXmlFile)

    assertThat(testSuite.testCases.map { it.name }).containsExactlyInAnyOrder(
      "test_Milk",
      "test_None",
      "test_Oat",
    )
  }

  @Test
  fun classParameters() {
    val projectDir = File("src/test/projects/classParameters")

    val taskName = ":lib:test"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(result.task(taskName)!!.outcome).isIn(*SUCCESS_OUTCOMES)

    val testResults = projectDir.resolve("lib/build/test-results")

    // There's no default specialization.
    assertThat(testResults.resolve("test/TEST-CoffeeTest.xml").exists()).isFalse()

    val sampleTest = readTestSuite(testResults.resolve("test/TEST-CoffeeTest_Regular_Milk.xml"))
    val sampleTestTest = sampleTest.testCases.single()
    assertThat(sampleTestTest.name).isEqualTo("test")
    assertThat(sampleTestTest.skipped).isFalse()
    assertThat(sampleTest.systemOut).isEqualTo(
      """
      |set up Regular Milk
      |running Regular Milk
      |
      """.trimMargin(),
    )
  }

  @Test
  fun android() {
    val projectDir = File("src/test/projects/android")

    val testTaskName = ":lib:test"
    val androidTestTaskName = ":lib:assembleAndroidTest"
    val result = createRunner(projectDir, "clean", testTaskName, androidTestTaskName).build()
    assertThat(result.task(testTaskName)!!.outcome).isIn(*SUCCESS_OUTCOMES)
    assertThat(result.task(androidTestTaskName)!!.outcome).isIn(*SUCCESS_OUTCOMES)
  }

  @Test
  fun defaultArguments() {
    val projectDir = File("src/test/projects/defaultArguments")

    val result = createRunner(projectDir, "clean", ":lib:test").build()
    assertThat(result.task(":lib:test")!!.outcome).isIn(*SUCCESS_OUTCOMES)

    val testResults = projectDir.resolve("lib/build/test-results")

    // The original test class runs the default specialization.
    with(readTestSuite(testResults.resolve("test/TEST-CoffeeTest.xml"))) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "test",
        "test_None",
        "test_Oat",
      )

      val defaultFunction = testCases.single { it.name == "test" }
      assertThat(defaultFunction.skipped).isFalse()

      val sampleSpecialization = testCases.single { it.name == "test_Oat" }
      assertThat(sampleSpecialization.skipped).isFalse()
    }

    // No subclass is generated for the default specialization.
    assertThat(testResults.resolve("test/TEST-CoffeeTest_Regular.xml").exists()).isFalse()

    // Another test class is executed normally with nothing skipped.
    with(readTestSuite(testResults.resolve("test/TEST-CoffeeTest_Double.xml"))) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "test",
        "test_None",
        "test_Oat",
      )

      val defaultFunction = testCases.single { it.name == "test" }
      assertThat(defaultFunction.skipped).isFalse()

      val sampleSpecialization = testCases.single { it.name == "test_Oat" }
      assertThat(sampleSpecialization.skipped).isFalse()
    }
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
    val SUCCESS_OUTCOMES = arrayOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
    val versionProperty = "-PburstVersion=${System.getProperty("burstVersion")}"
  }
}
