plugins {
  kotlin("jvm")
  id("app.cash.burst")
}

dependencies {
  implementation("app.cash.burst:burst:${project.property("burstVersion")}")
  implementation(kotlin("test"))
  implementation(kotlin("test-junit"))
}
