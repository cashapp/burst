package app.cash.burst.tests

import app.cash.burst.Burst
import app.cash.burst.InterceptTest
import kotlin.test.Test

@Burst
abstract class InterceptorAndBurstAbstractTest {
  abstract val name: String

  @InterceptTest
  val interceptor = BasicInterceptor("abstract")

  @Test
  fun test(condition: Boolean) {
    println("running $name $condition")
  }
}
