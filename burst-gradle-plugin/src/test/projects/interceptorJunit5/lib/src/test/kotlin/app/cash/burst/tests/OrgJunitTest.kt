package app.cash.burst.tests

import app.cash.burst.InterceptTest

class OrgJunitTest {
  @InterceptTest val interceptor = BasicInterceptor()

  @org.junit.Before
  fun beforeTest() {
    println("@org.junit.Before before test")
  }

  @org.junit.After
  fun afterTest() {
    println("@org.junit.After after test")
  }

  @org.junit.Test
  fun orgJunitTest() {
    println("@org.junit.Test running")
  }
}
