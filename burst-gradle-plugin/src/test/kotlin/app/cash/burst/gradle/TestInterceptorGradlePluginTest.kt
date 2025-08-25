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
        |intercepting
        |running
        |intercepted
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
        |intercepting
        |running
        |intercepted
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
        |> intercepting shape
        |> before test shape
        |> intercepting circle
        |> before test circle
        |  running testShape
        |< after test circle
        |< intercepted circle
        |< after test shape
        |< intercepted shape
        |
        """.trimMargin(),
      )
    }
  }
}
