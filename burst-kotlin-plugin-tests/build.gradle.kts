plugins {
  kotlin("jvm")
  id("org.jetbrains.dokka")
}

dependencies {
  testImplementation(kotlin("compiler-embeddable"))
  testImplementation(kotlin("test-junit"))
  testImplementation(libs.assertk)
  testImplementation(libs.kotlin.compile.testing)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(projects.burst)
  testImplementation(projects.burstCoroutines)
  testImplementation(projects.burstKotlinPlugin)
}
