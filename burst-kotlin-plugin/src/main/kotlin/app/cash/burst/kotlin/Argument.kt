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
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.isEnumClass

internal class Argument(
  private val original: IrElement,
  private val type: IrType,
  /** True if this argument matches the default parameter value. */
  internal val isDefault: Boolean,
  internal val value: IrEnumEntry,
) {
  /** Returns an expression that looks up this argument. */
  fun get(): IrExpression {
    return IrGetEnumValueImpl(original.startOffset, original.endOffset, type, value.symbol)
  }
}

/** Returns a name like `orderCoffee_Decaf_Oat` with each argument value inline. */
internal fun name(
  prefix: String,
  arguments: List<Argument>,
): String {
  return arguments.joinToString(
    prefix = prefix,
    separator = "_",
  ) { argument ->
    argument.value.name.identifier
  }
}

/** Returns null if we can't compute all possible arguments for this parameter. */
internal fun IrPluginContext.allPossibleArguments(
  parameter: IrValueParameter,
): List<Argument>? {
  val classId = parameter.type.getClass()?.classId ?: return null
  val referenceClass = referenceClass(classId)?.owner ?: return null
  if (!referenceClass.isEnumClass) return null
  val enumEntries = referenceClass.declarations.filterIsInstance<IrEnumEntry>()
  val defaultValueSymbol = (parameter.defaultValue?.expression as? IrGetEnumValue)?.symbol
  return enumEntries.map {
    Argument(
      original = parameter,
      type = parameter.type,
      isDefault = it.symbol == defaultValueSymbol,
      value = it,
    )
  }
}
