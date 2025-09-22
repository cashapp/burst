package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor
import kotlin.test.BeforeTest
import kotlin.test.Test

class BeforeTestInSuperclassTest {
  open class ShapeTest {
    @BeforeTest
    fun beforeTest() {
      println("beforeTest")
    }
  }

  class CircleTest : ShapeTest() {
    @InterceptTest
    val interceptor = LoggingInterceptor()

    @Test
    fun passingTest() {
      println("running")
    }
  }

  class LoggingInterceptor : TestInterceptor {
    override fun intercept(testFunction: TestFunction) {
      println("intercepting $testFunction")
      testFunction()
      println("intercepted $testFunction")
    }
  }
}
