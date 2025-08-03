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
import org.junit.Test

class TestInterceptorGradlePluginTest {
  @Test
  fun interceptor() {
    val tester = GradleTester("interceptor")

    val result = tester.cleanAndBuildAndFail(":lib:test")
    assertThat(result.outcome).isEqualTo(TaskOutcome.FAILED)

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

    // In JUnit 4:
    //   multiple set up functions in reverse alphabetical order
    //   multiple tear down functions in alphabetical order
    with(tester.readTestSuite("app.cash.burst.tests.MultipleBeforesAndAftersTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting
        |beforeTest red
        |beforeTest green
        |beforeTest blue
        |running
        |afterTest blue
        |afterTest green
        |afterTest red
        |intercepted
        |
        """.trimMargin(),
      )
    }

    with(tester.readTestSuite("app.cash.burst.tests.MultipleAftersAfterFailureTest")) {
      assertThat(testCases.single().failureMessage).isNotNull().contains("boom!")
      assertThat(systemOut).isEqualTo(
        """
        |afterTest blue
        |afterTest green
        |afterTest red
        |re-throwing exception: boom!
        |
        """.trimMargin(),
      )
    }

    // If any @AfterTest throws, the other @AfterTest functions still execute.
    with(tester.readTestSuite("app.cash.burst.tests.AfterTestThrowsTest")) {
      assertThat(testCases.single().failureMessage).isNotNull().contains("boom!")
      assertThat(systemOut).isEqualTo(
        """
        |running
        |afterTest green
        |afterTest red
        |re-throwing exception: boom!
        |
        """.trimMargin(),
      )
    }

    // If any @BeforeTest throws, no more @BeforeTests run and neither does the test itself.
    with(tester.readTestSuite("app.cash.burst.tests.BeforeTestThrowsTest")) {
      assertThat(testCases.single().failureMessage).isNotNull().contains("boom!")
      assertThat(systemOut).isEqualTo(
        """
        |beforeTest red
        |beforeTest green
        |afterTest
        |re-throwing exception: boom!
        |
        """.trimMargin(),
      )
    }

    // In JUnit 4, @Rules execute in reverse alphabetical order.
    with(tester.readTestSuite("app.cash.burst.tests.MultipleInterceptorsTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting red (app.cash.burst.tests MultipleInterceptorsTest passingTest)
        |intercepting green (app.cash.burst.tests MultipleInterceptorsTest passingTest)
        |intercepting blue (app.cash.burst.tests MultipleInterceptorsTest passingTest)
        |running
        |intercepted blue
        |intercepted green
        |intercepted red
        |
        """.trimMargin(),
      )
    }

    // In JUnit 4, superclass rules run before subclass rules.
    with(tester.readTestSuite("app.cash.burst.tests.InterceptInSuperclassTest${'$'}CircleTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting shape (app.cash.burst.tests InterceptInSuperclassTest.CircleTest passingTest)
        |intercepting circle (app.cash.burst.tests InterceptInSuperclassTest.CircleTest passingTest)
        |running
        |intercepted circle
        |intercepted shape
        |
        """.trimMargin(),
      )
    }
  }
}
