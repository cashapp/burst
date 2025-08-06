import app.cash.burst.InterceptTest
import app.cash.burst.TestFunction
import app.cash.burst.TestInterceptor
import kotlin.test.Test

class EmptyPackageNameTest {
  @InterceptTest
  val interceptor = BasicInterceptor()

  @Test
  fun test() {
  }

  class BasicInterceptor : TestInterceptor {
    override fun intercept(testFunction: TestFunction) {
      println("intercepting '${testFunction.packageName}' '${testFunction.className}' '${testFunction.functionName}'")
      testFunction()
    }
  }
}
