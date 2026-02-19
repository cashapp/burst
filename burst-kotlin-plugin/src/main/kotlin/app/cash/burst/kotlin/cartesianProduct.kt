/*
 * Copyright 2020 The Android Open Source Project
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

/**
 * Given an iterable lists like `[[A, B, C], [1, 2, 3]]`, return the cartesian product like `[[A,
 * 1], [A, 2], [A, 3], [B, 1], [B, 2], [B, 3], [C, 1], [C, 2], [C, 3]]`.
 */
fun <T> Iterable<List<T>>.cartesianProduct(): List<List<T>> {
  return fold(listOf(listOf())) { partials, list ->
    partials.flatMap { partial -> list.map { element -> partial + element } }
  }
}
