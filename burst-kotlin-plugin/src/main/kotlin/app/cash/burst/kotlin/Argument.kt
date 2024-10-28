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
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isEnumClass
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.NameUtils

internal sealed interface Argument {
  /** True if this argument matches the default parameter value. */
  val isDefault: Boolean

  /** A string that's safe to use in a declaration name. */
  val name: String

  /** Returns a new expression that looks up this argument. */
  fun expression(): IrExpression

  /** Visits this argument for validation. */
  fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R
}

private class EnumValueArgument(
  private val original: IrElement,
  private val type: IrType,
  override val isDefault: Boolean,
  private val value: IrEnumEntry,
) : Argument {
  override val name = value.name.identifier

  override fun expression() =
    IrGetEnumValueImpl(original.startOffset, original.endOffset, type, value.symbol)

  override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
    return original.accept(visitor, data)
  }
}

private class BooleanArgument(
  private val original: IrElement,
  private val booleanType: IrType,
  override val isDefault: Boolean,
  private val value: Boolean,
) : Argument {
  override val name = value.toString()

  override fun expression() =
    IrConstImpl.boolean(original.startOffset, original.endOffset, booleanType, value)

  override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
    return original.accept(visitor, data)
  }
}

private class BurstValuesArgument(
  private val declarationParent: IrDeclarationParent,
  override val isDefault: Boolean,
  override val name: String,
  val value: IrExpression,
) : Argument {
  override fun expression() = value.deepCopyWithSymbols(declarationParent)

  override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
    return value.accept(visitor, data)
  }
}

/**
 * Returns all arguments for [parameter].
 *
 * If the parameter's default value is an immediate call to `burstValues()`, this returns an
 * argument for each value.
 *
 * If the parameter's type is an enum, this returns each enum constant for that type.
 *
 * @throws BurstCompilationException if we can't compute all possible arguments for this parameter.
 */
@UnsafeDuringIrConstructionAPI
internal fun IrPluginContext.allPossibleArguments(
  parameter: IrValueParameter,
  burstApis: BurstApis,
): List<Argument> {
  val burstApisCall = parameter.defaultValue?.expression as? IrCall
  if (burstApisCall?.symbol == burstApis.burstValues) {
    return burstValuesArguments(parameter, burstApisCall)
  }

  val classId = parameter.type.getClass()?.classId ?: unexpectedParameter(parameter)
  val referenceClass = referenceClass(classId)?.owner ?: unexpectedParameter(parameter)
  when {
    referenceClass.isEnumClass -> return enumValueArguments(referenceClass, parameter)
    referenceClass.defaultType == irBuiltIns.booleanType -> return booleanArguments(parameter)
  }

  unexpectedParameter(parameter)
}

@UnsafeDuringIrConstructionAPI
private fun burstValuesArguments(
  parameter: IrValueParameter,
  burstApisCall: IrCall,
): List<Argument> {
  return buildList {
    val defaultExpression = burstApisCall.valueArguments[0] ?: unexpectedParameter(parameter)
    add(
      BurstValuesArgument(
        declarationParent = parameter.parent,
        isDefault = true,
        name = defaultExpression.suggestedName() ?: "0",
        value = defaultExpression,
      ),
    )

    for ((index, element) in (burstApisCall.valueArguments[1] as IrVararg).elements.withIndex()) {
      val varargExpression = element as? IrExpression ?: unexpectedParameter(parameter)
      add(
        BurstValuesArgument(
          declarationParent = parameter.parent,
          isDefault = false,
          name = varargExpression.suggestedName() ?: (index + 1).toString(),
          value = varargExpression,
        ),
      )
    }
  }
}

/**
 * Returns a short name for this expression appropriate for use in a generated symbol declaration.
 *
 * If this is a constant like 'hello' or '3.14', this returns the value as a string.
 *
 * If this is a call like `String.CASE_INSENSITIVE_ORDER` or `abs(-5)`, this returns the name of the
 * called symbol (`CASE_INSENSITIVE_ORDER` or `abs`), discarding the receiver, value parameters, and
 * type parameters.
 *
 * If this is a class reference like `String::class`, this returns the type's simple name.
 */
@UnsafeDuringIrConstructionAPI
private fun IrExpression.suggestedName(): String? {
  val raw = when (this) {
    is IrConst<*> -> value.toString()
    is IrCall -> {
      val target = (symbol.owner.correspondingPropertySymbol?.owner ?: symbol.owner)
      target.name.asString()
    }

    is IrClassReference -> classType.classFqName?.shortName()?.asString() ?: return null
    else -> return null
  }

  // Calling sanitizeAsJavaIdentifier is necessary but not sufficient. We assume further phases of
  // the compiler will make the returned name safe for the ultimate compilation target.
  return NameUtils.sanitizeAsJavaIdentifier(raw)
}

@UnsafeDuringIrConstructionAPI
private fun enumValueArguments(
  referenceClass: IrClass,
  parameter: IrValueParameter,
): List<EnumValueArgument> {
  val enumEntries = referenceClass.declarations.filterIsInstance<IrEnumEntry>()
  val defaultValueSymbol = parameter.defaultValue?.let { defaultValue ->
    (defaultValue.expression as? IrGetEnumValue)?.symbol ?: unexpectedDefaultValue(parameter)
  }

  return enumEntries.map {
    EnumValueArgument(
      original = parameter,
      type = parameter.type,
      isDefault = it.symbol == defaultValueSymbol,
      value = it,
    )
  }
}

private fun IrPluginContext.booleanArguments(
  parameter: IrValueParameter,
): List<BooleanArgument> {
  val defaultValue = parameter.defaultValue?.let { defaultValue ->
    (defaultValue.expression as? IrConst<*>)?.value ?: unexpectedDefaultValue(parameter)
  }

  return listOf(false, true).map {
    BooleanArgument(
      original = parameter,
      booleanType = irBuiltIns.booleanType,
      isDefault = defaultValue == it,
      value = it,
    )
  }
}

private fun unexpectedParameter(parameter: IrValueParameter): Nothing {
  throw BurstCompilationException(
    "@Burst parameter must be a boolean, enum, or have a burstValues() default value",
    parameter,
  )
}

private fun unexpectedDefaultValue(parameter: IrValueParameter): Nothing {
  throw BurstCompilationException(
    "@Burst parameter default value must be burstValues(), a constant, or absent",
    parameter,
  )
}
