package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor
import kotlin.test.Test

class InterceptInSuperclassTest {
  open class ShapeTest {
    @InterceptTest
    val shapeInterceptor = LoggingInterceptor("shape")
  }

  class CircleTest : ShapeTest() {
    @InterceptTest
    val circleInterceptor = LoggingInterceptor("circle")

    @Test
    fun passingTest() {
      println("running")
    }
  }

  class LoggingInterceptor(val name: String) : TestInterceptor {
    override fun intercept(testFunction: TestFunction) {
      println("intercepting $name (${testFunction.packageName} ${testFunction.className} ${testFunction.functionName})")
      testFunction()
      println("intercepted $name")
    }
  }
}
