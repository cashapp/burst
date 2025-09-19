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

  @kotlin.test.Test
  fun kotlinTestTest(dairy: Dairy) {
    println("@kotlin.test.Test running $espresso $dairy")
  }

  @org.junit.Test
  fun orgJunitTest(dairy: Dairy) {
    println("@org.junit.Test running $espresso $dairy")
  }

  @org.junit.jupiter.api.Test
  fun orgJunitJupiterApiTest(dairy: Dairy) {
    println("@org.junit.jupiter.api.Test running $espresso $dairy")
  }
}

enum class Espresso { Decaf, Regular, Double }

enum class Dairy { None, Milk, Oat }
