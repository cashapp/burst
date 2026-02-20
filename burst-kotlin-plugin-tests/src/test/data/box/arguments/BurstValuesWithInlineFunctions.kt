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
  fun test(
    greeting: () -> String = burstValues({ "Hello" }, { "Yo" }),
    subject: () -> String = burstValues({ "Burst" }, { "World" }),
  ) {
    log += "${greeting()} ${subject()}"
  }
}

// Confirm that inline function declarations are assigned parents.
fun box(): String {
  val instance = CoffeeTest()

  instance.invokeSpecialization("test")
  instance.invokeSpecialization("test_0_1")
  instance.invokeSpecialization("test_1_0")
  instance.invokeSpecialization("test_1_1")

  assertThat(instance.log).containsExactly("Hello Burst", "Hello World", "Yo Burst", "Yo World")

  return "OK"
}
