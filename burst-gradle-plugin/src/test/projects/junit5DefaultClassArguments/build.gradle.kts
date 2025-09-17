import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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
    classpath(libs.kotlin.gradlePlugin)
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

  tasks.withType(JavaCompile::class.java).configureEach {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
  }

  tasks.withType(KotlinJvmCompile::class.java).configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_1_8)
    }
  }

  tasks.withType<Test> {
    useJUnitPlatform()
  }
}
