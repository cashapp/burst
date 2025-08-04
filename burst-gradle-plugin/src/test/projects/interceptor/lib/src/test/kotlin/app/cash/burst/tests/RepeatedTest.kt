package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RepeatedTest {
  @InterceptTest
  val interceptor = RepeatingInterceptor()

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

  class RepeatingInterceptor : TestInterceptor {
    override fun intercept(test: TestInterceptor.Test) {
      for (i in 0 until 3) {
        println("run $i")
        test()
      }
    }
  }
}
