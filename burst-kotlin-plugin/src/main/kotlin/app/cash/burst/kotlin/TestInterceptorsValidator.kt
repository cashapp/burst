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

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.util.kotlinFqName

internal class TestInterceptorsValidator(
  val burstApis: BurstApis,
) {
  fun validate(input: TestInterceptorsInput) {
    val usesCoroutineTestInterceptor = input.usesCoroutineTestInterceptor
    val usesTestInterceptor = input.usesTestInterceptor

    if (input.otherInterceptTestProperties.isNotEmpty()) {
      throw BurstCompilationException(
        "@InterceptTest properties must extend either TestInterceptor or CoroutineTestInterceptor",
        input.otherInterceptTestProperties.first(),
      )
    }

    if (usesCoroutineTestInterceptor) {
      val testInterceptor = input.testInterceptors.firstOrNull()
      if (testInterceptor != null) {
        throw BurstCompilationException(
          "Cannot mix non-coroutine TestInterceptors with CoroutineTestInterceptors in the same test",
          testInterceptor,
        )
      }
    }

    if (usesTestInterceptor) {
      val coroutineTestInterceptor = input.coroutineTestInterceptors.firstOrNull()
      if (coroutineTestInterceptor != null) {
        throw BurstCompilationException(
          "Cannot mix non-coroutine TestInterceptors with CoroutineTestInterceptors in the same test",
          coroutineTestInterceptor,
        )
      }
    }

    for (function in input.testFunctions) {
      if (
        (usesTestInterceptor || usesCoroutineTestInterceptor) &&
        function.function.modality != Modality.FINAL &&
        input.subject.modality != Modality.FINAL
      ) {
        throw BurstCompilationException(
          "@InterceptTest cannot target test functions that are non-final",
          function.function,
        )
      }

      if (usesTestInterceptor && function is TestInterceptorsInput.Function.Suspending) {
        val testInterceptor = input.testInterceptors.firstOrNull()
        throw BurstCompilationException(
          "${testInterceptor?.nameForError ?: "TestInterceptor"} cannot intercept a coroutine test function",
          function.function,
        )
      }

      if (usesCoroutineTestInterceptor && function is TestInterceptorsInput.Function.NonSuspending) {
        val testInterceptor = input.coroutineTestInterceptors.firstOrNull()
        throw BurstCompilationException(
          "${testInterceptor?.nameForError ?: "CoroutineTestInterceptor"} cannot intercept a non-coroutine test function",
          function.function,
        )
      }
    }
  }

  private val IrProperty.nameForError: String
    get() = "${parent.kotlinFqName}.$name"
}
