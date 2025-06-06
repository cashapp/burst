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
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildReceiverParameter
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.name.Name

/**
 * Given a test function that has parameters:
 *
 * ```
 * @Test
 * fun test(espresso: Espresso = Espresso.Regular, dairy: Dairy = Dairy.Milk) {
 *   ...
 * }
 * ```
 *
 * This drops `@Test` from that test.
 *
 * It generates a new function for each specialization. The default specialization gets the same
 * name as the original test.
 *
 * ```
 * @Test fun test_Decaf_None() { test(Espresso.Decaf, Dairy.None) }
 * @Test fun test_Decaf_Milk() { test(Espresso.Decaf, Dairy.Milk) }
 * @Test fun test_Decaf_Oat() { test(Espresso.Decaf, Dairy.Oat) }
 * @Test fun test_Regular_Oat() { test(Espresso.Regular, Dairy.Oat) }
 * @Test fun test() { test(Espresso.Regular, Dairy.Milk) }
 * @Test fun test_Regular_None() { test(Espresso.Regular, Dairy.None) }
 * ```
 *
 * This way, the default specialization is executed when you run the test in the IDE.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class FunctionSpecializer(
  private val pluginContext: IrPluginContext,
  private val burstApis: BurstApis,
  private val originalParent: IrClass,
  private val original: IrSimpleFunction,
  private val testAnnotationClassSymbol: IrClassSymbol,
) {
  fun generateSpecializations() {
    val valueParameters = original.valueParameters()
    if (valueParameters.isEmpty()) return // Nothing to do.

    val originalDispatchReceiver = original.dispatchReceiverParameter
      ?: throw BurstCompilationException("Unexpected dispatch receiver", original)

    // Drop `@Test` from the original's annotations.
    original.annotations = original.annotations.filter {
      it.type.classOrNull != testAnnotationClassSymbol
    }

    // Skip overrides. Burst will specialize the overridden function, and that's sufficient! (And
    // we can't do anything here anyway, because overrides don't support default parameters.)
    if (original.overriddenSymbols.isNotEmpty()) {
      return
    }

    val specializations = specializations(pluginContext, burstApis, valueParameters)
    val indexOfDefaultSpecialization = specializations.indexOfFirst { it.isDefault }

    val functions = specializations.mapIndexed { index, specialization ->
      createFunction(
        originalDispatchReceiver = originalDispatchReceiver,
        specialization = specialization,
        isDefaultSpecialization = index == indexOfDefaultSpecialization,
      )
    }

    // Add new declarations.
    for (function in functions) {
      originalParent.addDeclaration(function)
      pluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(function)
    }
  }

  private fun createFunction(
    originalDispatchReceiver: IrValueParameter,
    specialization: Specialization,
    isDefaultSpecialization: Boolean,
  ): IrSimpleFunction {
    val result = original.factory.buildFun {
      initDefaults(original)
      name = when {
        isDefaultSpecialization -> original.name
        else -> Name.identifier("${original.name.identifier}_${specialization.name}")
      }
      returnType = original.returnType
    }.apply {
      parameters += buildReceiverParameter {
        initDefaults(originalDispatchReceiver)
        type = originalDispatchReceiver.type
      }
    }

    result.annotations += testAnnotationClassSymbol.asAnnotation()

    result.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = original.symbol,
    ) {
      val receiverLocal = irTemporary(
        value = irGet(result.dispatchReceiverParameter!!),
        nameHint = "receiver",
        isMutable = false,
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      +irCall(
        callee = original.symbol,
      ).apply {
        this.dispatchReceiver = irGet(receiverLocal)
        for (argument in specialization.arguments) {
          arguments[argument.indexInParameters] = argument.expression()
        }
      }
    }

    result.patchDeclarationParents()
    return result
  }
}
