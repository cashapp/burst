import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("binary-compatibility-validator")
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.burstKotlinPlugin)
  add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.burstKotlinPlugin)
}

kotlin {
  androidNativeArm32()
  androidNativeArm64()
  androidNativeX64()
  androidNativeX86()

  iosArm64()
  iosSimulatorArm64()
  iosX64()

  js().nodejs()

  jvm()

  linuxArm64()
  linuxX64()

  macosArm64()
  macosX64()

  mingwX64()

  tvosArm64()
  tvosSimulatorArm64()
  tvosX64()

  wasmJs().nodejs()
  wasmWasi().nodejs()

  watchosArm32()
  watchosArm64()
  watchosDeviceArm64()
  watchosSimulatorArm64()
  watchosX64()

  applyDefaultHierarchyTemplate()

  sourceSets {
    val commonTest by getting {
      dependencies {
        implementation(libs.assertk)
        implementation(kotlin("test"))
      }
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = JavadocJar.Empty())
  )
}
