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
class SuspendingTestInterceptorKotlinPluginTest {
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
        import app.cash.burst.coroutines.CoroutineTestFunction
        import app.cash.burst.coroutines.CoroutineTestInterceptor
        import kotlin.coroutines.coroutineContext
        import kotlin.test.Test
        import kotlinx.coroutines.CoroutineName
        import kotlinx.coroutines.test.runTest
        import kotlinx.coroutines.test.TestScope

        class SampleTest {
          @InterceptTest
          val interceptor = object : CoroutineTestInterceptor {
            override suspend fun intercept(testFunction: CoroutineTestFunction) {
              log("intercepting ${'$'}testFunction in ${'$'}{coroutineContext[CoroutineName]?.name}")
              testFunction()
              log("intercepted")
            }
          }

          @Test
          fun happyPath() = runTest(CoroutineName("happyCoroutine")) {
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
      "intercepting com.example.SampleTest.happyPath in happyCoroutine",
      "running happyPath",
      "intercepted",
    )
  }

  @Test
  fun useTestScopeInInterceptor() {
    val log = BurstTester(
      packageName = "com.example",
    ).compileAndRun(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.coroutines.CoroutineTestFunction
        import app.cash.burst.coroutines.CoroutineTestInterceptor
        import kotlin.test.Test
        import kotlinx.coroutines.test.runTest
        import kotlinx.coroutines.test.TestScope
        import kotlin.time.Duration.Companion.seconds

        class SampleTest {
          @InterceptTest
          val interceptor = object : CoroutineTestInterceptor {
            override suspend fun intercept(testFunction: CoroutineTestFunction) {
              log("advancing time in interceptor")
              testFunction.scope.testScheduler.advanceTimeBy(1.seconds)
              testFunction()
            }
          }

          @Test
          fun happyPath() = runTest {
            log("advancing time in test")
            testScheduler.advanceTimeBy(1.seconds)
          }
        }

        fun main(vararg args: String) {
          SampleTest().happyPath()
        }
        """,
      ),
    )

    assertThat(log).containsExactly(
      "advancing time in interceptor",
      "advancing time in test",
    )
  }

  @Test
  fun cannotUseNonCoroutineTestInterceptorWithCoroutineTestInterceptor() {
    val result = compile(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import app.cash.burst.coroutines.CoroutineTestFunction
        import app.cash.burst.coroutines.CoroutineTestInterceptor
        import kotlin.test.Test
        import kotlinx.coroutines.test.runTest

        open class SampleTest {
          @InterceptTest
          val interceptorA = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              testFunction()
            }
          }

          @InterceptTest
          val interceptorB = object : CoroutineTestInterceptor {
            override suspend fun intercept(testFunction: CoroutineTestFunction) {
              testFunction()
            }
          }
        }
        """,
      ),
    )

    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "Main.kt:12:3 Cannot mix non-coroutine TestInterceptors with CoroutineTestInterceptors in the same test",
    )
  }

  @Test
  fun cannotUseNonCoroutineTestInterceptorWithCoroutinesTest() {
    val result = compile(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test
        import kotlinx.coroutines.test.runTest

        class SampleTest {
          @InterceptTest
          val simpleInterceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              testFunction()
            }
          }

          @Test
          fun happyPath() = runTest {
          }
        }
        """,
      ),
    )

    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "Main.kt:17:3 com.example.SampleTest.simpleInterceptor cannot intercept a coroutine test function",
    )
  }

  @Test
  fun cannotUseCoroutineTestInterceptorWithNonCoroutinesTest() {
    val result = compile(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.coroutines.CoroutineTestFunction
        import app.cash.burst.coroutines.CoroutineTestInterceptor
        import kotlin.test.Test

        class SampleTest {
          @InterceptTest
          val simpleInterceptor = object : CoroutineTestInterceptor {
            override suspend fun intercept(testFunction: CoroutineTestFunction) {
              testFunction()
            }
          }

          @Test
          fun happyPath() {
          }
        }
        """,
      ),
    )

    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "Main.kt:16:3 com.example.SampleTest.simpleInterceptor cannot intercept a non-coroutine test function",
    )
  }

  @Test
  fun cannotUseNonCoroutinesTestWithCoroutinesSuperclass() {
    val result = compile(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.coroutines.CoroutineTestFunction
        import app.cash.burst.coroutines.CoroutineTestInterceptor
        import kotlin.test.Test

        open class BaseTest {
          @InterceptTest
          val simpleInterceptor = object : CoroutineTestInterceptor {
            override suspend fun intercept(testFunction: CoroutineTestFunction) {
              testFunction()
            }
          }
        }

        class SampleTest : BaseTest() {
          @Test
          fun happyPath() {
          }
        }
        """,
      ),
    )

    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "Main.kt:18:3 CoroutineTestInterceptor cannot intercept a non-coroutine test function",
    )
  }

  @Test
  fun cannotUseCoroutinesTestWithNonCoroutinesSuperclass() {
    val result = compile(
      SourceFile.kotlin(
        "Main.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test
        import kotlinx.coroutines.test.runTest

        open class BaseTest {
          @InterceptTest
          val simpleInterceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              testFunction()
            }
          }
        }

        class SampleTest : BaseTest() {
          @Test
          fun happyPath() = runTest {
          }
        }
        """,
      ),
    )

    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "Main.kt:19:3 TestInterceptor cannot intercept a coroutine test function",
    )
  }
}
