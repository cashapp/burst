import app.cash.burst.Burst
import app.cash.burst.burstValues
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@Burst
class TempDirTest {
  @Test
  fun test(
    @TempDir tempDir: File,
    string: String = burstValues("a", "b"),
  ) {
    println("running $tempDir $string")
  }
}
