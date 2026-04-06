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
  fun test(volume: Int = burstValues(12, 16, 20)) {
    log += "running $volume"
  }
}

fun box(): String {
  val instance = CoffeeTest()

  instance.invokeSpecialization("test")
  assertThat(instance.log).containsExactly("running 12")
  instance.log.clear()

  // The default test function is not generated
  assertFailsWith<NoSuchMethodException> { instance.invokeSpecialization("test_12") }

  // Other test functions are available, named by the literal values
  instance.invokeSpecialization("test_16")
  assertThat(instance.log).containsExactly("running 16")
  instance.log.clear()

  return "OK"
}
