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
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.name.Name

internal class BurstRewriter(
  private val messageCollector: MessageCollector,
  private val pluginContext: IrPluginContext,
  private val burstApis: BurstApis,
  private val file: IrFile,
  private val original: IrSimpleFunction,
) {
  /** Returns a list of additional declarations. */
  fun rewrite(): List<IrDeclaration> {
    val originalDispatchReceiver = original.dispatchReceiverParameter
    if (originalDispatchReceiver == null) {
      messageCollector.report(
        CompilerMessageSeverity.ERROR,
        "Unexpected dispatch receiver",
        file.locationOf(original),
      )
      return listOf()
    }

    val expansion = original.factory.buildFun {
      initDefaults(original)
      name = Name.identifier("${original.name.identifier}_2")
    }.apply {
      addDispatchReceiver {
        initDefaults(originalDispatchReceiver)
        type = originalDispatchReceiver.type
      }
      returnType = original.returnType
//          addValueParameter {
//            initDefaults(original)
//            name = Name.identifier("callHandler")
//            type = ziplineApis.outboundCallHandler.defaultType
//          }
//          overriddenSymbols = listOf(ziplineApis.ziplineServiceAdapterOutboundService)
    }

    expansion.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = original.symbol,
    ) {
      val receiverLocal = irTemporary(
        value = irGet(expansion.dispatchReceiverParameter!!),
        nameHint = "receiver",
        isMutable = false,
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      +irReturn(
        irCall(
          callee = original.symbol,
        ).apply {
          this.dispatchReceiver = irGet(receiverLocal)

//              putValueArgument(0, irGet(outboundServiceFunction.valueParameters[0]))
//              type = bridgedInterfaceT
        },
      )
    }

    return listOf(expansion)
  }
}
