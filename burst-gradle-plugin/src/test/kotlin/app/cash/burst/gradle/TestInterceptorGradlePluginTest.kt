/*
 * Copyright (C) 2025 Cash App
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
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.presetName
import org.junit.Test

class TestInterceptorGradlePluginTest {
  @Test
  fun happyPath() {
    val tester = GradleTester("interceptor")
    tester.cleanAndBuildAndFail(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.BasicTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting
        |  packageName=app.cash.burst.tests
        |  className=BasicTest
        |  functionName=passingTest
        |set up
        |running
        |tear down
        |intercepted
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun failingTest() {
    val tester = GradleTester("interceptor")
    tester.cleanAndBuildAndFail(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.FailingTest")) {
      assertThat(testCases.single().failureMessage).isNotNull().contains("boom!")
      assertThat(systemOut).isEqualTo(
        """
        |set up
        |tear down
        |re-throwing exception: boom!
        |
        """.trimMargin(),
      )
    }
  }

  /**
   * Note that this is different from JUnit 4, where rules enclose superclass' @Before functions.
   */
  @Test
  fun beforeTestInSuperclass() {
    val tester = GradleTester("interceptor")
    tester.cleanAndBuildAndFail(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.BeforeTestInSuperclassTest${'$'}CircleTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |beforeTest
        |intercepting app.cash.burst.tests.BeforeTestInSuperclassTest.CircleTest.passingTest
        |running
        |intercepted app.cash.burst.tests.BeforeTestInSuperclassTest.CircleTest.passingTest
        |
        """.trimMargin(),
      )
    }
  }

  /**
   * Note that this is different from JUnit 4, where rules enclose superclass' @After functions.
   */
  @Test
  fun afterTestInSuperclass() {
    val tester = GradleTester("interceptor")
    tester.cleanAndBuildAndFail(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.AfterTestInSuperclassTest${'$'}CircleTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting app.cash.burst.tests.AfterTestInSuperclassTest.CircleTest.passingTest
        |running
        |intercepted app.cash.burst.tests.AfterTestInSuperclassTest.CircleTest.passingTest
        |afterTest
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun interceptorAcrossModules() {
    val tester = GradleTester("interceptorAcrossModules")
    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.CircleTest")) {
      assertThat(systemOut).isEqualTo(
        """
        |> intercepting app.cash.burst.tests.CircleTest.testShape
        |> before test shape
        |> intercepting app.cash.burst.tests.CircleTest.testShape
        |> before test circle
        |  running testShape
        |< after test circle
        |< intercepted app.cash.burst.tests.CircleTest.testShape
        |< after test shape
        |< intercepted app.cash.burst.tests.CircleTest.testShape
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun abstractClassHasSuperClassThatIntercepts() {
    val tester = GradleTester("interceptorAcrossModules")
    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.BottomTest")) {
      assertThat(systemOut).isEqualTo(
        """
        |> intercepting app.cash.burst.tests.BottomTest.testBottom
        |  running bottom
        |< intercepted app.cash.burst.tests.BottomTest.testBottom
        |> intercepting app.cash.burst.tests.BottomTest.testMiddle
        |  running middle
        |< intercepted app.cash.burst.tests.BottomTest.testMiddle
        |> intercepting app.cash.burst.tests.BottomTest.testTop
        |  running top
        |< intercepted app.cash.burst.tests.BottomTest.testTop
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun junit5KotlinTest() {
    val tester = GradleTester("interceptorJunit5")
    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.KotlinTestTest")) {
      assertThat(systemOut).isEqualTo(
        """
        |intercepting app.cash.burst.tests.KotlinTestTest.kotlinTestTest
        |@kotlin.test.BeforeTest before test
        |@kotlin.test.Test running
        |@kotlin.test.AfterTest after test
        |intercepted app.cash.burst.tests.KotlinTestTest.kotlinTestTest
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun junit5OrgJunitTest() {
    val tester = GradleTester("interceptorJunit5")
    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.OrgJunitTest")) {
      assertThat(systemOut).isEqualTo(
        """
        |intercepting app.cash.burst.tests.OrgJunitTest.orgJunitTest
        |@org.junit.Before before test
        |@org.junit.Test running
        |@org.junit.After after test
        |intercepted app.cash.burst.tests.OrgJunitTest.orgJunitTest
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun junit5OrgJunitJupiterApiTest() {
    val tester = GradleTester("interceptorJunit5")
    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.OrgJunitJupiterApiTest")) {
      assertThat(systemOut).isEqualTo(
        """
        |intercepting app.cash.burst.tests.OrgJunitJupiterApiTest.orgJunitJupiterApiTest
        |@org.junit.jupiter.api.BeforeEach before test
        |@org.junit.jupiter.api.Test running
        |@org.junit.jupiter.api.AfterEach after test
        |intercepted app.cash.burst.tests.OrgJunitJupiterApiTest.orgJunitJupiterApiTest
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun interceptorAndBurstConstructor() {
    val tester = GradleTester("interceptorAndBurst")
    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.InterceptorAndBurstConstructorTest_false")) {
      assertThat(systemOut).isEqualTo(
        """
        |intercepting false app.cash.burst.tests.InterceptorAndBurstConstructorTest_false.test
        |running false
        |
        """.trimMargin(),
      )
    }
    with(tester.readTestSuite("app.cash.burst.tests.InterceptorAndBurstConstructorTest_true")) {
      assertThat(systemOut).isEqualTo(
        """
        |intercepting true app.cash.burst.tests.InterceptorAndBurstConstructorTest_true.test
        |running true
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun interceptorAndBurstFunction() {
    val tester = GradleTester("interceptorAndBurst")
    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.InterceptorAndBurstFunctionTest")) {
      assertThat(systemOut).isEqualTo(
        """
        |intercepting function app.cash.burst.tests.InterceptorAndBurstFunctionTest.test_true
        |running true
        |intercepting function app.cash.burst.tests.InterceptorAndBurstFunctionTest.test_false
        |running false
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun interceptorAndBurstSubclass() {
    val tester = GradleTester("interceptorAndBurst")
    tester.cleanAndBuild(":lib:test")

    with(tester.readTestSuite("app.cash.burst.tests.InterceptorAndBurstSubclassTest")) {
      assertThat(systemOut).isEqualTo(
        """
        |intercepting abstract app.cash.burst.tests.InterceptorAndBurstSubclassTest.test_true
        |running subclass true
        |intercepting abstract app.cash.burst.tests.InterceptorAndBurstSubclassTest.test_false
        |running subclass false
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun multiplatformJvm() {
    multiplatform(testTaskName = "jvmTest")
  }

  @Test
  fun multiplatformJs() {
    multiplatform(testTaskName = "jsNodeTest")
  }

  @Test
  fun multiplatformNative() {
    // Like 'linuxX64' or 'macosArm64'.
    val platformName = HostManager.host.presetName
    multiplatform(testTaskName = "${platformName}Test")
  }

  private fun multiplatform(testTaskName: String) {
    val tester = GradleTester("multiplatformInterceptor")
    tester.cleanAndBuild(":lib:$testTaskName")

    with(tester.readTestSuite("app.cash.burst.tests.BasicTest", testTaskName)) {
      assertThat(systemOut).isEqualTo(
        """
        |intercepting app.cash.burst.tests.BasicTest.passingTest
        |set up
        |running
        |tear down
        |intercepted
        |
        """.trimMargin(),
      )
    }

    with(tester.readTestSuite("app.cash.burst.tests.CoroutinesTest", testTaskName)) {
      assertThat(systemOut).isEqualTo(
        """
        |intercepting app.cash.burst.tests.CoroutinesTest.passingTest in passingCoroutine
        |set up
        |running in passingCoroutine
        |tear down
        |intercepted
        |
        """.trimMargin(),
      )
    }
  }
}
