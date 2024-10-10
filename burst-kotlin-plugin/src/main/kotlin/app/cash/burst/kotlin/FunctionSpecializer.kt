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
import org.jetbrains.kotlin.backend.common.ir.addDispatchReceiver
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class FunctionSpecializer(
  private val pluginContext: IrPluginContext,
  private val burstApis: BurstApis,
  private val originalParent: IrClass,
  private val original: IrSimpleFunction,
) {
  fun generateSpecializations() {
    val originalValueParameters = original.valueParameters
    if (originalValueParameters.isEmpty()) return // Nothing to do.

    val originalDispatchReceiver = original.dispatchReceiverParameter
      ?: throw BurstCompilationException("Unexpected dispatch receiver", original)

    val parameterArguments = mutableListOf<List<Argument>>()
    for (parameter in originalValueParameters) {
      val expanded = pluginContext.allPossibleArguments(parameter)
        ?: throw BurstCompilationException("Expected an enum for @Burst test parameter", parameter)
      parameterArguments += expanded
    }

    val cartesianProduct = parameterArguments.cartesianProduct()

    val specializations = cartesianProduct.map { arguments ->
      createSpecialization(originalDispatchReceiver, arguments)
    }

    // Drop `@Test` from the original's annotations.
    original.annotations = original.annotations.filter {
      it.type.classFqName != burstApis.testClassSymbol.starProjectedType.classFqName
    }

    // Add new declarations.
    for (specialization in specializations) {
      originalParent.addDeclaration(specialization)
    }
    originalParent.addDeclaration(
      createFunctionThatCallsAllSpecializations(originalDispatchReceiver, specializations),
    )
  }

  private fun IrClass.addDeclaration(declaration: IrDeclaration) {
    declarations.add(declaration)
    declaration.parent = this
  }

  private fun createSpecialization(
    originalDispatchReceiver: IrValueParameter,
    arguments: List<Argument>,
  ): IrSimpleFunction {
    val result = original.factory.buildFun {
      initDefaults(original)
      name = Name.identifier(name("${original.name.identifier}_", arguments))
      returnType = original.returnType
    }.apply {
      addDispatchReceiver {
        initDefaults(originalDispatchReceiver)
        type = originalDispatchReceiver.type
      }
    }

    result.annotations += burstApis.testClassSymbol.asAnnotation()

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
        for ((index, argument) in arguments.withIndex()) {
          putValueArgument(index, argument.get())
        }
      }
    }

    return result
  }

  /** Creates an @Test @Ignore no-args function that calls each specialization. */
  private fun createFunctionThatCallsAllSpecializations(
    originalDispatchReceiver: IrValueParameter,
    specializations: List<IrSimpleFunction>,
  ): IrSimpleFunction {
    val result = original.factory.buildFun {
      initDefaults(original)
      name = original.name
      returnType = original.returnType
    }.apply {
      addDispatchReceiver {
        initDefaults(originalDispatchReceiver)
        type = originalDispatchReceiver.type
      }
    }

    result.annotations += burstApis.testClassSymbol.asAnnotation()
    result.annotations += burstApis.ignoreClassSymbol.asAnnotation()

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

      for (specialization in specializations) {
        +irCall(
          callee = specialization.symbol,
        ).apply {
          this.dispatchReceiver = irGet(receiverLocal)
        }
      }
    }

    return result
  }
}
