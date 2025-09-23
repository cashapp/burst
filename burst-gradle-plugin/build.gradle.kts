import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  id("java-gradle-plugin")
  `kotlin-dsl`
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  compileOnly(libs.kotlin.gradlePlugin)
  testImplementation(kotlin("compiler-embeddable"))
  testImplementation(libs.assertk)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.metadata.klib)
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
  }

  val compilerPlugin = projects.burstKotlinPlugin
  packageName("app.cash.burst.gradle")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${libs.plugins.burst.kotlin.get()}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${compilerPlugin.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${compilerPlugin.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${compilerPlugin.version}\"")
  buildConfigField("String", "burstVersion", "\"${project.version}\"")
}

gradlePlugin {
  plugins {
    create("burst") {
      id = "app.cash.burst"
      displayName = "burst"
      description = "Compiler plugin to transform tests"
      implementationClass = "app.cash.burst.gradle.BurstPlugin"
    }
  }
}

tasks {
  test {
    systemProperty("burstVersion", project.version)
    dependsOn(":burst-gradle-plugin:publishAllPublicationsToTestMavenRepository")
    dependsOn(":burst-kotlin-plugin:publishAllPublicationsToTestMavenRepository")

    // Depend on the host platforms exercised by BurstGradlePluginTest.
    dependsOn(":burst:publishJsPublicationToTestMavenRepository")
    dependsOn(":burst:publishJvmPublicationToTestMavenRepository")
    dependsOn(":burst:publishKotlinMultiplatformPublicationToTestMavenRepository")
    dependsOn(":burst:publishLinuxX64PublicationToTestMavenRepository")
    dependsOn(":burst:publishMacosArm64PublicationToTestMavenRepository")
    dependsOn(":burst-coroutines:publishJsPublicationToTestMavenRepository")
    dependsOn(":burst-coroutines:publishJvmPublicationToTestMavenRepository")
    dependsOn(":burst-coroutines:publishKotlinMultiplatformPublicationToTestMavenRepository")
    dependsOn(":burst-coroutines:publishLinuxX64PublicationToTestMavenRepository")
    dependsOn(":burst-coroutines:publishMacosArm64PublicationToTestMavenRepository")
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    GradlePlugin(
      javadocJar = JavadocJar.Empty()
    )
  )
}
