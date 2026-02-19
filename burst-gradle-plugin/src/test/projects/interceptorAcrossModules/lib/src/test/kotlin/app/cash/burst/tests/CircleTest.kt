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
package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.testlib.BasicInterceptor
import app.cash.burst.testlib.ShapeTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/** This inherits a test function from the superclass. */
open class CircleTest : ShapeTest() {
  @InterceptTest val circleInterceptor = BasicInterceptor("circle")

  @BeforeTest
  fun beforeTestCircle() {
    println("> before test circle")
  }

  @AfterTest
  fun afterTestCircle() {
    println("< after test circle")
  }
}
