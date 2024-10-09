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
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class BurstIrGenerationExtension(
  private val messageCollector: MessageCollector,
) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    // Compiler plugin targeting is coarse grained. If we are operating in a context without our
    // runtime APIs, simply no-op rather than crash. This is required to allow new targets we don't
    // support, or to allow processing only test source sets and not main, for example.
    val burstApis = BurstApis.maybeCreate(pluginContext) ?: return

    val transformer = object : IrElementTransformerVoidWithContext() {
      override fun visitClassNew(declaration: IrClass): IrStatement {
        messageCollector.report(CompilerMessageSeverity.WARNING, "discovered ${declaration.name.identifier}", currentFile.locationOf(declaration))

        val declaration = super.visitClassNew(declaration) as IrClass

//        val outboundServiceFunction = declaration.addFunction {
////          initDefaults(original)
//          name = Name.identifier("hello")
////          returnType = bridgedInterfaceT
//        }.apply {
//          addDispatchReceiver {
////            initDefaults(original)
////            type = declaration.symbol.defaultDispatchReceiver
//          }
////          addValueParameter {
////            initDefaults(original)
////            name = Name.identifier("callHandler")
////            type = ziplineApis.outboundCallHandler.defaultType
////          }
////          overriddenSymbols = listOf(ziplineApis.ziplineServiceAdapterOutboundService)
//        }
//        outboundServiceFunction.irFunctionBody(
//          context = pluginContext,
//          scopeOwnerSymbol = outboundServiceFunction.symbol,
//        ) {
////          +irReturn(
////            irCallConstructor(
////              callee = outboundServiceClass.constructors.single().symbol,
////              typeArguments = adapterClass.typeParameters.map { it.defaultType },
////            ).apply {
////              putValueArgument(0, irGet(outboundServiceFunction.valueParameters[0]))
////              type = bridgedInterfaceT
////            },
////          )
//        }
//        return outboundServiceFunction
//
        return declaration
      }
    }

    moduleFragment.transform(transformer, null)
  }
}
