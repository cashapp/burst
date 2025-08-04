package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RetryingTest {
  @InterceptTest
  val interceptor = RetryingInterceptor()

  var attempts = 0

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
    check(++attempts == 3) { "not enough attempts" }
  }

  class RetryingInterceptor : TestInterceptor {
    override fun intercept(test: TestInterceptor.Test) {
      for (i in 0 until 5) {
        try {
          println("attempt $i")
          test()
          println("success")
          return
        } catch (e: Throwable) {
          println("failed: ${e.message}")
        }
      }
    }
  }
}
