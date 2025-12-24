package app.cash.burst.tests

import app.cash.burst.InterceptTest

class KotlinTestTest {
  @InterceptTest val interceptor = BasicInterceptor()

  @kotlin.test.BeforeTest
  fun beforeTest() {
    println("@kotlin.test.BeforeTest before test")
  }

  @kotlin.test.AfterTest
  fun afterTest() {
    println("@kotlin.test.AfterTest after test")
  }

  @kotlin.test.Test
  fun kotlinTestTest() {
    println("@kotlin.test.Test running")
  }
}
