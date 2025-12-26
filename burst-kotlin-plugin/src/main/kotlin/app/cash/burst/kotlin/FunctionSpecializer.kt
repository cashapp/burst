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
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildReceiverParameter
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.name.Name

/**
 * Given a test function that has parameters:
 * ```
 * @Test
 * fun test(espresso: Espresso = Espresso.Regular, dairy: Dairy = Dairy.Milk) {
 *   ...
 * }
 * ```
 *
 * This drops `@Test` from that test.
 *
 * It generates a new function for each specialization. The default specialization gets the same
 * name as the original test.
 *
 * ```
 * @Test fun test_Decaf_None() { test(Espresso.Decaf, Dairy.None) }
 * @Test fun test_Decaf_Milk() { test(Espresso.Decaf, Dairy.Milk) }
 * @Test fun test_Decaf_Oat() { test(Espresso.Decaf, Dairy.Oat) }
 * @Test fun test_Regular_Oat() { test(Espresso.Regular, Dairy.Oat) }
 * @Test fun test() { test(Espresso.Regular, Dairy.Milk) }
 * @Test fun test_Regular_None() { test(Espresso.Regular, Dairy.None) }
 * ```
 *
 * This way, the default specialization is executed when you run the test in the IDE.
 *
 * Coroutines
 * ----------
 * If the original test uses `runTest()` for coroutines, we copy it into a new function with these
 * transformations:
 * 1. A `TestScope` parameter is added.
 * 2. A `suspend` modifier is added.
 * 3. The `runTest()` call is removed.
 * 4. The `testBody` lambda is executed inline.
 *
 * For example, this test:
 * ```
 * @Test
 * fun test(espresso: Espresso, dairy: Dairy) = runTest {
 *   doTestThings(espresso, dairy)
 * }
 * ```
 *
 * Is transformed into this:
 * ```
 * suspend fun test(espresso: Espresso, dairy: Dairy, testScope: TestScope) {
 *   val testBody: suspend TestScope.() -> Unit = {
 *     doTestThings(espresso, dairy)
 *   }
 *   testScope.testBody()
 * }
 * ```
 *
 * Each generated specialization wraps the original call in `runTest()`.
 *
 * ```
 * @Test fun test_Decaf_None() = runTest {
 *   test(
 *     espresso = Espresso.Decaf,
 *     dairy = Dairy.None,
 *     testScope = this,
 *   )
 * }
 * ```
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class FunctionSpecializer(
  private val pluginContext: IrPluginContext,
  private val burstApis: BurstApis,
  private val originalParent: IrClass,
  private val original: TestFunction,
) {
  fun generateSpecializations() {
    val function = original.function
    val valueParameters = function.valueParameters()
    if (valueParameters.isEmpty()) return // Nothing to do.

    val originalDispatchReceiver =
      function.dispatchReceiverParameter
        ?: throw BurstCompilationException("Unexpected dispatch receiver", function)

    original.dropAtTest()

    // Skip overrides. Burst will specialize the overridden function, and that's sufficient! (And
    // we can't do anything here anyway, because overrides don't support default parameters.)
    if (function.overriddenSymbols.isNotEmpty()) {
      return
    }

    val delegate =
      when (original) {
        is TestFunction.Suspending -> {
          createSuspendingOverload(original).also { originalParent.addDeclaration(it) }
        }

        is TestFunction.NonSuspending -> original.function
      }

    val specializations = specializations(pluginContext, burstApis, valueParameters)
    val indexOfDefaultSpecialization = specializations.indexOfFirst { it.isDefault }

    val functions =
      specializations.mapIndexed { index, specialization ->
        createFunction(
          originalDispatchReceiver = originalDispatchReceiver,
          specialization = specialization,
          isDefaultSpecialization = index == indexOfDefaultSpecialization,
          delegate = delegate,
        )
      }

    // Add new declarations.
    for (function in functions) {
      originalParent.addDeclaration(function)
      pluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(function)
    }
  }

  /**
   * If the function body starts with `runTest()`, move its body to a new function that is
   * suspending and that accepts a `TestScope` parameter.
   */
  private fun createSuspendingOverload(original: TestFunction.Suspending): IrSimpleFunction {
    val result = original.function.deepCopyWithSymbols(originalParent)
    val runTestCall = TestFunctionReader(burstApis).readRunTestCall(result)!!

    result.isSuspend = true
    result.addValueParameter {
      initDefaults(result)
      name = Name.identifier("testScope")
      type = burstApis.testScope!!
    }

    result.irFunctionBody(context = pluginContext) {
      +irCall(callee = pluginContext.irBuiltIns.suspendFunctionN(1).symbol.functionByName("invoke"))
        .apply {
          arguments[0] = runTestCall.arguments[2]
          arguments[1] = irGet(result.parameters.last())
          type = pluginContext.irBuiltIns.unitType
        }
    }
    result.returnType = pluginContext.irBuiltIns.unitType

    result.patchDeclarationParents()
    return result
  }

  private fun createFunction(
    originalDispatchReceiver: IrValueParameter,
    specialization: Specialization,
    isDefaultSpecialization: Boolean,
    delegate: IrSimpleFunction,
  ): IrSimpleFunction {
    val result =
      pluginContext.irFactory
        .buildFun {
          initDefaults(delegate)
          modality = Modality.FINAL
          name =
            when {
              isDefaultSpecialization -> delegate.name
              else -> Name.identifier("${delegate.name.identifier}_${specialization.name}")
            }
          returnType =
            when {
              original is TestFunction.Suspending -> burstApis.runTestSymbol!!.owner.returnType
              else -> delegate.returnType
            }
        }
        .apply {
          parameters += buildReceiverParameter {
            initDefaults(originalDispatchReceiver)
            type = originalDispatchReceiver.type
          }
        }

    result.annotations += original.testAnnotation.asAnnotation()

    result.irFunctionBody(context = pluginContext) {
      val receiverLocal =
        irTemporary(
            value = irGet(result.dispatchReceiverParameter!!),
            nameHint = "receiver",
            isMutable = false,
          )
          .apply { origin = IrDeclarationOrigin.DEFINED }

      val argumentLocals =
        specialization.arguments.map { argument ->
          irTemporary(value = argument.expression(), nameHint = argument.name, isMutable = false)
            .apply { origin = IrDeclarationOrigin.DEFINED }
        }

      val callDelegate =
        irCall(callee = delegate.symbol).apply {
          arguments.clear()
          arguments += irGet(receiverLocal)
          for (argumentLocal in argumentLocals) {
            arguments += irGet(argumentLocal)
          }
        }

      when (original) {
        // Call runTest() with the original's runTest() arguments. The test body calls the delegate.
        is TestFunction.Suspending -> {
          +irReturn(
              irCall(callee = burstApis.runTestSymbol!!).apply {
                arguments.clear()
                // TODO: patch these arguments with the specialized arguments.
                arguments += original.runTestCall.arguments[0]?.deepCopyWithSymbols(result)
                arguments += original.runTestCall.arguments[1]?.deepCopyWithSymbols(result)
                arguments +=
                  irTestBodyLambda(
                    context = pluginContext,
                    burstApis = burstApis,
                    original = originalParent,
                  ) { testScope ->
                    callDelegate.arguments += irGet(testScope)
                    +callDelegate
                  }
              }
            )
            .apply { type = burstApis.runTestSymbol.owner.returnType }
        }

        is TestFunction.NonSuspending -> {
          +callDelegate
        }
      }
    }

    result.patchDeclarationParents()
    return result
  }
}
