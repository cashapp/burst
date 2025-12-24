package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.Test

class AfterTestInSuperclassTest {
  open class ShapeTest {
    @AfterTest
    fun afterTest() {
      println("afterTest")
    }
  }

  class CircleTest : ShapeTest() {
    @InterceptTest val interceptor = LoggingInterceptor()

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
