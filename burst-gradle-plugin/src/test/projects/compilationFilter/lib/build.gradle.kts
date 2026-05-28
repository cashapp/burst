import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

plugins {
  kotlin("jvm")
  id("app.cash.burst")
}

burst {
  compilationFilter.set { it.name == KotlinCompilation.TEST_COMPILATION_NAME }
}

dependencies {
  implementation("app.cash.burst:burst:${project.property("burstVersion")}")
  implementation(kotlin("test"))
  implementation(kotlin("test-junit"))
}
