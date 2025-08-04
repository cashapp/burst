import app.cash.burst.InterceptTest
import app.cash.burst.TestInterceptor
import kotlin.test.Test

class EmptyPackageNameTest {
  @InterceptTest
  val interceptor = BasicInterceptor()

  @Test
  fun test() {
  }

  class BasicInterceptor : TestInterceptor {
    override fun intercept(test: TestInterceptor.Test) {
      println("intercepting '${test.packageName}' '${test.className}' '${test.functionName}'")
      test()
    }
  }
}
