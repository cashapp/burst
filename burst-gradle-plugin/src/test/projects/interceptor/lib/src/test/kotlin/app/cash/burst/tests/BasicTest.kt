package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class BasicTest {
  @InterceptTest
  val interceptor = BasicInterceptor()

  @BeforeTest
  fun setUp() {
    println("set up")
  }

  @AfterTest
  fun tearDown() {
    println("tear down")
  }

  @Test
  fun passingTest() {
    println("running")
  }

  class BasicInterceptor : TestInterceptor {
    override fun intercept(test: TestInterceptor.Test) {
      println("intercepting")
      println("  packageName=${test.packageName}")
      println("  className=${test.className}")
      println("  functionName=${test.functionName}")
      test()
      println("intercepted")
    }
  }
}
