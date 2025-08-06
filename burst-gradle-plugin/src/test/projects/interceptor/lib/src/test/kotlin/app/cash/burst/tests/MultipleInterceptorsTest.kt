package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor
import kotlin.test.Test

class MultipleInterceptorsTest {
  @InterceptTest
  val interceptorRed = LoggingInterceptor("red")

  @InterceptTest
  val interceptorBlue = LoggingInterceptor("blue")

  @InterceptTest
  val interceptorGreen = LoggingInterceptor("green")

  @Test
  fun passingTest() {
    println("running")
  }

  class LoggingInterceptor(val name: String) : TestInterceptor {
    override fun intercept(testFunction: TestFunction) {
      println("intercepting $name (${testFunction.packageName} ${testFunction.className} ${testFunction.functionName})")
      testFunction()
      println("intercepted $name")
    }
  }
}
