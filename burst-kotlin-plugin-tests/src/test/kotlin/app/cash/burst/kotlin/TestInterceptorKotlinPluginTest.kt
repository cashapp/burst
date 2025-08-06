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
import assertk.assertions.containsExactly
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
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

  @Test
  fun multipleInterceptorsExecutedInSequence() {
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
          val interceptorA = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("start A")
              testFunction()
              log("end A")
            }
          }

          @InterceptTest
          val interceptorB = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("start B")
              testFunction()
              log("end B")
            }
          }

          @InterceptTest
          val interceptorC = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log("start C")
              testFunction()
              log("end C")
            }
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
      "start A",
      "start B",
      "start C",
      "running test",
      "end C",
      "end B",
      "end A",
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

  @Test
  fun inheritance() {
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
        import kotlin.test.Test

        open class ShapeTest {
          @InterceptTest
          val shapeInterceptor = LoggingInterceptor("shape")
        }

        class CircleTest : ShapeTest() {
          @InterceptTest
          val circleInterceptor = LoggingInterceptor("circle")

          @Test
          fun happyPath() {
            log("running")
          }
        }

        class LoggingInterceptor(val name: String) : TestInterceptor {
          override fun intercept(testFunction: TestFunction) {
            log("intercepting ${'$'}name")
            testFunction()
            log("intercepted ${'$'}name")
          }
        }

        fun main(vararg args: String) {
          CircleTest().happyPath()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "intercepting shape",
      "intercepting circle",
      "running",
      "intercepted circle",
      "intercepted shape",
    )
  }
}
