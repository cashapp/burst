buildscript {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
  }
  dependencies {
    classpath("app.cash.burst:burst-gradle-plugin:${project.property("burstVersion")}")
  }
}

allprojects {
  tasks.withType(JavaCompile::class.java).configureEach {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
  }
}
