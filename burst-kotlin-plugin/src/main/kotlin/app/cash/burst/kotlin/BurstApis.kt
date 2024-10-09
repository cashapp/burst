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
) {
  companion object {
    fun maybeCreate(pluginContext: IrPluginContext): BurstApis? {
      if (pluginContext.referenceClass(burstAnnotationClassId) == null) {
        // If we don't have @Burst, we don't have the runtime. Abort!
        return null
      }
      return BurstApis(pluginContext)
    }
  }

  val burstAnnotationClassSymbol: IrClassSymbol
    get() = pluginContext.referenceClass(burstAnnotationClassId)!!
}

private val burstFqPackage = FqPackageName("app.cash.burst")
private val burstAnnotationClassId = burstFqPackage.classId("Burst")

private val kotlinTestFqPackage = FqPackageName("kotlin.test")
private val kotlinTestTestClassId = kotlinTestFqPackage.classId("Test")

private val orgJunitFqPackage = FqPackageName("org.junit")
private val orgJunitTestClassId = orgJunitFqPackage.classId("Test")

internal val IrAnnotationContainer.hasAtTest: Boolean
  get() = hasAnnotation(orgJunitTestClassId) || hasAnnotation(kotlinTestTestClassId)

internal val IrAnnotationContainer.hasAtBurst: Boolean
  get() = hasAnnotation(burstAnnotationClassId)
