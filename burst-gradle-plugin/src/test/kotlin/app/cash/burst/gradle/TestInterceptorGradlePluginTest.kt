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
import org.gradle.testkit.runner.TaskOutcome
import org.junit.BeforeClass
import org.junit.Test

class TestInterceptorGradlePluginTest {
  companion object {
    private val tester = GradleTester("interceptor")

    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      val result = tester.cleanAndBuildAndFail(":lib:test")
      assertThat(result.outcome).isEqualTo(TaskOutcome.FAILED)
    }
  }

  @Test
  fun happyPath() {
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
}
