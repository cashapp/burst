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
package app.cash.burst.test

inline fun <reified T> loadClassInstance(specialization: String): T {
  val clazz = T::class.java.classLoader.loadClass(specialization)
  val constructor = clazz.getConstructor()
  return constructor.newInstance() as T
}

inline fun <reified T> T.invokeSpecialization(specialization: String) {
  T::class.java.getMethod(specialization).invoke(this)
}
