package app.cash.burst.tests

import app.cash.burst.InterceptTest

class OrgJunitJupiterApiTest {
  @InterceptTest
  val interceptor = BasicInterceptor()

  @org.junit.jupiter.api.BeforeEach
  fun beforeTest() {
    println("@org.junit.jupiter.api.BeforeEach before test")
  }

  @org.junit.jupiter.api.AfterEach
  fun afterTest() {
    println("@org.junit.jupiter.api.AfterEach after test")
  }

  @org.junit.jupiter.api.Test
  fun orgJunitJupiterApiTest() {
    println("@org.junit.jupiter.api.Test running")
  }
}
