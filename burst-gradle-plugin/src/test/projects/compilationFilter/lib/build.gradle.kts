plugins {
  kotlin("jvm")
  id("app.cash.burst")
}

burst {
  compilationFilter.set { it.defaultSourceSet.name == "test" }
}

dependencies {
  implementation("app.cash.burst:burst:${project.property("burstVersion")}")
  implementation(kotlin("test"))
  implementation(kotlin("test-junit"))
}
