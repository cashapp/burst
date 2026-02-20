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
  @Test
  fun test(
    content: Any? =
      burstValues(
        3, // No name is generated for the first value.
        "1",
        1,
        1L,
        "CASE_INSENSITIVE_ORDER",
        String.CASE_INSENSITIVE_ORDER,
        true,
        "true",
      )
  ) {}
}

fun box(): String {
  assertThat(CoffeeTest::class.testSuffixes)
    .containsExactlyInAnyOrder(
      "1_1",
      "2_1",
      "3_1",
      "4_CASE_INSENSITIVE_ORDER",
      "5_CASE_INSENSITIVE_ORDER",
      "6_true",
      "7_true",
    )

  return "OK"
}
