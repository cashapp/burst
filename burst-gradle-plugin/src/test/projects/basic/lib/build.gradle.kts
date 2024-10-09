plugins {
  kotlin("multiplatform")
  id("app.cash.burst")
}

kotlin {
  jvm()

  sourceSets {
    commonTest {
      dependencies {
        implementation("app.cash.burst:burst:${project.property("burstVersion")}")
        implementation(kotlin("test"))
      }
    }
  }
}
