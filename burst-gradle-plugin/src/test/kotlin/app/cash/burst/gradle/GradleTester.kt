/*
 * Copyright (C) 2024 Cash App
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.burst.gradle

import assertk.assertThat
import assertk.assertions.isIn
import java.io.File
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class GradleTester(
  projectName: String,
) {
  private val projectDir = File("src/test/projects/$projectName")

  fun createRunner(vararg taskNames: String): GradleRunner {
    val gradleRoot = projectDir.resolve("gradle").also { it.mkdir() }
    File("../gradle/wrapper").copyRecursively(gradleRoot.resolve("wrapper"), true)
    File(projectDir, "kotlin-js-store/yarn.lock").delete()
    val arguments = arrayOf("--info", "--stacktrace", "--continue")
    return GradleRunner.create()
      .withProjectDir(projectDir)
      .withDebug(true) // Run in-process.
      .withArguments(*arguments, *taskNames, versionProperty)
      .forwardOutput()
  }

  fun cleanAndBuild(vararg taskNames: String) {
    val result = createRunner("clean", *taskNames).build()
    for (taskName in taskNames) {
      assertThat(result.task(taskName)!!.outcome).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
    }
  }

  fun cleanAndBuildAndFail(taskName: String): BuildTask {
    val result = createRunner("clean", taskName).buildAndFail()
    return result.task(taskName)!!
  }

  fun readTestSuite(
    className: String,
    testTaskName: String = "test",
  ): TestSuite = readTestSuite(testSuitePath(className, testTaskName))

  fun hasTestSuite(
    className: String,
    testTaskName: String = "test",
  ) = testSuitePath(className, testTaskName).exists()

  private fun testSuitePath(
    className: String,
    testTaskName: String,
  ) = projectDir.resolve("lib/build/test-results/$testTaskName/TEST-$className.xml")

  private companion object {
    val versionProperty = "-PburstVersion=${System.getProperty("burstVersion")}"
  }
}
