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
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import java.io.File
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
      checkKlibMetadata = true,
    )
  }

  private fun multiplatform(
    testTaskName: String,
    platformName: String,
    checkKlibMetadata: Boolean = false,
  ) {
    val tester = GradleTester("multiplatform")

    val taskName = ":lib:$testTaskName"
    tester.cleanAndBuild(taskName)

    // There's no default specialization.
    assertThat(tester.hasTestSuite("CoffeeTest", testTaskName)).isFalse()

    // Each test class is executed normally with nothing skipped.
    with(tester.readTestSuite("CoffeeTest_Regular", testTaskName)) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "basicTest_Milk[$platformName]",
        "basicTest_None[$platformName]",
        "basicTest_Oat[$platformName]",
        "coroutinesTest_Milk[$platformName]",
        "coroutinesTest_None[$platformName]",
        "coroutinesTest_Oat[$platformName]",
      )

      val sampleSpecialization = testCases.single { it.name == "basicTest_Milk[$platformName]" }
      assertThat(sampleSpecialization.skipped).isFalse()

      assertThat(systemOut).contains(
        """
        |set up Regular
        |running Regular Oat in coffeeCoroutine
        |
        """.trimMargin(),
        """
        |set up Regular
        |running Regular Oat
        |
        """.trimMargin(),
      )
    }

    if (checkKlibMetadata) {
      val klib = readKlib(
        File("src/test/projects/multiplatform").resolve("lib/build/classes/kotlin/$platformName/test/klib/lib_test"),
      )
      val klibMetadata = klib.moduleMetadata()
      val coffeeTestMetadata = klibMetadata.classes.first { it.name == "CoffeeTest" }
      assertThat(coffeeTestMetadata.functions.map { it.name }).containsExactlyInAnyOrder(
        "setUp",
        "basicTest",
        "basicTest_Milk",
        "basicTest_None",
        "basicTest_Oat",
        "coroutinesTest",
        "coroutinesTest_Milk",
        "coroutinesTest_None",
        "coroutinesTest_Oat",
      )
    }
  }

  @Test
  fun functionParameters() {
    val tester = GradleTester("functionParameters")

    val taskName = ":lib:test"
    tester.cleanAndBuild(taskName)

    val testSuite = tester.readTestSuite("CoffeeTest")

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
    val tester = GradleTester("abstractClasses")

    tester.cleanAndBuild(":lib:test")

    val testSuite = tester.readTestSuite("CoffeeTest")

    assertThat(testSuite.testCases.map { it.name }).containsExactlyInAnyOrder(
      "test_Milk",
      "test_None",
      "test_Oat",
    )
  }

  @Test
  fun classParameters() {
    val tester = GradleTester("classParameters")

    tester.cleanAndBuild(":lib:test")

    // There's no default specialization.
    assertThat(tester.hasTestSuite("CoffeeTest")).isFalse()

    val sampleTest = tester.readTestSuite("CoffeeTest_Regular_Milk")
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
    val tester = GradleTester("android")
    tester.cleanAndBuild(":lib:test", ":lib:assembleAndroidTest")
  }

  @Test
  fun androidBuiltInKotlin() {
    val tester = GradleTester("androidBuiltInKotlin")
    tester.cleanAndBuild(":lib:test", ":lib:assembleAndroidTest")
  }

  @Test
  fun defaultArguments() {
    val tester = GradleTester("defaultArguments")

    tester.cleanAndBuild(":lib:test")

    // The original test class runs the default specialization.
    with(tester.readTestSuite("CoffeeTest")) {
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
    assertThat(tester.hasTestSuite("CoffeeTest_Regular")).isFalse()

    // Another test class is executed normally with nothing skipped.
    with(tester.readTestSuite("CoffeeTest_Double")) {
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

  @Test
  fun burstValues() {
    val tester = GradleTester("burstValues")

    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("CoffeeTest")) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "test",
        "test_12",
        "test_16",
      )
      assertThat(systemOut).isEqualTo(
        """
        |set up Decaf
        |running Decaf 12
        |set up Decaf
        |running Decaf 16
        |set up Decaf
        |running Decaf 8
        |
        """.trimMargin(),
      )
    }

    with(tester.readTestSuite("CoffeeTest_Regular")) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "test",
        "test_12",
        "test_16",
      )
      assertThat(systemOut).isEqualTo(
        """
        |set up Regular
        |running Regular 12
        |set up Regular
        |running Regular 16
        |set up Regular
        |running Regular 8
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun junit5() {
    val tester = GradleTester("junit5")

    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("CoffeeTest_Regular")) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "kotlinTestTest_Milk()",
        "kotlinTestTest_None()",
        "kotlinTestTest_Oat()",
        "orgJunitJupiterApiTest_Milk()",
        "orgJunitJupiterApiTest_None()",
        "orgJunitJupiterApiTest_Oat()",
        "orgJunitTest_Milk",
        "orgJunitTest_None",
        "orgJunitTest_Oat",
      )
    }
  }

  @Test
  fun junit5DefaultClassArguments() {
    val tester = GradleTester("junit5DefaultClassArguments")

    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("CoffeeTest")) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "kotlinTestTest_Milk()",
        "kotlinTestTest_None()",
        "kotlinTestTest_Oat()",
        "orgJunitJupiterApiTest_Milk()",
        "orgJunitJupiterApiTest_None()",
        "orgJunitJupiterApiTest_Oat()",
        "orgJunitTest_Milk",
        "orgJunitTest_None",
        "orgJunitTest_Oat",
      )
    }

    tester.hasTestSuite("CoffeeTest_Decaf")
    tester.hasTestSuite("CoffeeTest_Double")
  }

  /** https://github.com/cashapp/burst/issues/90 */
  @Test
  fun subclass() {
    val tester = GradleTester("subclass")

    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("CoffeeTest")) {
      assertThat(testCases.map { it.name }).containsExactlyInAnyOrder(
        "hasFakeOverride",
        "hasFakeOverride_Oat",
        "hasRealOverride",
        "hasRealOverride_Oat",
      )
      assertThat(systemOut.trim().lines()).containsExactlyInAnyOrder(
        "running fakeOverride Milk",
        "running fakeOverride Oat",
        "running realOverride Milk",
        "running realOverride Oat",
      )
    }
  }

  @Test
  fun valueClassConstructor() {
    val tester = GradleTester("valueClassConstructor")

    tester.cleanAndBuild(":lib:test")

    // There's no default specialization.
    assertThat(tester.hasTestSuite("CoffeeTest")).isFalse()

    val sampleTest = tester.readTestSuite("CoffeeTest_Decaf")
    val sampleTestTest = sampleTest.testCases.single()
    assertThat(sampleTestTest.name).isEqualTo("test")
    assertThat(sampleTestTest.skipped).isFalse()
    assertThat(sampleTest.systemOut).isEqualTo(
      """
      |running Decaf
      |
      """.trimMargin(),
    )
  }
}
