package app.cash.burst.tests

import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor

class BasicInterceptor : TestInterceptor {
  override fun intercept(testFunction: TestFunction) {
    println("intercepting ${testFunction.functionName}")
    testFunction()
    println("intercepted ${testFunction.functionName}")
  }
}
