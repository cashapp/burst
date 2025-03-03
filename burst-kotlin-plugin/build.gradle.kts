import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp")
  id("com.github.gmazzo.buildconfig")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  compileOnly(kotlin("compiler-embeddable"))
  compileOnly(kotlin("stdlib"))

  ksp(libs.auto.service.compiler)
  compileOnly(libs.auto.service.annotations)
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
  }

  packageName("app.cash.burst.kotlin")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${libs.plugins.burst.kotlin.get()}\"")
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Empty()
    )
  )
}
