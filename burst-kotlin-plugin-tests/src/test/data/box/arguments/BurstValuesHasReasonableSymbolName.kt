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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test

@Burst
class CoffeeTest {
  @Test
  fun test(
    content: Any? =
      burstValues(
        3, // No name is generated for the first value.
        "5".toInt(),
        "hello",
        "hello".uppercase(),
        CoffeeTest::class,
        Float.MAX_VALUE,
        PI,
        String.CASE_INSENSITIVE_ORDER,
        abs(1),
        null,
      )
  ) {}
}

fun box(): String {
  assertThat(CoffeeTest::class.testSuffixes)
    .containsExactlyInAnyOrder(
      "toInt",
      "hello",
      "uppercase",
      "CoffeeTest",
      // Would have preferred 'MAX_VALUE', but this is constant is inlined!
      "3_4028235E38",
      // Would have preferred 'PI', but this is constant is inlined!
      "3_141592653589793",
      "CASE_INSENSITIVE_ORDER",
      "abs",
      "null",
    )

  return "OK"
}
