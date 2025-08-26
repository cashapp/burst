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

import org.jetbrains.kotlin.backend.common.ir.moveBodyTo
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irCatch
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.IrClassBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.parent
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrInstanceInitializerCall
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.Name

/** Thrown on invalid or unexpected input code. */
class BurstCompilationException(
  override val message: String,
  val element: IrElement,
  val severity: CompilerMessageSeverity = CompilerMessageSeverity.ERROR,
) : Exception(message)

/** Finds the line and column of [irElement] within this file. */
fun IrFile.locationOf(irElement: IrElement?): CompilerMessageSourceLocation {
  val sourceRangeInfo = fileEntry.getSourceRangeInfo(
    beginOffset = irElement?.startOffset ?: SYNTHETIC_OFFSET,
    endOffset = irElement?.endOffset ?: SYNTHETIC_OFFSET,
  )
  return CompilerMessageLocationWithRange.create(
    path = sourceRangeInfo.filePath,
    lineStart = sourceRangeInfo.startLineNumber + 1,
    columnStart = sourceRangeInfo.startColumnNumber + 1,
    lineEnd = sourceRangeInfo.endLineNumber + 1,
    columnEnd = sourceRangeInfo.endColumnNumber + 1,
    lineContent = null,
  )!!
}

/** Set up reasonable defaults for a generated function or constructor. */
fun IrFunctionBuilder.initDefaults(original: IrElement) {
  this.startOffset = original.startOffset.toSyntheticIfUnknown()
  this.endOffset = original.endOffset.toSyntheticIfUnknown()
  this.origin = IrDeclarationOrigin.DEFINED
  this.visibility = DescriptorVisibilities.PUBLIC
  this.modality = Modality.OPEN
  this.isPrimary = true
}

/** Set up reasonable defaults for a generated class. */
fun IrClassBuilder.initDefaults(original: IrElement) {
  this.startOffset = original.startOffset.toSyntheticIfUnknown()
  this.endOffset = original.endOffset.toSyntheticIfUnknown()
  this.name = Name.special("<no name provided>")
  this.visibility = DescriptorVisibilities.LOCAL
}

/** Set up reasonable defaults for a value parameter. */
fun IrValueParameterBuilder.initDefaults(original: IrElement) {
  this.startOffset = original.startOffset.toSyntheticIfUnknown()
  this.endOffset = original.endOffset.toSyntheticIfUnknown()
}

fun IrClassSymbol.asAnnotation(): IrConstructorCall {
  return IrConstructorCallImpl.fromSymbolOwner(
    type = starProjectedType,
    constructorSymbol = constructors.single(),
  )
}

/**
 * When we generate code based on classes outside of the current module unit we get elements that
 * use `UNDEFINED_OFFSET`. Make sure we don't propagate this further into generated code; that
 * causes LLVM code generation to fail.
 */
private fun Int.toSyntheticIfUnknown(): Int {
  return when (this) {
    UNDEFINED_OFFSET -> SYNTHETIC_OFFSET
    else -> this
  }
}

fun IrConstructor.irConstructorBody(
  context: IrGeneratorContext,
  blockBody: DeclarationIrBuilder.(MutableList<IrStatement>) -> Unit,
) {
  val constructorIrBuilder = DeclarationIrBuilder(
    generatorContext = context,
    symbol = IrSimpleFunctionSymbolImpl(),
    startOffset = startOffset,
    endOffset = endOffset,
  )
  body = context.irFactory.createBlockBody(
    startOffset = constructorIrBuilder.startOffset,
    endOffset = constructorIrBuilder.endOffset,
  ) {
    constructorIrBuilder.blockBody(statements)
  }
}

fun DeclarationIrBuilder.irDelegatingConstructorCall(
  context: IrGeneratorContext,
  symbol: IrConstructorSymbol,
  typeArgumentsCount: Int = 0,
  block: IrDelegatingConstructorCall.() -> Unit = {},
): IrDelegatingConstructorCall {
  val result = IrDelegatingConstructorCallImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = context.irBuiltIns.unitType,
    symbol = symbol,
    typeArgumentsCount = typeArgumentsCount,
  )
  result.block()
  return result
}

