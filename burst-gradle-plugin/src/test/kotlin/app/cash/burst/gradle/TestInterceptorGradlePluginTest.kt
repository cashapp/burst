/*
 * Copyright (C) 2025 Cash App
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
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test

class TestInterceptorGradlePluginTest {
  @Test
  fun interceptor() {
    val tester = GradleTester("interceptor")

    val result = tester.cleanAndBuildAndFail(":lib:test")
    assertThat(result.outcome).isEqualTo(TaskOutcome.FAILED)

    val sampleTest = tester.readTestSuite("SampleTest")

    val failingTest = sampleTest.testCases.single { it.name == "failingTest" }
    assertThat(failingTest.failureMessage)
      .isNotNull()
      .contains("AssertionError: boom: During")

    val passingTest = sampleTest.testCases.single { it.name == "passingTest" }
    assertThat(passingTest.skipped).isFalse()

    assertThat(sampleTest.systemOut).isEqualTo(
      """
      |intercepting  SampleTest failingTest
      |set up During
      |tear down During
      |intercepting  SampleTest passingTest
      |set up During
      |running During
      |tear down During
      |
      """.trimMargin(),
    )
  }
}
