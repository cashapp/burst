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
import assertk.assertions.isNotNull
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
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
        "LunchTest.kt",
        """
        package app.cash.burst.testing

        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class LunchTest {
          @Test
          fun test() {
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val adapterClass = result.classLoader.loadClass("app.cash.burst.testing.LunchTest")
    assertThat(adapterClass).isNotNull()
    assertThat(adapterClass.getMethod("test_2")).isNotNull()
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
