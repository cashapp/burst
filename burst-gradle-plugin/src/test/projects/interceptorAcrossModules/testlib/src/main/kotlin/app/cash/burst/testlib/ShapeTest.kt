package app.cash.burst.testlib

import app.cash.burst.InterceptTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

open class ShapeTest {
  @InterceptTest
  val shapeInterceptor = BasicInterceptor("shape")

  @BeforeTest
  fun beforeTest() {
    println("> before test shape")
  }

  @AfterTest
  fun afterTest() {
    println("< after test shape")
  }

  @Test
  fun testShape() {
    println("  running testShape")
  }
}
