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
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          val log = mutableListOf<String>()

          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log += "intercepting ${'$'}{testFunction.packageName} ${'$'}{testFunction.className} ${'$'}{testFunction.functionName}"
              testFunction()
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
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          val log = mutableListOf<String>()

          @InterceptTest
          val interceptorA = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log += "start A"
              testFunction()
              log += "end A"
            }
          }

          @InterceptTest
          val interceptorB = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log += "start B"
              testFunction()
              log += "end B"
            }
          }

          @InterceptTest
          val interceptorC = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log += "start C"
              testFunction()
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
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          val log = mutableListOf<String>()

          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log += "intercepting test"
              testFunction()
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
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          val log = mutableListOf<String>()

          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log += "intercepting test"
              testFunction()
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
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.AfterTest
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        class SampleTest {
          val log = mutableListOf<String>()

          @InterceptTest
          val interceptor = object : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log += "intercepting test"
              try {
                testFunction()
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

  @Test
  fun inheritance() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "InterceptInSuperclassTest.kt",
        """
        package com.example

        import app.cash.burst.InterceptTest
        import app.cash.burst.TestFunction
        import app.cash.burst.TestInterceptor
        import kotlin.test.Test

        class InterceptInSuperclassTest {
          companion object {
            @JvmStatic
            val log = mutableListOf<String>()
          }

          open class ShapeTest {
            @InterceptTest
            val shapeInterceptor = LoggingInterceptor("shape")
          }

          class CircleTest : ShapeTest() {
            @InterceptTest
            val circleInterceptor = LoggingInterceptor("circle")

            @Test
            fun happyPath() {
              log += "running"
            }
          }

          class LoggingInterceptor(val name: String) : TestInterceptor {
            override fun intercept(testFunction: TestFunction) {
              log += "intercepting ${'$'}name"
              testFunction()
              log += "intercepted ${'$'}name"
            }
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val enclosingClass = result.classLoader.loadClass("com.example.InterceptInSuperclassTest")
    val testClass = result.classLoader.loadClass("com.example.InterceptInSuperclassTest\$CircleTest")

    val testInstance = testClass.constructors.single().newInstance()
    val log = enclosingClass.getMethod("getLog").invoke(null) as MutableList<*>

    val happyPath = testClass.methods.single { it.name == "happyPath" }
    happyPath.invoke(testInstance)
    assertThat(log).containsExactly(
      "intercepting shape",
      "intercepting circle",
      "running",
      "intercepted circle",
      "intercepted shape",
    )
  }
}
