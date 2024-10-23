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
package app.cash.burst.kotlin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.util.hasAnnotation

/** Looks up APIs used by the code rewriters. */
internal class BurstApis private constructor(
  private val pluginContext: IrPluginContext,
  private val testPackage: FqPackageName,
) {
  companion object {
    fun maybeCreate(pluginContext: IrPluginContext): BurstApis? {
      // If we don't have @Burst, we don't have the runtime. Abort!
      if (pluginContext.referenceClass(burstAnnotationClassId) == null) {
        return null
      }

      if (pluginContext.referenceClass(junitTestClassId) != null) {
        return BurstApis(pluginContext, junitPackage)
      }

      if (pluginContext.referenceClass(kotlinTestClassId) != null) {
        return BurstApis(pluginContext, kotlinTestPackage)
      }

      // No kotlin.test and no org.junit. No Burst for you.
      return null
    }
  }

  val testClassSymbol: IrClassSymbol
    get() = pluginContext.referenceClass(testPackage.classId("Test"))!!
}

private val burstFqPackage = FqPackageName("app.cash.burst")
private val burstAnnotationClassId = burstFqPackage.classId("Burst")

val junitPackage = FqPackageName("org.junit")
val junitTestClassId = junitPackage.classId("Test")
val kotlinTestPackage = FqPackageName("kotlin.test")
val kotlinTestClassId = kotlinTestPackage.classId("Test")

internal val IrAnnotationContainer.hasAtTest: Boolean
  get() = hasAnnotation(junitTestClassId) || hasAnnotation(kotlinTestClassId)

internal val IrAnnotationContainer.hasAtBurst: Boolean
  get() = hasAnnotation(burstAnnotationClassId)
