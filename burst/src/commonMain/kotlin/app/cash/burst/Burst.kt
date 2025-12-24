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
package app.cash.burst

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION) annotation class Burst

/**
 * This is a ✨ magic ✨ function that Burst will expand during compilation into specializations. Use
 * it in as a parameter's default value when declaring a test function:
 * ```kotlin
 * @Test
 * fun drinkSoda(volume: Int = burstValues(12, 16, 24)) {
 *   ...
 * }
 * ```
 *
 * This function has many limitations:
 * * It must only be used to declare the default value of a parameter.
 * * Its arguments must be literal values or uses of named declarations.
 */
fun <T> burstValues(default: T, vararg rest: T): T = throw UnsupportedOperationException()
