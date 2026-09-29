plugins {
  id("com.android.library")
  id("app.cash.burst")
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    minSdk = 21
  }
}

dependencies {
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.robolectric)
  // https://github.com/robolectric/robolectric/issues/11204
  testRuntimeOnly("org.ow2.asm:asm:9.9.1")
}
