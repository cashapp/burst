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
   * In JUnit 4:
   *   multiple set up functions run in reverse alphabetical order
   *   multiple tear down functions run in alphabetical order
   */
  @Test
  fun multipleBeforesAndAfters() {
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
  }

  @Test
  fun multipleAftersAfterFailure() {
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
  }

  /**
   * If any @AfterTest throws, the other @AfterTest functions still execute.
   */
  @Test
  fun afterTestThrows() {
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
  }

  /**
   * If any @BeforeTest throws, no more @BeforeTests run and neither does the test itself.
   */
  @Test
  fun beforeTestThrows() {
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
  }

  /**
   * In JUnit 4, @Rules execute in reverse alphabetical order.
   */
  @Test
  fun multipleInterceptors() {
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
  }

  /**
   * In JUnit 4, superclass rules run before subclass rules.
   */
  @Test
  fun interceptInSuperclass() {
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

  @Test
  fun symbolNames() {
    with(tester.readTestSuite("app.cash.burst.tests.SymbolNamesTest")) {
      assertThat(testCases.firstNotNullOfOrNull { it.failureMessage }).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting app.cash.burst.tests SymbolNamesTest test function with spaces
        |intercepting app.cash.burst.tests SymbolNamesTest test
        |
        """.trimMargin(),
      )
    }

    with(tester.readTestSuite("app.cash.burst.tests.SymbolNamesTest\$Nested")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting app.cash.burst.tests SymbolNamesTest.Nested test
        |
        """.trimMargin(),
      )
    }

    with(tester.readTestSuite("app.cash.burst.tests.SymbolNamesTest\$Enclosing\$TwiceNested")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting app.cash.burst.tests SymbolNamesTest.Enclosing.TwiceNested test
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun emptyPackageName() {
    with(tester.readTestSuite("EmptyPackageNameTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting '' 'EmptyPackageNameTest' 'test'
        |
        """.trimMargin(),
      )
    }
  }

  /**
   * This is different from JUnit rules, which must be public.
   */
  @Test
  fun privateInterceptor() {
    with(tester.readTestSuite("app.cash.burst.tests.PrivateInterceptorTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting
        |running
        |intercepted
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun repeatedTest() {
    with(tester.readTestSuite("app.cash.burst.tests.RepeatedTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |run 0
        |before test
        |running
        |after test
        |run 1
        |before test
        |running
        |after test
        |run 2
        |before test
        |running
        |after test
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun retryingTest() {
    with(tester.readTestSuite("app.cash.burst.tests.RetryingTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |attempt 0
        |before test
        |after test
        |failed: not enough attempts
        |attempt 1
        |before test
        |after test
        |failed: not enough attempts
        |attempt 2
        |before test
        |after test
        |success
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun reuseInterceptor() {
    with(tester.readTestSuite("app.cash.burst.tests.ReuseInterceptorTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |intercepting
        |intercepting
        |intercepting
        |running
        |intercepted
        |intercepted
        |intercepted
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun interceptorThrows() {
    with(tester.readTestSuite("app.cash.burst.tests.InterceptorThrowsTest")) {
      assertThat(testCases.single().failureMessage).isNotNull().contains("boom!")
      assertThat(systemOut).isEqualTo(
        """
        |before test
        |running
        |after test
        |
        """.trimMargin(),
      )
    }
  }

  @Test
  fun interceptorGetter() {
    with(tester.readTestSuite("app.cash.burst.tests.InterceptorGetterTest")) {
      assertThat(testCases.single().failureMessage).isNull()
      assertThat(systemOut).isEqualTo(
        """
        |getting interceptor red
        |intercepting red
        |getting interceptor green
        |intercepting green
        |getting interceptor blue
        |intercepting blue
        |before test
        |running
        |after test
        |intercepted blue
        |intercepted green
        |intercepted red
        |
        """.trimMargin(),
      )
    }
  }
}
