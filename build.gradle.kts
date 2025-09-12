import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import java.net.URI
import java.net.URL
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
  }
  dependencies {
    classpath(libs.binary.compatibility.validator.gradle.plugin)
    classpath(libs.mavenPublish.gradle.plugin)
    classpath(libs.kotlin.gradlePlugin)
    classpath(libs.dokka.gradle.plugin)
    classpath(libs.google.ksp)
  }
}

plugins {
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.spotless)
}

apply(plugin = "org.jetbrains.dokka")

apply(plugin = "com.vanniktech.maven.publish.base")

configure<SpotlessExtension> {
  kotlin {
    target("**/*.kt")
    ktlint()
      .editorConfigOverride(
        mapOf(
          "ktlint_standard_filename" to "disabled",
        )
      )
  }
}

allprojects {
  group = "app.cash.burst"
  version = project.property("VERSION_NAME") as String

  repositories {
    mavenCentral()
    google()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
  }
}

subprojects {
  tasks.withType(Test::class).configureEach {
    testLogging {
      if (System.getenv("CI") == "true") {
        events = setOf(TestLogEvent.STARTED, TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.PASSED)
      }
      exceptionFormat = TestExceptionFormat.FULL
    }
  }
}

tasks.named("dokkaHtmlMultiModule", DokkaMultiModuleTask::class.java).configure {
  moduleName.set("Burst")
}

allprojects {
  tasks.withType<DokkaTaskPartial>().configureEach {
    dokkaSourceSets.configureEach {
      documentedVisibilities.set(setOf(
        Visibility.PUBLIC,
        Visibility.PROTECTED
      ))
      reportUndocumented.set(false)
      jdkVersion.set(8)

      perPackageOption {
        matchingRegex.set("app\\.cash\\.burst\\.internal\\..*")
        suppress.set(true)
      }
      sourceLink {
        localDirectory.set(rootProject.projectDir)
        remoteUrl.set(URL("https://github.com/cashapp/burst/tree/main/"))
        remoteLineSuffix.set("#L")
      }
    }
  }

  // Don't attempt to sign anything if we don't have an in-memory key. Otherwise, the 'build' task
  // triggers 'signJsPublication' even when we aren't publishing (and so don't have signing keys).
  tasks.withType<Sign>().configureEach {
    enabled = project.findProperty("signingInMemoryKey") != null
  }

  val javaVersion = JavaVersion.VERSION_1_8

  plugins.withId("org.jetbrains.kotlin.multiplatform") {
    val kotlin = extensions.getByName("kotlin") as KotlinMultiplatformExtension
    kotlin.targets.withType(KotlinJvmTarget::class.java) {
      compilerOptions {
        freeCompilerArgs.add("-Xjdk-release=$javaVersion")
      }
    }
  }

  tasks.withType(JavaCompile::class.java).configureEach {
    sourceCompatibility = javaVersion.toString()
    targetCompatibility = javaVersion.toString()
  }

  tasks.withType(KotlinJvmCompile::class.java).configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
    }
  }

  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<PublishingExtension> {
      repositories {
        maven {
          name = "testMaven"
          url = rootProject.layout.buildDirectory.dir("testMaven").get().asFile.toURI()
        }

        /*
         * Want to push to an internal repository for testing?
         * Set the following properties in ~/.gradle/gradle.properties.
         *
         * internalUrl=YOUR_INTERNAL_URL
         * internalUsername=YOUR_USERNAME
         * internalPassword=YOUR_PASSWORD
         *
         * Then run the following command to publish a new internal release:
         *
         * ./gradlew publishAllPublicationsToInternalRepository -DRELEASE_SIGNING_ENABLED=false
         */
        val internalUrl = providers.gradleProperty("internalUrl").orNull
        if (internalUrl != null) {
          maven {
            name = "internal"
            url = URI(internalUrl)
            credentials {
              username = providers.gradleProperty("internalUsername").get()
              password = providers.gradleProperty("internalPassword").get()
            }
          }
        }
      }
    }
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
      pom {
        description.set("Adds parameters to tests")
        name.set(project.name)
        url.set("https://github.com/cashapp/burst/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        developers {
          developer {
            id.set("cashapp")
            name.set("Cash App")
          }
        }
        scm {
          url.set("https://github.com/cashapp/burst/")
          connection.set("scm:git:https://github.com/cashapp/burst.git")
          developerConnection.set("scm:git:ssh://git@github.com/cashapp/burst.git")
        }
      }
    }
  }
}
