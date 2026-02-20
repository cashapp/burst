plugins {
  kotlin("jvm")
}

val burstRuntimeClasspath: Configuration by configurations.creating

dependencies {
  testImplementation(kotlin("compiler"))
  testImplementation(kotlin("compiler-internal-test-framework"))
  testImplementation(kotlin("test-junit5"))
  testImplementation(libs.assertk)
  testImplementation(libs.kotlin.compile.testing)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(projects.burst)
  testImplementation(projects.burstCoroutines)
  testImplementation(projects.burstKotlinPlugin)

  burstRuntimeClasspath(projects.burst)
  burstRuntimeClasspath(projects.burstCoroutines)
  burstRuntimeClasspath(kotlin("test-junit5"))

  testRuntimeOnly(kotlin("reflect"))
  testRuntimeOnly(kotlin("test"))
  testRuntimeOnly(kotlin("script-runtime"))
  testRuntimeOnly(kotlin("annotations-jvm"))
}

tasks.register<JavaExec>("generateTests") {
  inputs
    .dir(layout.projectDirectory.dir("src/test/data"))
    .withPropertyName("testData")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  outputs.dir(layout.projectDirectory.dir("src/test/java")).withPropertyName("generatedTests")

  classpath = sourceSets.test.get().runtimeClasspath
  mainClass.set("app.cash.burst.kotlin.GenerateTestsKt")
  workingDir = rootDir
}

tasks.withType<Test> {
  dependsOn(burstRuntimeClasspath)

  inputs
    .dir(layout.projectDirectory.dir("src/test/data"))
    .withPropertyName("testData")
    .withPathSensitivity(PathSensitivity.RELATIVE)

  useJUnitPlatform()

  workingDir = rootDir

  systemProperty("burstRuntime.classpath", burstRuntimeClasspath.asPath)

  // Properties required to run the internal test framework.
  systemProperty("idea.ignore.disabled.plugins", "true")
  systemProperty("idea.home.path", rootDir)
}

