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

abstract class TestFunction(
  /** The package that this test is defined in, or "" it has none. */
  val packageName: String,

  /** The classes that enclose the test function, separated by '.'. */
  val className: String,

  /** The test function name. */
  val functionName: String,
) {
  /**
   * Runs the next interceptor in the chain if there is one.
   *
   * If there isn't, it runs the following in sequence:
   *
   *  * The `@BeforeTest` functions (if any)
   *  * The `@Test` function
   *  * The `@AfterTest` functions (if any)
   */
  abstract operator fun invoke()
}

interface TestInterceptor {
  fun intercept(testFunction: TestFunction)
}

@Target(AnnotationTarget.PROPERTY)
annotation class InterceptTest
