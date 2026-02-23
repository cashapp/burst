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
import kotlin.test.BeforeTest
import kotlin.test.Test

@Burst
class CoffeeTest(private val espresso: Espresso, private val dairy: Dairy) {
  val log = mutableListOf<String>()

  @BeforeTest
  fun setUp() {
    log += "set up $espresso $dairy"
  }

  @Test
  fun test() {
    log += "running $espresso $dairy"
  }
}

enum class Espresso {
  Decaf,
  Regular,
  Double,
}

enum class Dairy {
  None,
  Milk,
  Oat,
}

fun box(): String {
  val instance =
    loadClassInstance<CoffeeTest>("CoffeeTest_Regular_Milk").apply {
      setUp()
      test()
    }

  assertThat(instance.log).containsExactly("set up Regular Milk", "running Regular Milk")

  return "OK"
}
