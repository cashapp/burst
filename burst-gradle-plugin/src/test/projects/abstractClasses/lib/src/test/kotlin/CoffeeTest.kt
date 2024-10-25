import app.cash.burst.Burst
import kotlin.test.Test

@Burst
abstract class BaseCoffeeTest(
  private val name: String,
) {
  @Test
  fun test(dairy: Dairy) {
    println("running $name $dairy")
  }
}

class CoffeeTest : BaseCoffeeTest("decaf")

enum class Dairy { None, Milk, Oat }
