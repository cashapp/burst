plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
}

rootProject.name = "burst-root"

include(":burst")
include(":burst-gradle-plugin")
include(":burst-kotlin-plugin")
include(":burst-kotlin-plugin-tests")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
