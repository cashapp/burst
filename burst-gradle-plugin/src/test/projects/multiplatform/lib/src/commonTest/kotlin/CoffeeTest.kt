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
import kotlin.coroutines.coroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@Burst
class CoffeeTest(private val espresso: Espresso) {
  @BeforeTest
  fun setUp() {
    println("set up $espresso")
  }

  @AfterTest
  fun tearDown() {
    println("tear down $espresso")
  }

  @Test
  fun basicTest(dairy: Dairy) {
    println("running $espresso $dairy")
  }

  @Test
  fun coroutinesTest(dairy: Dairy) =
    runTest(CoroutineName("coffeeCoroutine")) {
      val deferred = async {
        println("running $espresso $dairy in ${coroutineContext[CoroutineName]?.name}")
      }
      delay(1000.milliseconds)
      deferred.await()
    }

  /**
   * This test doesn't call `runTest()` directly, so Burst can't inline it. On Kotlin/JS the
   * returned `TestResult` is a `Promise` that the test framework must receive to await the test.
   */
  @Test
  fun coroutinesInHelperTest(dairy: Dairy) = runCoffeeTest {
    delay(1000.milliseconds)
    println("running $espresso $dairy in helper")
  }
}

private fun runCoffeeTest(testBody: suspend TestScope.() -> Unit): TestResult =
  runTest(testBody = testBody)

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