fun DeclarationIrBuilder.irInstanceInitializerCall(
  context: IrGeneratorContext,
  classSymbol: IrClassSymbol,
): IrInstanceInitializerCall {
  return IrInstanceInitializerCallImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    classSymbol = classSymbol,
    type = context.irBuiltIns.unitType,
  )
}

fun IrSimpleFunction.irFunctionBody(
  context: IrGeneratorContext,
  scopeOwnerSymbol: IrSymbol,
  blockBody: IrBlockBodyBuilder.() -> Unit,
) {
  val bodyBuilder = IrBlockBodyBuilder(
    startOffset = startOffset,
    endOffset = endOffset,
    context = context,
    scope = Scope(scopeOwnerSymbol),
  )
  body = bodyBuilder.blockBody {
    blockBody()
  }
}

fun IrBlockBodyBuilder.localLambda(
  scopeOwnerSymbol: IrSymbol,
  block: IrBlockBodyBuilder.() -> Unit,
): IrFunctionExpressionImpl {
  return IrFunctionExpressionImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = context.irBuiltIns.functionN(0).typeWith(context.irBuiltIns.unitType),
    function = context.irFactory.buildFun {
      this.name = Name.special("<anonymous>")
      this.returnType = context.irBuiltIns.unitType
      this.origin = IrDeclarationOrigin.Companion.LOCAL_FUNCTION_FOR_LAMBDA
      this.visibility = DescriptorVisibilities.LOCAL
    }.apply {
      irFunctionBody(
        context = context,
        scopeOwnerSymbol = scopeOwnerSymbol,
      ) {
        block()
      }
    },
    origin = IrStatementOrigin.Companion.LAMBDA,
  )
}

/** Moves the body of [original] to a newly-created local lambda. */
fun IrBlockBodyBuilder.moveBodyToLocalLambda(
  original: IrSimpleFunction,
): IrFunctionExpressionImpl {
  return IrFunctionExpressionImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = context.irBuiltIns.functionN(0).typeWith(context.irBuiltIns.unitType),
    function = context.irFactory.buildFun {
      this.name = Name.special("<anonymous>")
      this.returnType = context.irBuiltIns.unitType
      this.origin = IrDeclarationOrigin.Companion.LOCAL_FUNCTION_FOR_LAMBDA
      this.visibility = DescriptorVisibilities.LOCAL
    }.apply {
      body = original.moveBodyTo(this, mapOf())
    },
    origin = IrStatementOrigin.Companion.LAMBDA,
  )
}

internal fun IrBlockBuilder.irAccumulateFailure(
  burstApis: BurstApis,
  failure: IrVariable,
  tryBody: IrExpression,
): IrExpression {
  val e = buildVariable(
    parent = parent,
    startOffset = UNDEFINED_OFFSET,
    endOffset = UNDEFINED_OFFSET,
    origin = IrDeclarationOrigin.CATCH_PARAMETER,
    name = Name.identifier("e"),
    type = context.irBuiltIns.throwableType,
  )

  return irTry(
    type = context.irBuiltIns.anyNType,
    tryResult = tryBody,
    catches = listOf(
      irCatch(
        catchParameter = e,
        result = irIfNull(
          type = context.irBuiltIns.anyNType,
          subject = irGet(failure),
          thenPart = irSet(failure, irGet(e)),
          elsePart = irCall(burstApis.throwableAddSuppressed).apply {
            arguments[0] = irGet(failure)
            arguments[1] = irGet(e)
          },
        ),
      ),
    ),
    finallyExpression = null,
  )
}

@UnsafeDuringIrConstructionAPI
internal fun IrDeclarationContainer.addDeclaration(declaration: IrDeclaration) {
  declarations.add(declaration)
  declaration.parent = this
}

@UnsafeDuringIrConstructionAPI
internal fun IrFunction.valueParameters(): List<IrValueParameter> {
  return parameters
    .filter { it.kind == IrParameterKind.Regular }
}

@UnsafeDuringIrConstructionAPI
internal fun IrCall.valueArguments(): List<IrExpression?> {
  return symbol.owner.valueParameters()
    .map { arguments[it.indexInParameters] }
}
