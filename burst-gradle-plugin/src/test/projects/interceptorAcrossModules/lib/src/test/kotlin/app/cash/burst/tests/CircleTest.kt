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
