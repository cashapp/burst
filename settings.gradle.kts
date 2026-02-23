rootProject.name = "burst-root"

include(":burst")
include(":burst-coroutines")
include(":burst-gradle-plugin")
include(":burst-kotlin-plugin")
include(":burst-kotlin-plugin-tests")
include(":burst-kotlin-plugin-tests:helpers")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
