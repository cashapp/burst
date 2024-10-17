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
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
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

  private fun multiplatform(
    testTaskName: String,
    platformName: String,
  ) {
    val projectDir = File("src/test/projects/multiplatform")

    val taskName = ":lib:$testTaskName"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val testResults = projectDir.resolve("lib/build/test-results")

    // The original test class runs the default specialization.
    with(readTestSuite(testResults.resolve("$testTaskName/TEST-CoffeeTest.xml"))) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "test[$platformName]",
        "test_Milk[$platformName]",
        "test_None[$platformName]",
        "test_Oat[$platformName]",
      )

      val defaultFunction = testCases.single { it.name == "test[$platformName]" }
      assertThat(defaultFunction.skipped).isFalse()

      val defaultSpecialization = testCases.single { it.name == "test_None[$platformName]" }
      assertThat(defaultSpecialization.skipped).isTrue()

      val sampleSpecialization = testCases.single { it.name == "test_Milk[$platformName]" }
      assertThat(sampleSpecialization.skipped).isFalse()
    }

    // The default test class is completely skipped.
    with(readTestSuite(testResults.resolve("$testTaskName/TEST-CoffeeTest_Decaf.xml"))) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "test[$platformName]",
        "test_Milk[$platformName]",
        "test_None[$platformName]",
        "test_Oat[$platformName]",
      )

      val defaultFunction = testCases.single { it.name == "test[$platformName]" }
      assertThat(defaultFunction.skipped).isTrue()

      val defaultSpecialization = testCases.single { it.name == "test_None[$platformName]" }
      assertThat(defaultSpecialization.skipped).isTrue()

      val sampleSpecialization = testCases.single { it.name == "test_Milk[$platformName]" }
      assertThat(sampleSpecialization.skipped).isTrue()
    }

    // Another test class is executed normally with nothing skipped.
    with(readTestSuite(testResults.resolve("$testTaskName/TEST-CoffeeTest_Regular.xml"))) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "test[$platformName]",
        "test_Milk[$platformName]",
        "test_None[$platformName]",
        "test_Oat[$platformName]",
      )

      val defaultFunction = testCases.single { it.name == "test[$platformName]" }
      assertThat(defaultFunction.skipped).isFalse()

      val defaultSpecialization = testCases.single { it.name == "test_None[$platformName]" }
      assertThat(defaultSpecialization.skipped).isTrue()

      val sampleSpecialization = testCases.single { it.name == "test_Milk[$platformName]" }
      assertThat(sampleSpecialization.skipped).isFalse()
    }
  }

  @Test
  fun functionParameters() {
    val projectDir = File("src/test/projects/functionParameters")

    val taskName = ":lib:test"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val testResults = projectDir.resolve("lib/build/test-results")
    val testXmlFile = testResults.resolve("test/TEST-CoffeeTest.xml")

    val testSuite = readTestSuite(testXmlFile)

    assertThat(testSuite.testCases.map { it.name }).containsExactlyInAnyOrder(
      "test",
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

    val originalTest = testSuite.testCases.single { it.name == "test" }
    assertThat(originalTest.skipped).isFalse()

    val defaultSpecialization = testSuite.testCases.single { it.name == "test_Decaf_None" }
    assertThat(defaultSpecialization.skipped).isTrue()

    val sampleSpecialization = testSuite.testCases.single { it.name == "test_Regular_Milk" }
    assertThat(sampleSpecialization.skipped).isFalse()
  }

  @Test
  fun classParameters() {
    val projectDir = File("src/test/projects/classParameters")

    val taskName = ":lib:test"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val testResults = projectDir.resolve("lib/build/test-results")

    val coffeeTest = readTestSuite(testResults.resolve("test/TEST-CoffeeTest.xml"))
    val coffeeTestTest = coffeeTest.testCases.single()
    assertThat(coffeeTestTest.name).isEqualTo("test")
    assertThat(coffeeTest.systemOut).isEqualTo(
      """
      |set up Decaf None
      |running Decaf None
      |
      """.trimMargin(),
    )

    val defaultTest = readTestSuite(testResults.resolve("test/TEST-CoffeeTest_Decaf_None.xml"))
    val defaultTestTest = defaultTest.testCases.single()
    assertThat(defaultTestTest.name).isEqualTo("test")
    assertThat(defaultTestTest.skipped).isTrue()
    assertThat(defaultTest.systemOut).isEmpty()

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
