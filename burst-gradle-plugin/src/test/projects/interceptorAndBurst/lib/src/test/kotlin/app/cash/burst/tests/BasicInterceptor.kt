package app.cash.burst.tests

import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor

class BasicInterceptor(val name: String) : TestInterceptor {
  override fun intercept(testFunction: TestFunction) {
    println("intercepting $name $testFunction")
    testFunction()
  }
}
