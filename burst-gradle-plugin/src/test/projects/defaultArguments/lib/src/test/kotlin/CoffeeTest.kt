import app.cash.burst.Burst
import kotlin.test.BeforeTest
import kotlin.test.Test

@Burst
class CoffeeTest(
  private val espresso: Espresso = Espresso.Regular,
) {
  @BeforeTest
  fun setUp() {
    println("set up $espresso")
  }

  @Test
  fun test(dairy: Dairy = Dairy.Milk) {
    println("running $espresso $dairy")
  }
}

enum class Espresso { Decaf, Regular, Double }

enum class Dairy { None, Milk, Oat }
