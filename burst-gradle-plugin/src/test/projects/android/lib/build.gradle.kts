plugins {
  id("com.android.library")
  kotlin("android")
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
  testImplementation(libs.kotlin.test)
  androidTestImplementation(libs.kotlin.test)
}
