package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.Test

class AfterTestThrowsTest {
  @InterceptTest
  val interceptor = CatchingInterceptor()

  @AfterTest
  fun afterTestRed() {
    println("afterTest red")
  }

  @AfterTest
  fun afterTestBlue() {
    error("boom!")
  }

  @AfterTest
  fun afterTestGreen() {
    println("afterTest green")
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
