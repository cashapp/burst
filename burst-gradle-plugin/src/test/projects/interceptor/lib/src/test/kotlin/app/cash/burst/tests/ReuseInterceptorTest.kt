package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestInterceptor
import kotlin.test.Test

class ReuseInterceptorTest {
  @InterceptTest
  val interceptorA = BasicInterceptor()

  @InterceptTest
  val interceptorB = interceptorA

  @InterceptTest
  val interceptorC = interceptorA

  @Test
  fun passingTest() {
    println("running")
  }

  class BasicInterceptor : TestInterceptor {
    override fun intercept(test: TestInterceptor.Test) {
      println("intercepting")
      test()
      println("intercepted")
    }
  }
}
