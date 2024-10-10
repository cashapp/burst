plugins {
  kotlin("multiplatform")
  id("app.cash.burst")
}

kotlin {
  jvm()

  sourceSets {
    commonTest {
      dependencies {
        implementation(kotlin("test"))
      }
    }
  }
}
