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

sealed interface BrewStyle

object Aeropress : BrewStyle

object Espresso : BrewStyle

object Drip : BrewStyle

@Burst
class CoffeeTest {
  @Test fun test(style: BrewStyle = burstValues(Drip, Espresso, Aeropress)) {}
}

fun box(): String {
  assertThat(CoffeeTest::class.testSuffixes).containsExactlyInAnyOrder("Aeropress", "Espresso")

  return "OK"
}
