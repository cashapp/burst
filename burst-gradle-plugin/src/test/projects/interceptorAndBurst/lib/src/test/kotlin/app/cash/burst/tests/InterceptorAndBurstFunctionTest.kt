package app.cash.burst.tests

import app.cash.burst.Burst
import app.cash.burst.InterceptTest
import kotlin.test.Test

@Burst
class InterceptorAndBurstFunctionTest {
  @InterceptTest val interceptor = BasicInterceptor("function")

  @Test
  fun test(condition: Boolean) {
    println("running $condition")
  }
}
