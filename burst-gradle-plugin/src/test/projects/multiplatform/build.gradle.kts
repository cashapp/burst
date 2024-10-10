buildscript {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
    google()
  }
  dependencies {
    classpath("app.cash.burst:burst-gradle-plugin:${project.property("burstVersion")}")
    classpath(libs.kotlin.gradle.plugin)
  }
}

allprojects {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
    google()
  }
}
