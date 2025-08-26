package app.cash.burst.tests

import app.cash.burst.testlib.TopTest
import kotlin.test.Test

abstract class MiddleTest : TopTest() {
  @Test
  fun testMiddle() {
    println("  running middle")
  }
}
