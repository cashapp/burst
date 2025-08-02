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
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.Name

/** Looks up APIs used by the code rewriters. */
internal class BurstApis private constructor(
  pluginContext: IrPluginContext,
  private val testClassSymbols: List<IrClassSymbol>,
  val beforeTestSymbols: List<IrClassSymbol>,
  val afterTestSymbols: List<IrClassSymbol>,
) {
  val burstValues: IrFunctionSymbol = pluginContext.referenceFunctions(burstValuesId).single()

  val testInterceptor: IrClassSymbol =
    pluginContext.referenceClass(testInterceptorClassId)!!

  val testInterceptorTest: IrClassSymbol =
    pluginContext.referenceClass(testInterceptorTestClassId)!!

  val testInterceptorIntercept: IrSimpleFunctionSymbol =
    pluginContext.referenceFunctions(testInterceptorInterceptId).single()

  val testInterceptorTestPackageName: IrPropertySymbol =
    pluginContext.referenceProperties(testInterceptorTestPackageNameId).single()

  val testInterceptorTestClassName: IrPropertySymbol =
    pluginContext.referenceProperties(testInterceptorTestClassNameId).single()

  val testInterceptorTestFunctionName: IrPropertySymbol =
    pluginContext.referenceProperties(testInterceptorTestFunctionNameId).single()

  val testInterceptorTestInvoke: IrSimpleFunctionSymbol =
    pluginContext.referenceFunctions(testInterceptorTestInvokeId).single()

  val throwableAddSuppressed: IrSimpleFunctionSymbol =
    pluginContext.referenceFunctions(throwableAddSuppressedId).single()

  fun findTestAnnotation(function: IrSimpleFunction): IrClassSymbol? {
    return function.annotations
      .mapNotNull { it.type.classOrNull }
      .firstOrNull { it in testClassSymbols }
  }

  fun findBeforeTestAnnotation(function: IrSimpleFunction): IrClassSymbol? {
    return function.annotations
      .mapNotNull { it.type.classOrNull }
      .firstOrNull { it in beforeTestSymbols }
  }

  fun findAfterTestAnnotation(function: IrSimpleFunction): IrClassSymbol? {
    return function.annotations
      .mapNotNull { it.type.classOrNull }
      .firstOrNull { it in afterTestSymbols }
  }

  companion object {
    fun maybeCreate(pluginContext: IrPluginContext): BurstApis? {
      // If we don't have @Burst, we don't have the runtime. Abort!
      if (pluginContext.referenceClass(burstAnnotationId) == null) {
        return null
      }

      val testClassSymbols = listOfNotNull(
        pluginContext.referenceClass(junit5TestClassId),
        pluginContext.referenceClass(junitTestClassId),
        pluginContext.referenceClass(kotlinTestClassId),
      )

      // No @Test annotations? No Burst.
      if (testClassSymbols.isEmpty()) {
        return null
      }

      val beforeTestSymbols = listOfNotNull(
        pluginContext.referenceClass(junitBeforeTestClassId),
        pluginContext.referenceClass(kotlinBeforeTestClassId),
      )

      val afterTestSymbols = listOfNotNull(
        pluginContext.referenceClass(junitAfterTestClassId),
        pluginContext.referenceClass(kotlinAfterTestClassId),
      )

      return BurstApis(
        pluginContext = pluginContext,
        testClassSymbols = testClassSymbols,
        beforeTestSymbols = beforeTestSymbols,
        afterTestSymbols = afterTestSymbols,
      )
    }
  }
}

private val kotlinPackage = FqPackageName("kotlin")
private val throwableAddSuppressedId = kotlinPackage.callableId("addSuppressed")

private val burstFqPackage = FqPackageName("app.cash.burst")
private val burstAnnotationId = burstFqPackage.classId("Burst")
private val burstValuesId = burstFqPackage.callableId("burstValues")

private val interceptTestAnnotationId = burstFqPackage.classId("InterceptTest")
private val testInterceptorClassId = burstFqPackage.classId("TestInterceptor")
private val testInterceptorTestClassId = testInterceptorClassId.createNestedClassId(Name.identifier("Test"))
private val testInterceptorInterceptId = testInterceptorClassId.callableId("intercept")
private val testInterceptorTestInvokeId = testInterceptorTestClassId.callableId("invoke")
private val testInterceptorTestPackageNameId = testInterceptorTestClassId.callableId("packageName")
private val testInterceptorTestClassNameId = testInterceptorTestClassId.callableId("className")
private val testInterceptorTestFunctionNameId = testInterceptorTestClassId.callableId("functionName")

private val junitPackage = FqPackageName("org.junit")
private val junitTestClassId = junitPackage.classId("Test")
private val junitBeforeTestClassId = junitPackage.classId("Before")
private val junitAfterTestClassId = junitPackage.classId("After")
private val junit5Package = FqPackageName("org.junit.jupiter.api")
private val junit5TestClassId = junit5Package.classId("Test")
private val kotlinTestPackage = FqPackageName("kotlin.test")
private val kotlinTestClassId = kotlinTestPackage.classId("Test")
private val kotlinBeforeTestClassId = kotlinTestPackage.classId("BeforeTest")
private val kotlinAfterTestClassId = kotlinTestPackage.classId("AfterTest")

private val kotlinJvmFqPackage = FqPackageName("kotlin.jvm")
private val jvmInlineAnnotationId = kotlinJvmFqPackage.classId("JvmInline")

internal val IrAnnotationContainer.hasAtBurst: Boolean
  get() = hasAnnotation(burstAnnotationId)

internal val IrAnnotationContainer.hasAtTestInterceptor: Boolean
  get() = hasAnnotation(interceptTestAnnotationId)

internal val IrAnnotationContainer.hasAtJvmInline: Boolean
  get() = hasAnnotation(jvmInlineAnnotationId)
