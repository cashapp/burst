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
package app.cash.burst

import app.cash.burst.coroutines.CoroutineTestFunction
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class CoroutineTestFunctionTest {
  @Test
  fun testToString() = runTest {
    val testFunction =
      object :
        CoroutineTestFunction(
          scope = this,
          packageName = "app.cash.burst.test",
          className = "SampleTest",
          classAnnotations = listOf(),
          functionName = "happyPath",
          functionAnnotations = listOf(),
        ) {
        override suspend fun invoke() {}
      }
    assertThat(testFunction.toString()).isEqualTo("app.cash.burst.test.SampleTest.happyPath")
  }

  @Test
  fun testToStringWithEmptyPackageName() = runTest {
    val testFunction =
      object :
        CoroutineTestFunction(
          scope = this,
          packageName = "",
          className = "SampleTest",
          classAnnotations = listOf(),
          functionName = "happyPath",
          functionAnnotations = listOf(),
        ) {
        override suspend fun invoke() {}
      }
    assertThat(testFunction.toString()).isEqualTo("SampleTest.happyPath")
  }
}
