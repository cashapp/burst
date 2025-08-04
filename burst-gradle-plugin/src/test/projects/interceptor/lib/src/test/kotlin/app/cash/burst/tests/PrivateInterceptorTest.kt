package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestInterceptor
import kotlin.test.Test

class PrivateInterceptorTest {
  @InterceptTest
  private val interceptor = PrivateInterceptor()

  @Test
  fun test() {
    println("running")
  }

  private class PrivateInterceptor : TestInterceptor {
    override fun intercept(test: TestInterceptor.Test) {
      println("intercepting")
      test()
      println("intercepted")
    }
  }
}
