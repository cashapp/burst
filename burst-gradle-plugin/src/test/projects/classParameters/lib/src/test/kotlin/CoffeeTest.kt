import app.cash.burst.Burst
import kotlin.test.BeforeTest
import kotlin.test.Test

@Burst
class CoffeeTest(private val espresso: Espresso, private val dairy: Dairy) {
  @BeforeTest
  fun setUp() {
    println("set up $espresso $dairy")
  }

  @Test
  fun test() {
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
