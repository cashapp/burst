package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class InterceptorThrowsTest {
  @InterceptTest
  val interceptor = ThrowingInterceptor()

  @BeforeTest
  fun beforeTest() {
    println("before test")
  }

  @AfterTest
  fun afterTest() {
    println("after test")
  }

  @Test
  fun test() {
    println("running")
  }

  class ThrowingInterceptor : TestInterceptor {
    override fun intercept(testFunction: TestFunction) {
      testFunction()
      error("boom!")
    }
  }
}
