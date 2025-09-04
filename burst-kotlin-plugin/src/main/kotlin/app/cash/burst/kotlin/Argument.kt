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
import org.jetbrains.kotlin.ir.declarations.IrClass
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
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isEnumClass
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.jetbrains.kotlin.name.NameUtils

internal sealed interface Argument {
  /** True if this argument matches the default parameter value. */
  val isDefault: Boolean

  /** A string that's safe to use in a declaration name. */
  val name: String

  /** Returns a new expression that looks up this argument. */
  fun expression(): IrExpression

  /** Visits this argument for validation. */
  fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R
}

private class EnumValueArgument(
  private val original: IrValueParameter,
  private val type: IrType,
  override val isDefault: Boolean,
  private val value: IrEnumEntry,
) : Argument {
  override val name = value.name.identifier

  override fun expression() =
    IrGetEnumValueImpl(original.startOffset, original.endOffset, type, value.symbol)

  override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
    return original.accept(visitor, data)
  }
}

private class BooleanArgument(
  private val original: IrValueParameter,
  private val booleanType: IrType,
  override val isDefault: Boolean,
  private val value: Boolean,
) : Argument {
  override val name = value.toString()

  override fun expression() =
    IrConstImpl.boolean(original.startOffset, original.endOffset, booleanType, value)

  override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
    return original.accept(visitor, data)
  }
}

private class NullArgument(
  private val original: IrValueParameter,
  private val type: IrType,
  override val isDefault: Boolean,
) : Argument {
  override val name = "null"

  override fun expression() = IrConstImpl.constNull(original.startOffset, original.endOffset, type)

  override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
    return original.accept(visitor, data)
  }
}

@UnsafeDuringIrConstructionAPI
private class BurstValuesArgument(
  private val parameter: IrValueParameter,
  private val value: IrExpression,
  index: Int,
) : Argument {
  override val isDefault = index == 0
  override val name = value.suggestedName() ?: index.toString()

  override fun expression() = value.deepCopyWithSymbols(parameter.parent)

  override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
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
    val valueArguments = burstApisCall.valueArguments()
    add(
      BurstValuesArgument(
        parameter = parameter,
        value = valueArguments[0] ?: unexpectedParameter(parameter),
        index = size,
      ),
    )

    val varargs = valueArguments[1] as? IrVararg ?: return@buildList
    for (element in varargs.elements) {
      add(
        BurstValuesArgument(
          parameter = parameter,
          value = element as? IrExpression ?: unexpectedParameter(parameter),
          index = size,
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
    is IrConst -> value.toString()
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
): List<Argument> {
  val enumEntries = referenceClass.declarations.filterIsInstance<IrEnumEntry>()
  val hasDefaultValue = parameter.defaultValue != null
  val defaultEnumSymbolName = parameter.defaultValue?.let { defaultValue ->
    val expression = defaultValue.expression
    when {
      expression is IrGetEnumValue -> expression.symbol.owner.name
      expression is IrConst && expression.value == null -> null
      else -> unexpectedDefaultValue(parameter)
    }
  }

  return buildList {
    for (enumEntry in enumEntries) {
      add(
        EnumValueArgument(
          original = parameter,
          type = parameter.type,
          isDefault = hasDefaultValue && enumEntry.symbol.owner.name == defaultEnumSymbolName,
          value = enumEntry,
        ),
      )
    }
    if (parameter.type.isNullable()) {
      add(
        NullArgument(
          original = parameter,
          type = parameter.type,
          isDefault = hasDefaultValue && defaultEnumSymbolName == null,
        ),
      )
    }
  }
}

private fun IrPluginContext.booleanArguments(
  parameter: IrValueParameter,
): List<Argument> {
  val hasDefaultValue = parameter.defaultValue != null
  val defaultValue = parameter.defaultValue?.let { defaultValue ->
    val expression = defaultValue.expression
    when {
      expression is IrConst -> expression.value
      else -> unexpectedDefaultValue(parameter)
    }
  }

  return buildList {
    for (b in listOf(false, true)) {
      add(
        BooleanArgument(
          original = parameter,
          booleanType = irBuiltIns.booleanType,
          isDefault = hasDefaultValue && defaultValue == b,
          value = b,
        ),
      )
    }
    if (parameter.type.isNullable()) {
      add(
        NullArgument(
          original = parameter,
          type = parameter.type,
          isDefault = hasDefaultValue && defaultValue == null,
        ),
      )
    }
  }
}

private fun unexpectedParameter(parameter: IrValueParameter): Nothing {
  throw BurstCompilationException(
    "@Burst parameter must be a boolean, an enum, or have a burstValues() default value",
    parameter,
  )
}

private fun unexpectedDefaultValue(parameter: IrValueParameter): Nothing {
  throw BurstCompilationException(
    "@Burst parameter default must be burstValues(), a constant, null, or absent",
    parameter,
  )
}
