package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor
import kotlin.test.Test

class SymbolNamesTest {
  @InterceptTest
  val interceptor = BasicInterceptor()

  @Test
  fun test() {
  }

  @Test
  fun `test function with spaces`() {
  }

  class Nested {
    @InterceptTest
    val interceptor = BasicInterceptor()

    @Test
    fun test() {
    }
  }

  class Enclosing {
    class TwiceNested {
      @InterceptTest
      val interceptor = BasicInterceptor()

      @Test
      fun test() {
      }
    }
  }

  class BasicInterceptor : TestInterceptor {
    override fun intercept(testFunction: TestFunction) {
      println("intercepting ${testFunction.packageName} ${testFunction.className} ${testFunction.functionName}")
      testFunction()
    }
  }
}
