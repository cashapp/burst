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
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class BurstRewriter(
  private val messageCollector: MessageCollector,
  private val pluginContext: IrPluginContext,
  private val burstApis: BurstApis,
  private val file: IrFile,
  private val original: IrSimpleFunction,
) {
  /** Returns a list of additional declarations. */
  fun rewrite(): List<IrDeclaration> {
    val originalValueParameters = original.valueParameters
    if (originalValueParameters.isEmpty()) {
      return listOf()
    }

    val originalDispatchReceiver = original.dispatchReceiverParameter
    if (originalDispatchReceiver == null) {
      messageCollector.report(
        CompilerMessageSeverity.ERROR,
        "Unexpected dispatch receiver",
        file.locationOf(original),
      )
      return listOf()
    }

    val parameterArguments = mutableListOf<List<Argument>>()
    for (parameter in originalValueParameters) {
      val expanded = parameter.allPossibleArguments()
      if (expanded == null) {
        messageCollector.report(
          CompilerMessageSeverity.ERROR,
          "Expected an enum for @Burst test parameter",
          file.locationOf(parameter),
        )
        return listOf()
      }
      parameterArguments += expanded
    }

    val cartesianProduct = parameterArguments.cartesianProduct()

    val variants = cartesianProduct.map { variantArguments ->
      createVariant(originalDispatchReceiver, variantArguments)
    }

    // Side-effect: add `@Ignore`
    // TODO: if its' absent!
    original.annotations += burstApis.ignoreClassSymbol.asAnnotation()

    val result = mutableListOf<IrDeclaration>()
    result += createFunctionThatCallsAllVariants(originalDispatchReceiver, variants)
    result += variants
    return result
  }

  private fun createVariant(
    originalDispatchReceiver: IrValueParameter,
    arguments: List<Argument>,
  ): IrSimpleFunction {
    val result = original.factory.buildFun {
      initDefaults(original)
      name = Name.identifier(name(arguments))
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

  /** Creates a function with no arguments that calls each variant. */
  private fun createFunctionThatCallsAllVariants(
    originalDispatchReceiver: IrValueParameter,
    variants: List<IrSimpleFunction>,
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

      for (variant in variants) {
        +irCall(
          callee = variant.symbol,
        ).apply {
          this.dispatchReceiver = irGet(receiverLocal)
        }
      }
    }

    return result
  }

  private inner class Argument(
    val type: IrType,
    val value: IrEnumEntry,
  )

  /** Returns a name like `orderCoffee_Decaf_Oat` with each argument value inline. */
  private fun name(arguments: List<Argument>): String {
    return arguments.joinToString(
      prefix = "${original.name.identifier}_",
      separator = "_",
    ) { argument ->
      argument.value.name.identifier
    }
  }

  /** Returns an expression that looks up this argument. */
  private fun Argument.get(): IrExpression {
    return IrGetEnumValueImpl(original.startOffset, original.endOffset, type, value.symbol)
  }

  /** Returns null if we can't compute all possible arguments for this parameter. */
  private fun IrValueParameter.allPossibleArguments(): List<Argument>? {
    val classId = type.getClass()?.classId ?: return null
    val referenceClass = pluginContext.referenceClass(classId)?.owner ?: return null
    val enumEntries = referenceClass.declarations.filterIsInstance<IrEnumEntry>()
    return enumEntries.map { Argument(type, it) }
  }

  private fun IrClassSymbol.asAnnotation(): IrConstructorCall {
    return IrConstructorCallImpl.fromSymbolOwner(
      type = starProjectedType,
      constructorSymbol = constructors.single(),
    )
  }
}
