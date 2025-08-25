plugins {
  kotlin("jvm")
  id("app.cash.burst")
}

dependencies {
  implementation(project(":testlib"))
  testImplementation(kotlin("test"))
}
