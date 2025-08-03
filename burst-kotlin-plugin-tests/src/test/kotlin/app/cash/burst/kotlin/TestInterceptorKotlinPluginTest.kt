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
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class TestInterceptorKotlinPluginTest {
  @Test
  fun interceptor() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "SampleTest.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          val log = mutableListOf<String>()

          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(test: TestInterceptor.Test) {
              log += "intercepting ${'$'}{test.packageName} ${'$'}{test.className} ${'$'}{test.functionName}"
              test()
            }
          }

          @Test
          fun happyPath() {
            log += "running happyPath"
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val testClass = result.classLoader.loadClass("com.example.SampleTest")

    val testInstance = testClass.constructors.single().newInstance()
    val log = testClass.getMethod("getLog").invoke(testInstance) as MutableList<*>

    val happyPath = testClass.methods.single { it.name == "happyPath" }
    happyPath.invoke(testInstance)
    assertThat(log).containsExactly(
      "intercepting com.example SampleTest happyPath",
      "running happyPath",
    )
    log.clear()
  }

  @Test
  fun multipleInterceptorsExecutedInSequence() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "SampleTest.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          val log = mutableListOf<String>()

          @InterceptTest
          val interceptorA = object : TestInterceptor {
            override fun intercept(test: TestInterceptor.Test) {
              log += "start A"
              test()
              log += "end A"
            }
          }

          @InterceptTest
          val interceptorB = object : TestInterceptor {
            override fun intercept(test: TestInterceptor.Test) {
              log += "start B"
              test()
              log += "end B"
            }
          }

          @InterceptTest
          val interceptorC = object : TestInterceptor {
            override fun intercept(test: TestInterceptor.Test) {
              log += "start C"
              test()
              log += "end C"
            }
          }

          @Test
          fun happyPath() {
            log += "running test"
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val testClass = result.classLoader.loadClass("com.example.SampleTest")

    val testInstance = testClass.constructors.single().newInstance()
    val log = testClass.getMethod("getLog").invoke(testInstance) as MutableList<*>

    val happyPath = testClass.methods.single { it.name == "happyPath" }
    happyPath.invoke(testInstance)
    assertThat(log).containsExactly(
      "start A",
      "start B",
      "start C",
      "running test",
      "end C",
      "end B",
      "end A",
    )
    log.clear()
  }

  @Test
  fun beforeTestIsCalledByInterceptor() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "SampleTest.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          val log = mutableListOf<String>()

          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(test: TestInterceptor.Test) {
              log += "intercepting test"
              test()
              log += "intercepted test"
            }
          }

          @BeforeTest
          fun beforeTest() {
            log += "before test"
          }

          @Test
          fun happyPath() {
            log += "running test"
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val testClass = result.classLoader.loadClass("com.example.SampleTest")

    val testInstance = testClass.constructors.single().newInstance()
    val log = testClass.getMethod("getLog").invoke(testInstance) as MutableList<*>

    val happyPath = testClass.methods.single { it.name == "happyPath" }
    happyPath.invoke(testInstance)
    assertThat(log).containsExactly(
      "intercepting test",
      "before test",
      "running test",
      "intercepted test",
    )
    log.clear()
  }

  @Test
  fun afterTestIsCalledByInterceptor() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "SampleTest.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          val log = mutableListOf<String>()

          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(test: TestInterceptor.Test) {
              log += "intercepting test"
              test()
              log += "intercepted test"
            }
          }

          @AfterTest
          fun afterTest() {
            log += "after test"
          }

          @Test
          fun happyPath() {
            log += "running test"
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val testClass = result.classLoader.loadClass("com.example.SampleTest")

    val testInstance = testClass.constructors.single().newInstance()
    val log = testClass.getMethod("getLog").invoke(testInstance) as MutableList<*>

    val happyPath = testClass.methods.single { it.name == "happyPath" }
    happyPath.invoke(testInstance)
    assertThat(log).containsExactly(
      "intercepting test",
      "running test",
      "after test",
      "intercepted test",
    )
    log.clear()
  }

  @Test
  fun afterTestIsCalledWhenTestThrows() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "SampleTest.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          val log = mutableListOf<String>()

          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(test: TestInterceptor.Test) {
              log += "intercepting test"
              try {
                test()
              } catch (e: AssertionError) {
                log += "intercepted test"
                throw e
              }
            }
          }

          @AfterTest
          fun afterTest() {
            log += "after test"
          }

          @Test
          fun happyPath() {
            throw AssertionError("test failed")
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val testClass = result.classLoader.loadClass("com.example.SampleTest")

    val testInstance = testClass.constructors.single().newInstance()
    val log = testClass.getMethod("getLog").invoke(testInstance) as MutableList<*>

    val happyPath = testClass.methods.single { it.name == "happyPath" }
    val failure = assertFailsWith<InvocationTargetException> {
      happyPath.invoke(testInstance)
    }
    assertThat(failure.cause).isNotNull().isInstanceOf<AssertionError>()
    assertThat(log).containsExactly(
      "intercepting test",
      "after test",
      "intercepted test",
    )
    log.clear()
  }
}
