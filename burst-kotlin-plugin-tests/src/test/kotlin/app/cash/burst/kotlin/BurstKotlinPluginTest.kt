/*
 * Copyright (C) 2024 Cash App
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
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class BurstKotlinPluginTest {
  @Test
  fun functionParameters() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(espresso: Espresso, dairy: Dairy) {
            log += "running ${'$'}espresso ${'$'}dairy"
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        enum class Dairy { None, Milk, Oat }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val testClass = result.classLoader.loadClass("CoffeeTest")

    // Burst doesn't make the class non-final as it has no reason to.
    assertThat(Modifier.isFinal(testClass.modifiers)).isTrue()

    val adapterInstance = testClass.constructors.single().newInstance()
    val log = testClass.getMethod("getLog").invoke(adapterInstance) as MutableList<*>

    // Burst drops @Test from the original test function.
    val originalTest = testClass.methods.single { it.name == "test" && it.parameterCount == 2 }
    assertThat(originalTest.isAnnotationPresent(Test::class.java)).isFalse()

    // Burst adds a specialization for each combination of parameters.
    val sampleSpecialization = testClass.getMethod("test_Regular_Milk")
    assertThat(sampleSpecialization.isAnnotationPresent(Test::class.java)).isTrue()
    sampleSpecialization.invoke(adapterInstance)
    assertThat(log).containsExactly("running Regular Milk")
    log.clear()

    // Burst doesn't add a no-parameter function because there's no default specialization.
    assertFailsWith<NoSuchMethodException> {
      testClass.getMethod("test")
    }
  }

  @Test
  fun unexpectedArgumentType() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          @Test
          fun test(espresso: String) {
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains("CoffeeTest.kt:7:12 Expected an enum for @Burst test parameter")
  }

  @Test
  fun unexpectedDefaultArgumentValue() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        val defaultEspresso = Espresso.Regular

        @Burst
        class CoffeeTest {
          @Test
          fun test(espresso: Espresso = defaultEspresso) {
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains("CoffeeTest.kt:9:12 @Burst default parameter must be an enum constant (or absent)")
  }

  @Test
  fun constructorParameters() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        @Burst
        class CoffeeTest(
          private val espresso: Espresso,
          private val dairy: Dairy,
        ) {
          val log = mutableListOf<String>()

          @BeforeTest
          fun setUp() {
            log += "set up ${'$'}espresso ${'$'}dairy"
          }

          @Test
          fun test() {
            log += "running ${'$'}espresso ${'$'}dairy"
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        enum class Dairy { None, Milk, Oat }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")

    // Burst opens the class because it needs to subclass it.
    assertThat(Modifier.isFinal(baseClass.modifiers)).isFalse()

    // Burst doesn't add a no-arg constructor because there's no default specialization.
    assertFailsWith<NoSuchMethodException> {
      baseClass.getConstructor()
    }

    // It generates a subclass for each specialization.
    val sampleClass = result.classLoader.loadClass("CoffeeTest_Regular_Milk")
    val sampleConstructor = sampleClass.getConstructor()
    val sampleInstance = sampleConstructor.newInstance()
    val sampleLog = sampleClass.getMethod("getLog")
      .invoke(sampleInstance) as MutableList<*>
    sampleClass.getMethod("setUp").invoke(sampleInstance)
    sampleClass.getMethod("test").invoke(sampleInstance)
    assertThat(sampleLog).containsExactly(
      "set up Regular Milk",
      "running Regular Milk",
    )
    sampleLog.clear()
  }

  @Test
  fun defaultArgumentsHonored() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        @Burst
        class CoffeeTest(
          private val espresso: Espresso = Espresso.Regular,
        ) {
          val log = mutableListOf<String>()

          @BeforeTest
          fun setUp() {
            log += "set up ${'$'}espresso"
          }

          @Test
          fun test(dairy: Dairy = Dairy.Milk) {
            log += "running ${'$'}espresso ${'$'}dairy"
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        enum class Dairy { None, Milk, Oat }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseConstructor = baseClass.constructors.single { it.parameterCount == 0 }
    val baseInstance = baseConstructor.newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    // The setUp function gets the default parameter value.
    baseClass.getMethod("setUp").invoke(baseInstance)
    assertThat(baseLog).containsExactly("set up Regular")
    baseLog.clear()

    // The test function gets its default parameter value.
    baseClass.getMethod("test").invoke(baseInstance)
    assertThat(baseLog).containsExactly("running Regular Milk")
    baseLog.clear()

    // The default specialization's subclass is not generated.
    assertFailsWith<ClassNotFoundException> {
      result.classLoader.loadClass("CoffeeTest_Regular")
    }

    // Other subclasses are available.
    result.classLoader.loadClass("CoffeeTest_Double")

    // The default test function is also not generated.
    assertFailsWith<NoSuchMethodException> {
      baseClass.getMethod("test_Milk")
    }

    // Other test functions are available.
    baseClass.getMethod("test_Oat")
  }
}

@ExperimentalCompilerApi
fun compile(
  sourceFiles: List<SourceFile>,
  plugin: CompilerPluginRegistrar = BurstCompilerPluginRegistrar(),
): JvmCompilationResult {
  return KotlinCompilation().apply {
    sources = sourceFiles
    compilerPluginRegistrars = listOf(plugin)
    inheritClassPath = true
  }.compile()
}

@ExperimentalCompilerApi
fun compile(
  sourceFile: SourceFile,
  plugin: CompilerPluginRegistrar = BurstCompilerPluginRegistrar(),
): JvmCompilationResult {
  return compile(listOf(sourceFile), plugin)
}
