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
  androidTestImplementation(libs.kotlin.test.junit)
}
