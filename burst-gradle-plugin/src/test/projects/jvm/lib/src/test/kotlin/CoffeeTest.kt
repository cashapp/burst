import app.cash.burst.Burst
import kotlin.test.Test

@Burst
class CoffeeTest {
  val log = mutableListOf<String>()

  @Test
  fun test(espresso: Espresso, dairy: Dairy) {
    log += "running $espresso $dairy"
  }
}

enum class Espresso { Decaf, Regular, Double }

enum class Dairy { None, Milk, Oat }
