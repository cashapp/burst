package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class BeforeTestThrowsTest {
  @InterceptTest
  val interceptor = CatchingInterceptor()

  @BeforeTest
  fun beforeTestRed() {
    println("beforeTest red")
  }

  @BeforeTest
  fun beforeTestBlue() {
    error("boom!")
  }

  @BeforeTest
  fun beforeTestGreen() {
    println("beforeTest green")
  }

  @AfterTest
  fun afterTest() {
    println("afterTest")
  }

  @Test
  fun passingTest() {
    println("running")
  }

  class CatchingInterceptor : TestInterceptor {
    override fun intercept(test: TestInterceptor.Test) {
      try {
        test()
      } catch (e: Throwable) {
        println("re-throwing exception: ${e.message}")
        throw e
      }
    }
  }
}
