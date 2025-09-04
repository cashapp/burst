import app.cash.burst.Burst
import kotlin.coroutines.coroutineContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

@Burst
class CoffeeTest(
  private val espresso: Espresso,
) {
  @BeforeTest
  fun setUp() {
    println("set up $espresso")
  }

  @Test
  fun basicTest(dairy: Dairy) {
    println("running $espresso $dairy")
  }

  @Test
  fun coroutinesTest(dairy: Dairy) = runTest(CoroutineName("coffeeCoroutine")) {
    val deferred = async {
      println("running $espresso $dairy in ${coroutineContext[CoroutineName]?.name}")
    }
    delay(1000.milliseconds)
    deferred.await()
  }
}

enum class Espresso { Decaf, Regular, Double }

enum class Dairy { None, Milk, Oat }
