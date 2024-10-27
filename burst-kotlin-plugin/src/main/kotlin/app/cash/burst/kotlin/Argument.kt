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
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.isEnumClass

internal sealed interface Argument {
  /** True if this argument matches the default parameter value. */
  val isDefault: Boolean

  /** A string that's safe to use in a declaration name. */
  val name: String

  /** Returns an expression that looks up this argument. */
  fun expression(): IrExpression
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
}

private class BurstValuesArgument(
  override val isDefault: Boolean,
  private val value: IrExpression,
  private val index: Int,
) : Argument {
  override val name: String get() {
    return when {
      value is IrConst<*> -> value.value.toString()
      else -> index.toString()
    }
  }

  override fun expression() = value.deepCopyWithSymbols()
}

/** Returns a name like `orderCoffee_Decaf_Oat` with each argument value inline. */
internal fun name(
  prefix: String,
  arguments: List<Argument>,
): String = arguments.joinToString(prefix = prefix, separator = "_", transform = Argument::name)

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
internal fun IrPluginContext.allPossibleArguments(
  parameter: IrValueParameter,
  burstApis: BurstApis,
): List<Argument> {
  val burstApisCall = parameter.defaultValue?.expression as? IrCall
  if (burstApisCall?.symbol == burstApis.burstValues) {
    return buildList {
      val defaultArgument = burstApisCall.valueArguments[0]
      add(
        BurstValuesArgument(
          isDefault = true,
          value = defaultArgument ?: unexpectedParameter(parameter),
          index = 0,
        ),
      )

      for ((index, element) in (burstApisCall.valueArguments[1] as IrVararg).elements.withIndex()) {
        add(
          BurstValuesArgument(
            isDefault = false,
            value = element as? IrExpression ?: unexpectedParameter(parameter),
            index = index + 1,
          ),
        )
      }
    }
  }

  val classId = parameter.type.getClass()?.classId ?: unexpectedParameter(parameter)
  val referenceClass = referenceClass(classId)?.owner ?: unexpectedParameter(parameter)
  if (referenceClass.isEnumClass) {
    val enumEntries = referenceClass.declarations.filterIsInstance<IrEnumEntry>()
    val defaultValueSymbol = parameter.defaultValue?.let { defaultValue ->
      val expression = defaultValue.expression
      if (expression !is IrGetEnumValue) {
        throw BurstCompilationException(
          "@Burst default parameter must be an enum constant (or absent)",
          parameter,
        )
      }
      expression.symbol
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

  unexpectedParameter(parameter)
}

private fun unexpectedParameter(parameter: IrValueParameter): Nothing {
  throw BurstCompilationException(
    "Expected an enum or burstValues() for @Burst test parameter",
    parameter,
  )
}
