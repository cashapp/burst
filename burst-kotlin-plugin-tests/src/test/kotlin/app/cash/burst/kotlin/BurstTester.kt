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

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

class BurstTester(
  val packageName: String,
) {
  private val testHarness = SourceFile.Companion.kotlin(
    "TestHarness.kt",
    """
    |${if (packageName != "") "package $packageName" else ""}
    |
    |val log = mutableListOf<String>()
    |
    |fun log(message: String) {
    |  log += message
    |}
    """.trimMargin(),
  )

  @ExperimentalCompilerApi
  fun compileAndRun(
    vararg sourceFiles: SourceFile,
  ): List<String> {
    val result = compile(
      sourceFiles.toList() + testHarness,
      BurstCompilerPluginRegistrar(),
    )

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val packagePrefix = when (packageName) {
      "" -> ""
      else -> "$packageName."
    }
    val main = result.classLoader.loadClass("${packagePrefix}MainKt")

    val testInstance = main.getMethod("main", String::class.java.arrayType())
    testInstance.invoke(null, arrayOf<String>())

    val testHarness = result.classLoader.loadClass("${packagePrefix}TestHarnessKt")
    val log = testHarness.getMethod("getLog").invoke(null) as List<String>

    return log.toList()
  }
}
