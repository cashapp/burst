import app.cash.burst.InterceptTest
import app.cash.burst.TestInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SampleTest {
  @InterceptTest
  val testPhase = TestPhase()

  @BeforeTest
  fun setUp() {
    println("set up $testPhase")
  }

  @AfterTest
  fun tearDown() {
    println("tear down $testPhase")
  }

  @Test
  fun failingTest() {
    throw AssertionError("boom: $testPhase")
  }

  @Test
  fun passingTest() {
    println("running $testPhase")
  }
}

class TestPhase : TestInterceptor {
  var name = "Before"

  override fun intercept(test: TestInterceptor.Test) {
    println("intercepting ${test.packageName} ${test.className} ${test.functionName}")

    name = "During"
    try {
      test()
      name = "After Success"
    } catch (e: Throwable) {
      name = "After Failure"
      throw e
    }
  }

  override fun toString() = name
}
