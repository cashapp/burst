import app.cash.burst.Burst
import app.cash.burst.burstValues
import kotlin.test.BeforeTest
import kotlin.test.Test

@Burst
class CoffeeTest(private val espresso: String = burstValues("Decaf", "Regular", "Double")) {
  @BeforeTest
  fun setUp() {
    println("set up $espresso")
  }

  @Test
  fun test(size: Int = burstValues(8, 12, 16)) {
    println("running $espresso $size")
  }
}
