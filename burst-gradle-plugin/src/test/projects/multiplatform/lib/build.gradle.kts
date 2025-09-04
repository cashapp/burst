plugins {
  kotlin("multiplatform")
  id("app.cash.burst")
}

kotlin {
  jvm()
  js {
    nodejs()
  }

  // Cover the host platforms where we run Burst tests.
  linuxX64()
  macosArm64()

  sourceSets {
    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.coroutines.test)
      }
    }
  }
}
