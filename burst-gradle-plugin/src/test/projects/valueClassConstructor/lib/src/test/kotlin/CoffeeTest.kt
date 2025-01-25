import app.cash.burst.Burst
import app.cash.burst.burstValues
import kotlin.test.Test

@Burst
class CoffeeTest(
  private val espresso: Espresso = burstValues(Decaf, Regular, Double),
) {
  @Test
  fun test() {
    println("running $espresso")
  }
}

@JvmInline
value class Espresso(val ordinal: Int) {
  override fun toString() = when (this) {
    Decaf -> "Decaf"
    Regular -> "Regular"
    Double -> "Double"
    else -> "$ordinal"
  }
}

val Decaf = Espresso(0)
val Regular = Espresso(1)
val Double = Espresso(2)
