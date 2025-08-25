package app.cash.burst.testlib

import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor

class BasicInterceptor(val name: String) : TestInterceptor {
  override fun intercept(testFunction: TestFunction) {
    println("> intercepting $name")
    testFunction()
    println("< intercepted $name")
  }
}
