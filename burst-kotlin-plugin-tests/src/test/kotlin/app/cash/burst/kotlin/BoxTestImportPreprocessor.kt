/*
 * Copyright (C) 2026 Cash App
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
// Adapted from Metro's ImportsPreprocessor
package app.cash.burst.kotlin

import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.services.ReversibleSourceFilePreprocessor
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.isJavaFile

class BoxTestImportPreprocessor(testServices: TestServices) :
  ReversibleSourceFilePreprocessor(testServices) {
  private val additionalImports =
    setOf("app.cash.burst.test.*", "assertk.assertThat", "assertk.assertions.*")

  private val additionalImportsString by lazy {
    additionalImports.sorted().joinToString(separator = "\n") { "import $it" }
  }

  override fun process(file: TestFile, content: String): String {
    if (file.isAdditional) return content
    if (file.isJavaFile) return content

    val lines = content.lines().toMutableList()
    when (val packageIndex = lines.indexOfFirst { it.startsWith("package ") }) {
      // No package declaration found.
      -1 ->
        when (val nonBlankIndex = lines.indexOfFirst { it.isNotBlank() }) {
          // No non-blank lines? Place imports at the very beginning...
          -1 -> lines.add(0, additionalImportsString)

          // Place imports before first non-blank line.
          else -> lines.add(nonBlankIndex, additionalImportsString)
        }

      // Place imports just after package declaration.
      else -> lines.add(packageIndex + 1, additionalImportsString)
    }
    return lines.joinToString(separator = "\n")
  }

  override fun revert(file: TestFile, actualContent: String): String {
    if (file.isAdditional) return actualContent
    if (file.isJavaFile) return actualContent
    return actualContent.replace(additionalImportsString + "\n", "")
  }
}
