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
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities.PROTECTED
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities.PUBLIC
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.addFakeOverrides
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.Name

/**
 * Given a test class with a single constructor that has parameters:
 *
 * ```
 * @Burst
 * class CoffeeTest(
 *   private val espresso: Espresso = Espresso.Regular,
 *   private val dairy: Dairy = Dairy.Milk,
 * ) {
 *   ...
 * }
 * ```
 *
 * This opens the class, makes that constructor protected, and removes the default arguments.
 *
 * If there's a default specialization, it adds a no-args constructor that calls it:
 *
 * ```
 * @Burst
 * open class CoffeeTest protected constructor(
 *   private val espresso: Espresso,
 *   private val dairy: Dairy,
 * ) {
 *   constructor() : this(Espresso.Regular, Dairy.Milk)
 *   ...
 * }
 * ```
 *
 * If there is no default specialization this makes the test class abstract.
 *
 * And it generates a new test class for each non-default specialization.
 *
 * ```
 * class CoffeeTest_Decaf_None : CoffeeTest(Espresso.Decaf, Dairy.None)
 * class CoffeeTest_Decaf_Milk : CoffeeTest(Espresso.Decaf, Dairy.Milk)
 * class CoffeeTest_Decaf_Oat : CoffeeTest(Espresso.Decaf, Dairy.Oat)
 * class CoffeeTest_Regular_None : CoffeeTest(Espresso.Regular, Dairy.None)
 * class CoffeeTest_Regular_Oat : CoffeeTest(Espresso.Regular, Dairy.Oat)
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

    val valueParameters = onlyConstructor.valueParameters()
    if (valueParameters.isEmpty()) return // Nothing to do.

    val specializations = specializations(pluginContext, burstApis, valueParameters)
    val indexOfDefaultSpecialization = when {
      onlyConstructor.defaultSpecializationIsBroken() -> -1
      else -> specializations.indexOfFirst { it.isDefault }
    }

    // Make sure the constructor we're using is accessible. Drop the default arguments to prevent
    // JUnit from using it.
    onlyConstructor.visibility = PROTECTED
    for (valueParameter in valueParameters) {
      valueParameter.defaultValue = null
    }

    if (indexOfDefaultSpecialization != -1) {
      original.modality = Modality.OPEN

      // Add a no-args constructor that calls the only constructor as the default specialization.
      createNoArgsConstructor(
        superConstructor = onlyConstructor,
        specialization = specializations[indexOfDefaultSpecialization],
      )

      // Make the primary constructor synthetic so that runners like JUnit5 ignore it.
      onlyConstructor.origin = IrDeclarationOriginImpl("SYNTHETIC_CONSTRUCTOR", isSynthetic = true)
    } else {
      // There's no default specialization. Make the class abstract so JUnit skips it.
      original.modality = Modality.ABSTRACT
    }

    // Add a subclass for each specialization.
    for ((index, specialization) in specializations.withIndex()) {
      // Don't generate code for the default specialization; we only want to run it once.
      if (index == indexOfDefaultSpecialization) continue

      createSubclass(
        superConstructor = onlyConstructor,
        specialization = specialization,
      )
    }
  }

  private fun createSubclass(
    superConstructor: IrConstructor,
    specialization: Specialization,
  ) {
    val created = original.factory.buildClass {
      initDefaults(original)
      visibility = PUBLIC
      name = Name.identifier("${original.name.identifier}_${specialization.name}")
    }.apply {
      superTypes = listOf(original.defaultType)
      createThisReceiverParameter()
    }

    created.addConstructor {
      initDefaults(original)
    }.apply {
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = superConstructor.symbol,
        ) {
          arguments.clear()
          for (argument in specialization.arguments) {
            arguments += argument.expression()
          }
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = created.symbol,
        )
      }
    }

    originalParent.addDeclaration(created)
    created.addFakeOverrides(irTypeSystemContext)
  }

  private fun createNoArgsConstructor(
    superConstructor: IrConstructor,
    specialization: Specialization,
  ) {
    val constructor = original.addConstructor {
      initDefaults(original)
      isPrimary = false
    }.apply {
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = superConstructor.symbol,
        ) {
          arguments.clear()
          for (argument in specialization.arguments) {
            arguments += argument.expression()
          }
        }
      }
    }
    pluginContext.metadataDeclarationRegistrar.registerConstructorAsMetadataVisible(constructor)
  }

  /**
   * Returns true if the default specialization would be broken for this constructor.
   *
   * We don't support the default specialization if any constructor parameter is a JVM value class.
   * For each constructor that declares a value class parameter, the Kotlin compiler generates an
   * extra (not-user-visible) constructor. But JUnit requires each test class to have exactly one
   * public constructor.
   *
   * https://github.com/cashapp/burst/issues/93
   */
  private fun IrConstructor.defaultSpecializationIsBroken(): Boolean {
    return valueParameters().any { it.type.getClass()?.hasAtJvmInline == true }
  }
}
