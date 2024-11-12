plugins {
  kotlin("jvm")
  id("app.cash.burst")
}

dependencies {
  testImplementation(kotlin("test"))
  testImplementation(libs.junit)
}
