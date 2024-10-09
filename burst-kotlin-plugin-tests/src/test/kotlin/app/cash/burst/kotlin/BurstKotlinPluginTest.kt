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
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class BurstKotlinPluginTest {
  @Test
  fun happyPath() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        package app.cash.burst.testing

        import app.cash.burst.Burst
        import kotlin.test.Ignore
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(espresso: Espresso, dairy: Dairy) {
            log += "running ${'$'}espresso ${'$'}dairy"
          }

          // Generate this
          @Test
          @Ignore
          fun x_test() {
            x_test_Decaf_None()
          }

          // Generate this
          @Test
          fun x_test_Decaf_None() {
            test(Espresso.Decaf, Dairy.None)
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        enum class Dairy { None, Milk, Oat }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val adapterClass = result.classLoader.loadClass("app.cash.burst.testing.CoffeeTest")
    val adapterInstance = adapterClass.constructors.single().newInstance()
    val log = adapterClass.getMethod("getLog").invoke(adapterInstance) as MutableList<*>

    // Burst adds @Ignore to the original test.
    val originalTest = adapterClass.methods.single { it.name == "test" && it.parameterCount == 2 }
    assertThat(originalTest.isAnnotationPresent(Test::class.java)).isTrue()
    assertThat(originalTest.isAnnotationPresent(Ignore::class.java)).isTrue()

    // Burst adds a variant for each combination of parameters.
    val sampleVariant = adapterClass.getMethod("test_Decaf_None")
    assertThat(sampleVariant.isAnnotationPresent(Test::class.java)).isTrue()
    assertThat(sampleVariant.isAnnotationPresent(Ignore::class.java)).isFalse()
    sampleVariant.invoke(adapterInstance)
    assertThat(log).containsExactly("running Decaf None")
    log.clear()

    // Burst adds a no-parameter function that calls each variant in sequence.
    val noArgsTest = adapterClass.getMethod("test")
    assertThat(noArgsTest.isAnnotationPresent(Test::class.java)).isTrue()
    assertThat(noArgsTest.isAnnotationPresent(Ignore::class.java)).isTrue()
    noArgsTest.invoke(adapterInstance)
    assertThat(log).containsExactly(
      "running Decaf None",
      "running Decaf Milk",
      "running Decaf Oat",
      "running Regular None",
      "running Regular Milk",
      "running Regular Oat",
      "running Double None",
      "running Double Milk",
      "running Double Oat",
    )
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
