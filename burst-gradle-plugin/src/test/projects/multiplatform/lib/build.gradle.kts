plugins {
  kotlin("multiplatform")
  id("app.cash.burst")
}

kotlin {
  jvm()
  js {
    nodejs()
  }

  sourceSets {
    commonTest {
      dependencies {
        implementation(kotlin("test"))
      }
    }
  }
}
