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
import kotlin.test.Ignore
import kotlin.test.Test

@Burst
class CoffeeTest {
  val log = mutableListOf<String>()

  @Test
  @Ignore
  fun test(espresso: Espresso) {
    log += "running $espresso"
  }
}

@Burst
@Ignore
class Coffee2Test(private val espresso: Espresso) {
  val log = mutableListOf<String>()

  @Test
  fun test() {
    log += "running ${espresso}"
  }
}

enum class Espresso {
  Decaf,
  Regular,
  Double,
}
