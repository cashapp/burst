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
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * This test doesn't call `runTest()` directly. It calls a helper that does, and returns that
 * helper's result. On Kotlin/JS a `TestResult` is a `Promise` that the test framework must receive
 * in order to await the test, so each generated specialization must return the delegate's result.
 */
@Burst
class CoffeeTest {
  val log = mutableListOf<String>()

  @Test
  fun test(espresso: Espresso): CoffeeTestResult = runCoffeeTest {
    delay(1000.milliseconds)
    log += "running $espresso"
  }

  private fun runCoffeeTest(testBody: suspend TestScope.() -> Unit): CoffeeTestResult {
    runTest(testBody = testBody)
    return CoffeeTestResult(log.toList())
  }
}

/**
 * Stands in for `kotlinx.coroutines.test.TestResult`, which is a typealias for `Unit` on the JVM.
 */
class CoffeeTestResult(val log: List<String>)

enum class Espresso {
  Decaf,
  Regular,
  Double,
}

fun box(): String {
  val instance = CoffeeTest()

  val result = instance.invokeSpecialization("test_Decaf")
  assertThat(result).isNotNull().isInstanceOf<CoffeeTestResult>()
  assertThat((result as CoffeeTestResult).log).containsExactly("running Decaf")

  return "OK"
}
