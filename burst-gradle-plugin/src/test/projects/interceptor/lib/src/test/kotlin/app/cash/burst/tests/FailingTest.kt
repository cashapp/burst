package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FailingTest {
  @InterceptTest
  val interceptor = CatchingInterceptor()

  @BeforeTest
  fun setUp() {
    println("set up")
  }

  @AfterTest
  fun tearDown() {
    println("tear down")
  }

  @Test
  fun failingTest() {
    error("boom!")
  }

  class CatchingInterceptor : TestInterceptor {
    override fun intercept(testFunction: TestFunction) {
      try {
        testFunction()
      } catch (e: Throwable) {
        println("re-throwing exception: ${e.message}")
        throw e
      }
    }
  }
}
