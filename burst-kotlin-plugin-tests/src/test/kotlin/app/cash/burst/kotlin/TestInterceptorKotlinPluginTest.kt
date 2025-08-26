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
package app.cash.burst.kotlin

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class TestInterceptorKotlinPluginTest {
  @Test
  fun interceptor() {
    val log = BurstTester(
      packageName = "com.example",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("intercepting")
              log("  packageName=${'$'}{testFunction.packageName}")
              log("  className=${'$'}{testFunction.className}")
              log("  functionName=${'$'}{testFunction.functionName}")
              testFunction()
              log("intercepted")
            }
          }

          @Test
          fun happyPath() {
            log("running happyPath")
          }
        }

        fun main(vararg args: String) {
          SampleTest().happyPath()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting",
      "  packageName=com.example",
      "  className=SampleTest",
      "  functionName=happyPath",
      "running happyPath",
      "intercepted",
    )
  }

  /**
   * Note that this is different from JUnit 4, which executes @Rules in reverse alphabetical order.
   */
  @Test
  fun multipleInterceptorsExecutedInSequence() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test

        class MultipleInterceptorsTest {
          @InterceptTest
          val interceptorRed = LoggingInterceptor("red")

          @InterceptTest
          val interceptorBlue = LoggingInterceptor("blue")

          @InterceptTest
          val interceptorGreen = LoggingInterceptor("green")

          @Test
          fun passingTest() {
            log("running")
          }
        }

        class LoggingInterceptor(val name: String) : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            log("intercepting ${'$'}name (${'$'}{testFunction.packageName} ${'$'}{testFunction.className} ${'$'}{testFunction.functionName})")
            testFunction()
            log("intercepted ${'$'}name")
          }
        }

        fun main(vararg args: String) {
          MultipleInterceptorsTest().passingTest()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting red (app.cash.burst.tests MultipleInterceptorsTest passingTest)",
      "intercepting blue (app.cash.burst.tests MultipleInterceptorsTest passingTest)",
      "intercepting green (app.cash.burst.tests MultipleInterceptorsTest passingTest)",
      "running",
      "intercepted green",
      "intercepted blue",
      "intercepted red",
    )
  }

  @Test
  fun beforeTestIsCalledByInterceptor() {
    val log = BurstTester(
      packageName = "com.example",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("intercepting test")
              testFunction()
              log("intercepted test")
            }
          }

          @BeforeTest
          fun beforeTest() {
            log("before test")
          }

          @Test
          fun happyPath() {
            log("running test")
          }
        }

        fun main(vararg args: String) {
          SampleTest().happyPath()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting test",
      "before test",
      "running test",
      "intercepted test",
    )
  }

  @Test
  fun afterTestIsCalledByInterceptor() {
    val log = BurstTester(
      packageName = "com.example",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("intercepting test")
              testFunction()
              log("intercepted test")
            }
          }

          @AfterTest
          fun afterTest() {
            log("after test")
          }

          @Test
          fun happyPath() {
            log("running test")
          }
        }

        fun main(vararg args: String) {
          SampleTest().happyPath()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting test",
      "running test",
      "after test",
      "intercepted test",
    )
  }

  /**
   * Note that this is different from JUnit 4, which has this behavior:
   *   multiple set up functions run in reverse alphabetical order
   *   multiple tear down functions run in alphabetical order
   */
  @Test
  fun multipleBeforesAndAfters() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class MultipleBeforesAndAftersTest {
          @InterceptTest
          val interceptor = BasicInterceptor()

          @BeforeTest
          fun beforeTestRed() {
            log("beforeTest red")
          }

          @BeforeTest
          fun beforeTestBlue() {
            log("beforeTest blue")
          }

          @BeforeTest
          fun beforeTestGreen() {
            log("beforeTest green")
          }

          @AfterTest
          fun afterTestRed() {
            log("afterTest red")
          }

          @AfterTest
          fun afterTestBlue() {
            log("afterTest blue")
          }

          @AfterTest
          fun afterTestGreen() {
            log("afterTest green")
          }

          @Test
          fun passingTest() {
            log("running")
          }
        }

        class BasicInterceptor : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            log("intercepting")
            testFunction()
            log("intercepted")
          }
        }


        fun main(vararg args: String) {
          MultipleBeforesAndAftersTest().passingTest()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting",
      "beforeTest red",
      "beforeTest blue",
      "beforeTest green",
      "running",
      "afterTest red",
      "afterTest blue",
      "afterTest green",
      "intercepted",
    )
  }

  @Test
  fun multipleAftersAfterFailure() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.Test

        class MultipleAftersAfterFailureTest {
          @InterceptTest
          val interceptor = CatchingInterceptor()

          @AfterTest
          fun afterTestRed() {
            log("afterTest red")
          }

          @AfterTest
          fun afterTestBlue() {
            log("afterTest blue")
          }

          @AfterTest
          fun afterTestGreen() {
            log("afterTest green")
          }

          @Test
          fun failingTest() {
            error("boom!")
          }
        }

        class CatchingInterceptor : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            try {
              testFunction()
            } catch (e: Throwable) {
              log("re-throwing exception: ${'$'}{e.message}")
              throw e
            }
          }
        }

        fun main(vararg args: String) {
          try {
            MultipleAftersAfterFailureTest().failingTest()
          } catch (e: Throwable) {
            log("caught: ${'$'}{e.message}")
          }
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "afterTest red",
      "afterTest blue",
      "afterTest green",
      "re-throwing exception: boom!",
      "caught: boom!",
    )
  }

  /**
   * If any @AfterTest throws, the other @AfterTest functions still execute.
   */
  @Test
  fun afterTestThrows() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.Test

        class AfterTestThrowsTest {
          @InterceptTest
          val interceptor = CatchingInterceptor()

          @AfterTest
          fun afterTestRed() {
            log("afterTest red")
          }

          @AfterTest
          fun afterTestBlue() {
            error("boom!")
          }

          @AfterTest
          fun afterTestGreen() {
            log("afterTest green")
          }

          @Test
          fun passingTest() {
            log("running")
          }
        }

        class CatchingInterceptor : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            try {
              testFunction()
            } catch (e: Throwable) {
              log("re-throwing exception: ${'$'}{e.message}")
              throw e
            }
          }
        }

        fun main(vararg args: String) {
          try {
            AfterTestThrowsTest().passingTest()
          } catch (e: Throwable) {
            log("caught: ${'$'}{e.message}")
          }
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "running",
      "afterTest red",
      "afterTest green",
      "re-throwing exception: boom!",
      "caught: boom!",
    )
  }

  /**
   * If any @BeforeTest throws, no more @BeforeTests run and neither does the test itself.
   */
  @Test
  fun beforeTestThrows() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class BeforeTestThrowsTest {
          @InterceptTest
          val interceptor = CatchingInterceptor()

          @BeforeTest
          fun beforeTestRed() {
            log("beforeTest red")
          }

          @BeforeTest
          fun beforeTestBlue() {
            error("boom!")
          }

          @BeforeTest
          fun beforeTestGreen() {
            log("beforeTest green")
          }

          @AfterTest
          fun afterTest() {
            log("afterTest")
          }

          @Test
          fun passingTest() {
            log("running")
          }
        }

        class CatchingInterceptor : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            try {
              testFunction()
            } catch (e: Throwable) {
              log("re-throwing exception: ${'$'}{e.message}")
              throw e
            }
          }
        }

        fun main(vararg args: String) {
          try {
            BeforeTestThrowsTest().passingTest()
          } catch (e: Throwable) {
            log("caught: ${'$'}{e.message}")
          }
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "beforeTest red",
      "afterTest",
      "re-throwing exception: boom!",
      "caught: boom!",
    )
  }

  @Test
  fun afterTestIsCalledWhenTestThrows() {
    val log = BurstTester(
      packageName = "com.example",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("intercepting test")
              try {
                testFunction()
              } catch (e: Exception) {
                log("intercepted test")
                throw e
              }
            }
          }

          @AfterTest
          fun afterTest() {
            log("after test")
          }

          @Test
          fun happyPath() {
            throw Exception("boom!")
          }
        }

        fun main(vararg args: String) {
          try {
            SampleTest().happyPath()
          } catch (e: Exception) {
            log("test failed: ${'$'}{e.message}")
          }
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting test",
      "after test",
      "intercepted test",
      "test failed: boom!",
    )
  }

  /**
   * In JUnit 4, superclass rules run before subclass rules.
   */
  @Test
  fun interceptInSuperclass() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test

        open class ShapeTest {
          @InterceptTest
          val shapeInterceptor = LoggingInterceptor("shape")
        }

        class CircleTest : ShapeTest() {
          @InterceptTest
          val circleInterceptor = LoggingInterceptor("circle")

          @Test
          fun passingTest() {
            log("running")
          }
        }

        class LoggingInterceptor(val name: String) : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            log("intercepting ${'$'}name (${'$'}{testFunction.packageName} ${'$'}{testFunction.className} ${'$'}{testFunction.functionName})")
            testFunction()
            log("intercepted ${'$'}name")
          }
        }

        fun main(vararg args: String) {
          CircleTest().passingTest()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting shape (app.cash.burst.tests CircleTest passingTest)",
      "intercepting circle (app.cash.burst.tests CircleTest passingTest)",
      "running",
      "intercepted circle",
      "intercepted shape",
    )
  }

  /**
   * Like [interceptInSuperclass], but the declaration order is flipped. We're sensitive to this
   * because each test class inspects its superclass to decide if it must call `super.intercept()`.
   */
  @Test
  fun interceptInSuperclassDeclaredAfterTestClass() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test

        class CircleTest : ShapeTest() {
          @InterceptTest
          val circleInterceptor = LoggingInterceptor("circle")

          @Test
          fun passingTest() {
            log("running")
          }
        }

        open class ShapeTest {
          @InterceptTest
          val shapeInterceptor = LoggingInterceptor("shape")
        }

        class LoggingInterceptor(val name: String) : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            log("intercepting ${'$'}name (${'$'}{testFunction.packageName} ${'$'}{testFunction.className} ${'$'}{testFunction.functionName})")
            testFunction()
            log("intercepted ${'$'}name")
          }
        }

        fun main(vararg args: String) {
          CircleTest().passingTest()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting shape (app.cash.burst.tests CircleTest passingTest)",
      "intercepting circle (app.cash.burst.tests CircleTest passingTest)",
      "running",
      "intercepted circle",
      "intercepted shape",
    )
  }

  /** Like [interceptInSuperclass], but the test class doesn't have its own interceptor. */
  @Test
  fun interceptInSuperclassButNotTestClass() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test

        class CircleTest : ShapeTest() {
          @Test
          fun passingTest() {
            log("running")
          }
        }

        open class ShapeTest {
          @InterceptTest
          val shapeInterceptor = LoggingInterceptor("shape")
        }

        class LoggingInterceptor(val name: String) : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            log("intercepting ${'$'}name (${'$'}{testFunction.packageName} ${'$'}{testFunction.className} ${'$'}{testFunction.functionName})")
            testFunction()
            log("intercepted ${'$'}name")
          }
        }

        fun main(vararg args: String) {
          CircleTest().passingTest()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting shape (app.cash.burst.tests CircleTest passingTest)",
      "running",
      "intercepted shape",
    )
  }

  @Test
  fun symbolNames() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test

        class SymbolNamesTest {
          @InterceptTest
          val interceptor = BasicInterceptor()

          @Test
          fun test() {
          }

          @Test
          fun `test function with spaces`() {
          }

          class Nested {
            @InterceptTest
            val interceptor = BasicInterceptor()

            @Test
            fun test() {
            }
          }

          class Enclosing {
            class TwiceNested {
              @InterceptTest
              val interceptor = BasicInterceptor()

              @Test
              fun test() {
              }
            }
          }

          class BasicInterceptor : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("intercepting ${'$'}{testFunction.packageName} ${'$'}{testFunction.className} ${'$'}{testFunction.functionName}")
              testFunction()
            }
          }
        }

        fun main(vararg args: String) {
          SymbolNamesTest().test()
          SymbolNamesTest().`test function with spaces`()
          SymbolNamesTest.Nested().test()
          SymbolNamesTest.Enclosing.TwiceNested().test()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting app.cash.burst.tests SymbolNamesTest test",
      "intercepting app.cash.burst.tests SymbolNamesTest test function with spaces",
      "intercepting app.cash.burst.tests SymbolNamesTest.Nested test",
      "intercepting app.cash.burst.tests SymbolNamesTest.Enclosing.TwiceNested test",
    )
  }

  @Test
  fun emptyPackageName() {
    val log = BurstTester(
      packageName = "",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test

        class EmptyPackageNameTest {
          @InterceptTest
          val interceptor = BasicInterceptor()

          @Test
          fun test() {
          }

          class BasicInterceptor : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("intercepting '${'$'}{testFunction.packageName}' '${'$'}{testFunction.className}' '${'$'}{testFunction.functionName}'")
              testFunction()
            }
          }
        }

        fun main(vararg args: String) {
          EmptyPackageNameTest().test()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting '' 'EmptyPackageNameTest' 'test'",
    )
  }

  /**
   * This is different from JUnit rules, which must be public.
   */
  @Test
  fun privateInterceptor() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test

        class PrivateInterceptorTest {
          @InterceptTest
          private val interceptor = PrivateInterceptor()

          @Test
          fun test() {
            log("running")
          }

          private class PrivateInterceptor : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("intercepting")
              testFunction()
              log("intercepted")
            }
          }
        }

        fun main(vararg args: String) {
          PrivateInterceptorTest().test()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting",
      "running",
      "intercepted",
    )
  }

  @Test
  fun repeatedTest() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class RepeatedTest {
          @InterceptTest
          val interceptor = RepeatingInterceptor()

          @BeforeTest
          fun beforeTest() {
            log("before test")
          }

          @AfterTest
          fun afterTest() {
            log("after test")
          }

          @Test
          fun test() {
            log("running")
          }
        }

        class RepeatingInterceptor : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            for (i in 0 until 3) {
              log("run ${'$'}i")
              testFunction()
            }
          }
        }

        fun main(vararg args: String) {
          RepeatedTest().test()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "run 0",
      "before test",
      "running",
      "after test",
      "run 1",
      "before test",
      "running",
      "after test",
      "run 2",
      "before test",
      "running",
      "after test",
    )
  }

  @Test
  fun retryingTest() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class RetryingTest {
          @InterceptTest
          val interceptor = RetryingInterceptor()

          var attempts = 0

          @BeforeTest
          fun beforeTest() {
            log("before test")
          }

          @AfterTest
          fun afterTest() {
            log("after test")
          }

          @Test
          fun test() {
            check(++attempts == 3) { "not enough attempts" }
          }
        }

        class RetryingInterceptor : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            for (i in 0 until 5) {
              try {
                log("attempt ${'$'}i")
                testFunction()
                log("success")
                return
              } catch (e: Throwable) {
                log("failed: ${'$'}{e.message}")
              }
            }
          }
        }

        fun main(vararg args: String) {
          RetryingTest().test()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "attempt 0",
      "before test",
      "after test",
      "failed: not enough attempts",
      "attempt 1",
      "before test",
      "after test",
      "failed: not enough attempts",
      "attempt 2",
      "before test",
      "after test",
      "success",
    )
  }

  @Test
  fun reuseInterceptor() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test

        class ReuseInterceptorTest {
          @InterceptTest
          val interceptorA = BasicInterceptor()

          @InterceptTest
          val interceptorB = interceptorA

          @InterceptTest
          val interceptorC = interceptorA

          @Test
          fun passingTest() {
            log("running")
          }
        }

        class BasicInterceptor : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            log("intercepting")
            testFunction()
            log("intercepted")
          }
        }

        fun main(vararg args: String) {
          ReuseInterceptorTest().passingTest()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting",
      "intercepting",
      "intercepting",
      "running",
      "intercepted",
      "intercepted",
      "intercepted",
    )
  }

  @Test
  fun interceptorThrows() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class InterceptorThrowsTest {
          @InterceptTest
          val interceptor = ThrowingInterceptor()

          @BeforeTest
          fun beforeTest() {
            log("before test")
          }

          @AfterTest
          fun afterTest() {
            log("after test")
          }

          @Test
          fun test() {
            log("running")
          }

          class ThrowingInterceptor : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              testFunction()
              error("boom!")
            }
          }
        }
        fun main(vararg args: String) {
          try {
            InterceptorThrowsTest().test()
          } catch (e: Throwable) {
            log("caught: ${'$'}{e.message}")
          }
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "before test",
      "running",
      "after test",
      "caught: boom!",
    )
  }

  @Test
  fun interceptorGetter() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class InterceptorGetterTest {
          @InterceptTest
          val interceptorRed: TestInterceptor
            get() {
              log("getting interceptor red")
              return BasicInterceptor("red")
            }

          @InterceptTest
          val interceptorBlue: TestInterceptor
            get() {
              log("getting interceptor blue")
              return BasicInterceptor("blue")
            }

          @InterceptTest
          val interceptorGreen: TestInterceptor
            get() {
              log("getting interceptor green")
              return BasicInterceptor("green")
            }

          @BeforeTest
          fun beforeTest() {
            log("before test")
          }

          @AfterTest
          fun afterTest() {
            log("after test")
          }

          @Test
          fun test() {
            log("running")
          }
        }

        class BasicInterceptor(val name: String) : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            log("intercepting ${'$'}name")
            testFunction()
            log("intercepted ${'$'}name")
          }
        }

        fun main(vararg args: String) {
          InterceptorGetterTest().test()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "getting interceptor red",
      "intercepting red",
      "getting interceptor blue",
      "intercepting blue",
      "getting interceptor green",
      "intercepting green",
      "before test",
      "running",
      "after test",
      "intercepted green",
      "intercepted blue",
      "intercepted red",
    )
  }

  @Test
  fun interceptorGetterThrows() {
    val log = BurstTester(
      packageName = "app.cash.burst.tests",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package app.cash.burst.tests

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class InterceptorGetterThrowsTest {
          @InterceptTest
          val workingInterceptor = BasicInterceptor("working")

          @InterceptTest
          val brokenInterceptor: TestInterceptor
            get() = error("boom!")

          @Test
          fun test() {
            log("running")
          }
        }

        class BasicInterceptor(val name: String) : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            log("intercepting ${'$'}name")
            try {
              testFunction()
            } finally {
              log("intercepted ${'$'}name")
            }
          }
        }

        fun main(vararg args: String) {
          try {
            InterceptorGetterThrowsTest().test()
          } catch (e: Throwable) {
            log("caught: ${'$'}{e.message}")
          }
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting working",
      "intercepted working",
      "caught: boom!",
    )
  }

  @Test
  fun interceptorMustBeTheRightType() {
    val result = compile(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import kotlin.test.Test

        class InterceptorMustBeTheRightType {
          @InterceptTest
          val wrongType: String = "hello"

          @Test
          fun test() {
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "Main.kt:7:3 @InterceptTest properties must be assignable to TestInterceptor",
    )
  }

  @Test
  fun interceptedTestMustBeFinal() {
    val result = compile(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test

        open class SampleTest {
          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              testFunction()
            }
          }

          @Test
          open fun happyPath() {
            println("running happyPath")
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "Main.kt:16:3 @InterceptTest cannot target test functions that are non-final",
    )
  }

  @Test
  fun interceptedTestMayOverrideSuperclassTest() {
    val log = BurstTester(
      packageName = "com.example",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        open class BaseTest {
          @Test
          open fun happyPath() {
            log("running BaseTest.happyPath")
          }
        }

        class SampleTest : BaseTest() {
          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("intercepting")
              testFunction()
            }
          }

          @Test
          override fun happyPath() {
            log("running happyPath")
            super.happyPath()
          }
        }

        fun main(vararg args: String) {
          SampleTest().happyPath()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting",
      "running happyPath",
      "running BaseTest.happyPath",
    )
  }

  /**
   * Our initial implementation failed bytecode validation when a function returned 'void' when the
   * interceptor required it to return 'Unit'.
   */
  @Test
  fun earlyReturn() {
    val log = BurstTester(
      packageName = "com.example",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("intercepting")
              testFunction()
              log("intercepted")
            }
          }

          @Test
          fun happyPath() {
            if (true) {
              log("early return")
              return
            }
            log("no early return")
          }
        }

        fun main(vararg args: String) {
          SampleTest().happyPath()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting",
      "early return",
      "intercepted",
    )
  }
}
