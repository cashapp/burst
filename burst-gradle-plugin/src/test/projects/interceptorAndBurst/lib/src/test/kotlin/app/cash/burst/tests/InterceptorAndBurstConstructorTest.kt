package app.cash.burst.tests

import app.cash.burst.Burst
import app.cash.burst.InterceptTest
import kotlin.test.Test

@Burst
class InterceptorAndBurstConstructorTest(val condition: Boolean) {
  @InterceptTest val interceptor = BasicInterceptor("$condition")

  @Test
  fun test() {
    println("running $condition")
  }
}
