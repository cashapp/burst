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

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.functions

@UnsafeDuringIrConstructionAPI // To use IrDeclarationContainer.declarations.
class BurstIrGenerationExtension(
  private val messageCollector: MessageCollector,
) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    // Skip the rewrite if the Burst APIs aren't loaded. We don't expect to find @Burst anywhere.
    val burstApis = BurstApis.maybeCreate(pluginContext) ?: return

    val transformer = object : IrElementTransformerVoidWithContext() {
      override fun visitClassNew(declaration: IrClass): IrStatement {
        val classDeclaration = super.visitClassNew(declaration) as IrClass
        val classHasAtBurst = classDeclaration.hasAtBurst

        // Return early if there's no @Burst anywhere.
        if (!classHasAtBurst && classDeclaration.functions.none { it.hasAtBurst }) {
          return classDeclaration
        }

        if (classHasAtBurst && classDeclaration.modality != Modality.ABSTRACT) {
          try {
            ClassSpecializer(
              pluginContext = pluginContext,
              burstApis = burstApis,
              originalParent = currentFile,
              original = classDeclaration,
            ).generateSpecializations()
          } catch (e: BurstCompilationException) {
            messageCollector.report(e.severity, e.message, currentFile.locationOf(e.element))
          }
        }

        // Snapshot the original functions because the loop mutates them.
        val originalFunctions = classDeclaration.functions.toList()

        for (function in originalFunctions) {
          val testAnnotationClassSymbol = burstApis.findTestAnnotation(function) ?: continue
          if (!classHasAtBurst && !function.hasAtBurst) continue

          try {
            val specializer = FunctionSpecializer(
              pluginContext = pluginContext,
              burstApis = burstApis,
              originalParent = classDeclaration,
              original = function,
              testAnnotationClassSymbol = testAnnotationClassSymbol,
            )
            specializer.generateSpecializations()
          } catch (e: BurstCompilationException) {
            messageCollector.report(e.severity, e.message, currentFile.locationOf(e.element))
          }
        }

        return classDeclaration
      }
    }

    moduleFragment.transform(transformer, null)
  }
}
