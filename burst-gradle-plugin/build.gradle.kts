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
  implementation(kotlin("gradle-plugin-api"))
  implementation(projects.burst)
  implementation(projects.burstKotlinPlugin)
  implementation(libs.kotlin.gradle.plugin)
  testImplementation(libs.assertk)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test)
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
    dependsOn(":burst:publishJsPublicationToTestMavenRepository")
    dependsOn(":burst:publishJvmPublicationToTestMavenRepository")
    dependsOn(":burst:publishKotlinMultiplatformPublicationToTestMavenRepository")
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    GradlePlugin(
      javadocJar = JavadocJar.Empty()
    )
  )
}
