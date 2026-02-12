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
package app.cash.burst.coroutines

import kotlinx.coroutines.test.TestScope

/**
 * Intercepts the execution of a test function, including its `@BeforeTest` and `@AfterTest`
 * functions.
 *
 * Test functions must make a single call to `kotlinx.coroutines.test.runTest` in their test body.
 */
fun interface CoroutineTestInterceptor {
  suspend fun intercept(testFunction: CoroutineTestFunction)
}

abstract class CoroutineTestFunction(
  val scope: TestScope,
  /** The package that this test is defined in, or "" it has none. */
  val packageName: String,
  /** The classes that enclose the test function, separated by '.'. */
  val className: String,
  /** Annotations applied to the enclosing class of the test function. */
  val classAnnotations: List<Annotation>,
  /** The test function name. */
  val functionName: String,
  /** Annotations applied to the test function. */
  val functionAnnotations: List<Annotation>,
) {
  /**
   * Runs the next interceptor in the chain if there is one.
   *
   * If there isn't, it runs the following in sequence:
   * * The `@BeforeTest` functions (if any)
   * * The `@Test` function
   * * The `@AfterTest` functions (if any)
   */
  abstract suspend operator fun invoke()

  /** Returns the full test name, like `com.example.project.FeatureTest.testMyFeature`. */
  override fun toString(): String {
    return buildString {
      if (packageName.isNotEmpty()) {
        append(packageName)
        append(".")
      }
      append(className)
      append(".")
      append(functionName)
    }
  }
}
