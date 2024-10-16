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
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.util.addFakeOverrides
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.Name

/**
 * Given a test class with a single constructor that has parameters:
 *
 * ```
 * @Burst
 * class CoffeeTest(
 *   private val espresso: Espresso,
 *   private val dairy: Dairy,
 * ) {
 *   ...
 * }
 * ```
 *
 * This opens the class, makes that constructor protected, and adds a default constructor that calls
 * the first specialization:
 *
 * ```
 * @Burst
 * open class CoffeeTest protected constructor(
 *   private val espresso: Espresso,
 *   private val dairy: Dairy,
 * ) {
 *   constructor() : this(Espresso.Decaf, Dairy.None)
 *   ...
 * }
 * ```
 *
 * And it generates a new test class for each specialization. The default specialization is also
 * annotated `@Ignore`.
 *
 * ```
 * @Ignore class CoffeeTest_Decaf_None : CoffeeTest(Espresso.Decaf, Dairy.None)
 * class CoffeeTest_Decaf_Milk : CoffeeTest(Espresso.Decaf, Dairy.Milk)
 * class CoffeeTest_Decaf_Oat : CoffeeTest(Espresso.Decaf, Dairy.Oat)
 * class CoffeeTest_Regular_None : CoffeeTest(Espresso.Regular, Dairy.None)
 * ...
 * ```
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class ClassSpecializer(
  private val pluginContext: IrPluginContext,
  private val burstApis: BurstApis,
  private val originalParent: IrFile,
  private val original: IrClass,
) {
  private val irTypeSystemContext = IrTypeSystemContextImpl(pluginContext.irBuiltIns)

  fun generateSpecializations() {
    val onlyConstructor = original.constructors.singleOrNull()
      ?: return // We only know how to handle a single constructor.

    val valueParameters = onlyConstructor.valueParameters
    if (valueParameters.isEmpty()) return // Nothing to do.

    val parameterArguments = valueParameters.map { parameter ->
      pluginContext.allPossibleArguments(parameter)
        ?: throw BurstCompilationException("Expected an enum for @Burst test parameter", parameter)
    }

    val cartesianProduct = parameterArguments.cartesianProduct()
    val defaultSpecialization = cartesianProduct.first()

    // Add @Ignore and open the class
    // TODO: don't double-add @Ignore
    original.modality = Modality.OPEN
    onlyConstructor.visibility = DescriptorVisibilities.PROTECTED

    // Add a no-args constructor that calls the only constructor as the default specialization.
    createNoArgsConstructor(
      superConstructor = onlyConstructor,
      arguments = defaultSpecialization,
    )

    // Add a subclass for each specialization.
    cartesianProduct.map { arguments ->
      createSpecialization(
        superConstructor = onlyConstructor,
        arguments = arguments,
        isDefaultSpecialization = arguments == defaultSpecialization,
      )
    }
  }

  private fun createSpecialization(
    superConstructor: IrConstructor,
    arguments: List<Argument>,
    isDefaultSpecialization: Boolean,
  ) {
    val specialization = original.factory.buildClass {
      initDefaults(original)
      name = Name.identifier(name("${original.name.identifier}_", arguments))
    }.apply {
      superTypes = listOf(original.defaultType)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    if (isDefaultSpecialization) {
      specialization.annotations += burstApis.ignoreClassSymbol.asAnnotation()
    }

    specialization.addConstructor {
      initDefaults(original)
    }.apply {
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = superConstructor.symbol,
          valueArgumentsCount = arguments.size,
        ) {
          for ((index, argument) in arguments.withIndex()) {
            putValueArgument(index, argument.get())
          }
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = specialization.symbol,
        )
      }
    }

    originalParent.addDeclaration(specialization)
    specialization.addFakeOverrides(irTypeSystemContext)
  }

  private fun createNoArgsConstructor(
    superConstructor: IrConstructor,
    arguments: List<Argument>,
  ) {
    original.addConstructor {
      initDefaults(original)
    }.apply {
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = superConstructor.symbol,
          valueArgumentsCount = arguments.size,
        ) {
          for ((index, argument) in arguments.withIndex()) {
            putValueArgument(index, argument.get())
          }
        }
      }
    }
  }
}
