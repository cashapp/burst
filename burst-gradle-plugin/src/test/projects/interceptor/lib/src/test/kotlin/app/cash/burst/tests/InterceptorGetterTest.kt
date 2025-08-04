package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class InterceptorGetterTest {
  @InterceptTest
  val interceptorRed: TestInterceptor
    get() {
      println("getting interceptor red")
      return BasicInterceptor("red")
    }

  @InterceptTest
  val interceptorBlue: TestInterceptor
    get() {
      println("getting interceptor blue")
      return BasicInterceptor("blue")
    }

  @InterceptTest
  val interceptorGreen: TestInterceptor
    get() {
      println("getting interceptor green")
      return BasicInterceptor("green")
    }

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

  class BasicInterceptor(val name: String) : TestInterceptor {
    override fun intercept(test: TestInterceptor.Test) {
      println("intercepting $name")
      test()
      println("intercepted $name")
    }
  }
}
