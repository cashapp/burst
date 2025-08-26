package app.cash.burst.testlib

import app.cash.burst.InterceptTest
import kotlin.test.Test

abstract class TopTest {
  @InterceptTest
  private val interceptor = BasicInterceptor("top")

  @Test
  fun testTop() {
    println("  running top")
  }
}
