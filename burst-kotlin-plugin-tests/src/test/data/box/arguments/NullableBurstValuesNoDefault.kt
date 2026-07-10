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
import app.cash.burst.Burst
import app.cash.burst.burstValues
import kotlin.test.Test

@Burst
class CoffeeTest {
  val log = mutableListOf<String>()

  @Test
  fun test(volume: Int? = burstValues(12, 16, 20, null)) {
    log += "running $volume"
  }
}

fun box(): String {
  val instance = CoffeeTest()

  instance.invokeSpecialization("test")
  instance.invokeSpecialization("test_16")
  instance.invokeSpecialization("test_20")
  instance.invokeSpecialization("test_null")

  assertThat(instance.log)
    .containsExactly(
      "running 12",
      "running 16",
      "running 20",
      "running null",
    )

  return "OK"
}
