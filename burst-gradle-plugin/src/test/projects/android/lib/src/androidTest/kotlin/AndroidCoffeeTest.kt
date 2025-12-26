import app.cash.burst.Burst
import kotlin.test.BeforeTest
import kotlin.test.Test

@Burst
class AndroidCoffeeTest(private val espresso: Espresso) {
  @BeforeTest
  fun setUp() {
    println("set up $espresso")
  }

  @Test
  fun test(dairy: Dairy) {
    println("running $espresso $dairy")
  }
}

enum class Espresso {
  Decaf,
  Regular,
  Double,
}

enum class Dairy {
  None,
  Milk,
  Oat,
}
