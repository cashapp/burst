import app.cash.burst.Burst
import app.cash.burst.burstValues
import kotlin.test.Test

@Burst
class CoffeeTest : BaseCoffeeTest() {
  @Test
  override fun hasRealOverride(dairy: String) {
    println("running realOverride $dairy")
  }
}

@Burst
abstract class BaseCoffeeTest {
  @Test
  open fun hasRealOverride(dairy: String = burstValues("Milk", "Oat")) {
    error("unexpected call")
  }

  @Test
  open fun hasFakeOverride(dairy: String = burstValues("Milk", "Oat")) {
    println("running fakeOverride $dairy")
  }
}
