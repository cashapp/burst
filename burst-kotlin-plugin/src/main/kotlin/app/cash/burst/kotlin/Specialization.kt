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
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI

internal class Specialization(
  /** The argument values for this specialization. */
  val arguments: List<Argument>,

  /** A string like `Decaf_Oat` with each argument value named. */
  val name: String,
) {
  val isDefault: Boolean = arguments.all { it.isDefault }
}

@UnsafeDuringIrConstructionAPI
internal fun specializations(
  pluginContext: IrPluginContext,
  burstApis: BurstApis,
  parameters: List<IrValueParameter>,
): List<Specialization> {
  val parameterArguments = parameters.map { parameter ->
    pluginContext.allPossibleArguments(parameter, burstApis)
  }

  val specializations = parameterArguments.cartesianProduct().map { arguments ->
    Specialization(
      arguments = arguments,
      name = arguments.joinToString(separator = "_", transform = Argument::name),
    )
  }

  // If all elements already have distinct names, we're done.
  if (specializations.distinctBy { it.name }.size == specializations.size) {
    return specializations
  }

  // Otherwise, prefix each with its index.
  return specializations.mapIndexed { index, specialization ->
    Specialization(specialization.arguments, "${index}_${specialization.name}")
  }
}
