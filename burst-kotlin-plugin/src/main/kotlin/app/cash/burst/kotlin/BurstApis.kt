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
@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package app.cash.burst.kotlin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.name.ClassId

/** Looks up APIs used by the code rewriters. */
internal class BurstApis private constructor(
  pluginContext: IrPluginContext,
  private val testClassSymbols: List<IrClassSymbol>,
  val beforeTestSymbols: List<IrClassSymbol>,
  val afterTestSymbols: List<IrClassSymbol>,
  /** Null if `kotlinx.coroutines.test` isn't in this build. */
  val runTestSymbol: IrFunctionSymbol?,
) {
  val burstValues: IrFunctionSymbol = pluginContext.referenceFunctions(burstValuesId).single()

  val testInterceptorApis: TestInterceptorApis = pluginContext.testInterceptorApis(
    testFunctionClassId = burstFqPackage.classId("TestFunction"),
    testInterceptorClassId = burstFqPackage.classId("TestInterceptor"),
  )!!

  /** Null if `app.cash.burst.coroutines` isn't in this build. */
  val coroutinesTestInterceptorApis: TestInterceptorApis? = pluginContext.testInterceptorApis(
    testFunctionClassId = burstCoroutinesFqPackage.classId("CoroutineTestFunction"),
    testInterceptorClassId = burstCoroutinesFqPackage.classId("CoroutineTestInterceptor"),
  )

  val throwableAddSuppressed: IrSimpleFunctionSymbol =
    pluginContext.referenceFunctions(throwableAddSuppressedId).single()

  /** Null if `kotlinx.coroutines.test` isn't in this build. */
  val testScope: IrType? = pluginContext.referenceClass(testScopeId)?.defaultType

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

  fun isRunTest(irCall: IrCall): Boolean {
    return runTestSymbol != null && irCall.symbol == runTestSymbol
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
        pluginContext.referenceClass(junit5BeforeEachClassId),
        pluginContext.referenceClass(junitBeforeTestClassId),
        pluginContext.referenceClass(kotlinBeforeTestClassId),
      )

      val afterTestSymbols = listOfNotNull(
        pluginContext.referenceClass(junit5AfterEachClassId),
        pluginContext.referenceClass(junitAfterTestClassId),
        pluginContext.referenceClass(kotlinAfterTestClassId),
      )

      val runTestSymbol = pluginContext.referenceFunctions(runTestId).singleOrNull {
        it.owner.parameters.size == 3 &&
          it.owner.parameters[0].type.classFqName == coroutineContextId.asSingleFqName() &&
          it.owner.parameters[1].type.classFqName == durationId.asSingleFqName()
      }

      return BurstApis(
        pluginContext = pluginContext,
        testClassSymbols = testClassSymbols,
        beforeTestSymbols = beforeTestSymbols,
        afterTestSymbols = afterTestSymbols,
        runTestSymbol = runTestSymbol,
      )
    }
  }
}

/** The interceptor APIs for either suspending or non-suspending calls. */
internal class TestInterceptorApis(
  val interceptor: IrClassSymbol,
  val function: IrClassSymbol,
  val intercept: IrSimpleFunctionSymbol,
  val testScope: IrPropertySymbol?,
  val packageName: IrPropertySymbol,
  val className: IrPropertySymbol,
  val functionName: IrPropertySymbol,
  val invoke: IrSimpleFunctionSymbol,
) {
  val interceptorType: IrType = interceptor.defaultType
  val functionType: IrType = function.defaultType

  fun isTestInterceptor(property: IrProperty): Boolean {
    val returnType = property.getter?.returnType ?: return false
    return returnType.isSubtypeOfClass(interceptor)
  }
}

private fun IrPluginContext.testInterceptorApis(
  testFunctionClassId: ClassId,
  testInterceptorClassId: ClassId,
): TestInterceptorApis? {
  val interceptorClass = referenceClass(testInterceptorClassId) ?: return null
  val functionClass = referenceClass(testFunctionClassId) ?: return null
  return TestInterceptorApis(
    interceptor = interceptorClass,
    function = functionClass,
    intercept = referenceFunctions(testInterceptorClassId.callableId("intercept")).single(),
    testScope = referenceProperties(testFunctionClassId.callableId("scope")).singleOrNull(),
    packageName = referenceProperties(testFunctionClassId.callableId("packageName")).single(),
    className = referenceProperties(testFunctionClassId.callableId("className")).single(),
    functionName = referenceProperties(testFunctionClassId.callableId("functionName")).single(),
    invoke = referenceFunctions(testFunctionClassId.callableId("invoke")).single(),
  )
}

private val kotlinPackage = FqPackageName("kotlin")
private val throwableAddSuppressedId = kotlinPackage.callableId("addSuppressed")

private val kotlinCoroutinePackage = FqPackageName("kotlin.coroutines")
private val coroutineContextId = kotlinCoroutinePackage.callableId("CoroutineContext")

private val kotlinTimeFqPackage = FqPackageName("kotlin.time")
private val durationId = kotlinTimeFqPackage.classId("Duration")

private val burstFqPackage = FqPackageName("app.cash.burst")
private val burstCoroutinesFqPackage = FqPackageName("app.cash.burst.coroutines")
private val burstAnnotationId = burstFqPackage.classId("Burst")
private val burstValuesId = burstFqPackage.callableId("burstValues")
private val interceptTestAnnotationId = burstFqPackage.classId("InterceptTest")

private val junitPackage = FqPackageName("org.junit")
private val junitTestClassId = junitPackage.classId("Test")
private val junitBeforeTestClassId = junitPackage.classId("Before")
private val junitAfterTestClassId = junitPackage.classId("After")
private val junit5Package = FqPackageName("org.junit.jupiter.api")
private val junit5BeforeEachClassId = junit5Package.classId("BeforeEach")
private val junit5AfterEachClassId = junit5Package.classId("AfterEach")
private val junit5TestClassId = junit5Package.classId("Test")
private val kotlinTestPackage = FqPackageName("kotlin.test")
private val kotlinTestClassId = kotlinTestPackage.classId("Test")
private val kotlinBeforeTestClassId = kotlinTestPackage.classId("BeforeTest")
private val kotlinAfterTestClassId = kotlinTestPackage.classId("AfterTest")

private val kotlinJvmFqPackage = FqPackageName("kotlin.jvm")
private val jvmInlineAnnotationId = kotlinJvmFqPackage.classId("JvmInline")

private val kotlinxCoroutinesTestPackage = FqPackageName("kotlinx.coroutines.test")
private val runTestId = kotlinxCoroutinesTestPackage.callableId("runTest")
private val testScopeId = kotlinxCoroutinesTestPackage.classId("TestScope")

internal val IrAnnotationContainer.hasAtBurst: Boolean
  get() = hasAnnotation(burstAnnotationId)

internal val IrAnnotationContainer.hasAtTestInterceptor: Boolean
  get() = hasAnnotation(interceptTestAnnotationId)

internal val IrAnnotationContainer.hasAtJvmInline: Boolean
  get() = hasAnnotation(jvmInlineAnnotationId)
