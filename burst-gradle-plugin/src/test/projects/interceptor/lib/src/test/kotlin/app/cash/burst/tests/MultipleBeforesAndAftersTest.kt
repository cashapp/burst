package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MultipleBeforesAndAftersTest {
  @InterceptTest
  val interceptor = BasicInterceptor()

  @BeforeTest
  fun beforeTestRed() {
    println("beforeTest red")
  }

  @BeforeTest
  fun beforeTestBlue() {
    println("beforeTest blue")
  }

  @BeforeTest
  fun beforeTestGreen() {
    println("beforeTest green")
  }

  @AfterTest
  fun afterTestRed() {
    println("afterTest red")
  }

  @AfterTest
  fun afterTestBlue() {
    println("afterTest blue")
  }

  @AfterTest
  fun afterTestGreen() {
    println("afterTest green")
  }

  @Test
  fun passingTest() {
    println("running")
  }

  class BasicInterceptor : TestInterceptor {
    override fun intercept(testFunction: TestFunction) {
      println("intercepting")
      testFunction()
      println("intercepted")
    }
  }
}
