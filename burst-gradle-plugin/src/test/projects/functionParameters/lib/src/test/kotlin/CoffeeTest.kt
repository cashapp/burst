import app.cash.burst.Burst
import kotlin.test.Test

@Burst
class CoffeeTest {
  @Test
  fun test(espresso: Espresso, dairy: Dairy) {
    println("running $espresso $dairy")
  }
}

enum class Espresso { Decaf, Regular, Double }

enum class Dairy { None, Milk, Oat }
