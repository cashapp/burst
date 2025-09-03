package app.cash.burst.tests

import app.cash.burst.InterceptTest
import app.cash.burst.coroutines.CoroutineTestFunction
import app.cash.burst.coroutines.CoroutineTestInterceptor
import kotlin.coroutines.coroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

/**
 * Note that although this test includes 4 seconds of delays, it takes advantage of the
 * `TestCoroutineScheduler` to skip these delays.
 */
class CoroutinesTest {
  @InterceptTest
  val interceptor = DelayInterceptor()

  @BeforeTest
  fun setUp() {
    println("set up")
  }

  @AfterTest
  fun tearDown() {
    println("tear down")
  }

  @Test
  fun passingTest() = runTest(CoroutineName("passingCoroutine")) {
    val deferred = async {
      println("running in ${coroutineContext[CoroutineName]?.name}")
    }
    delay(1000.milliseconds)
    deferred.await()
  }

  class DelayInterceptor : CoroutineTestInterceptor {
    override suspend fun intercept(testFunction: CoroutineTestFunction) {
      coroutineScope {
        val before = async {
          println("intercepting $testFunction in ${coroutineContext[CoroutineName]?.name}")
        }
        delay(1000.milliseconds)
        before.await()

        val execute = async {
          testFunction()
        }
        delay(1000.milliseconds)
        execute.await()

        val after = async {
          println("intercepted")
        }
        delay(1000.milliseconds)
        before.await()
      }
    }
  }
}
