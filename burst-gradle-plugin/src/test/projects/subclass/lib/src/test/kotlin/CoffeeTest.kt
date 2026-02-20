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
class CoffeeTest : BaseCoffeeTest() {
  @Test
  override fun hasRealOverride(dairy: String) {
    println("running realOverride $dairy")
  }
}

@Burst
abstract class BaseCoffeeTest {
  @Test
  open fun hasRealOverride(dairy: String = burstValues("Milk", "Oat")) {
    error("unexpected call")
  }

  @Test
  open fun hasFakeOverride(dairy: String = burstValues("Milk", "Oat")) {
    println("running fakeOverride $dairy")
  }
}
