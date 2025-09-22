package app.cash.burst.testlib

import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor

class BasicInterceptor(val name: String) : TestInterceptor {
  override fun intercept(testFunction: TestFunction) {
    println("> intercepting $testFunction")
    testFunction()
    println("< intercepted $testFunction")
  }
}
