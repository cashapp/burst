/*
 * Copyright (C) 2025 Cash App
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

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.ir.visitors.IrTransformer

/**
 * The declarations on a class that are interesting to test interceptors.
 *
 * Note that overridden symbols are omitted.
 */
internal data class TestInterceptorsInput(
  val subject: IrClass,
  val superClassInput: TestInterceptorsInput?,
  val interceptFunction: IrSimpleFunction?,
  /** Properties annotated `@InterceptTest` of type `TestInterceptor`. */
  val testInterceptors: List<IrProperty>,
  /** Properties annotated `@InterceptTest` of type `CoroutineTestInterceptor`. */
  val coroutineTestInterceptors: List<IrProperty>,
  /** Properties annotated `@InterceptTest` of any other type. */
  val otherInterceptTestProperties: List<IrProperty>,
  val beforeTestFunctions: List<IrSimpleFunction>,
  val afterTestFunctions: List<IrSimpleFunction>,
  val testFunctions: List<Function>,
) {
  /** True if there's a `TestInterceptor` property in this or a superclass. */
  val usesTestInterceptor: Boolean
    get() = interceptFunction?.isSuspend == false ||
      testInterceptors.isNotEmpty() ||
      superClassInput?.usesTestInterceptor == true

  /** True if there's a `CoroutineTestInterceptor` property in this or a superclass. */
  val usesCoroutineTestInterceptor: Boolean
    get() = interceptFunction?.isSuspend == true ||
      coroutineTestInterceptors.isNotEmpty() ||
      superClassInput?.usesCoroutineTestInterceptor == true

  sealed interface Function {
    val function: IrSimpleFunction

    /** A test that calls `runTest()` in its body. */
    data class Suspending(
      override val function: IrSimpleFunction,
      val runTestCall: IrCall,
    ) : Function

    /** A test that does not call `runTest()`. */
    data class NonSuspending(
      override val function: IrSimpleFunction,
    ) : Function
  }
}

internal class TestInterceptorsInputReader(
  val burstApis: BurstApis,
  val classDeclaration: IrClass,
) {
  internal fun read(): TestInterceptorsInput {
    val superClassPlan = classDeclaration.superClass?.let {
      TestInterceptorsInputReader(burstApis, it).read()
    }
    var interceptFunction: IrSimpleFunction? = null
    val testInterceptors = mutableListOf<IrProperty>()
    val coroutineTestInterceptors = mutableListOf<IrProperty>()
    val otherInterceptTestProperties = mutableListOf<IrProperty>()
    val beforeTestFunctions = mutableListOf<IrSimpleFunction>()
    val afterTestFunctions = mutableListOf<IrSimpleFunction>()
    val testFunctions = mutableListOf<TestInterceptorsInput.Function>()

    for (declaration in classDeclaration.declarations) {
      if (declaration is IrProperty) {
        if (declaration.overriddenSymbols.isNotEmpty()) continue

        if (declaration.hasAtTestInterceptor) {
          if (burstApis.testInterceptorApis.isTestInterceptor(declaration)) {
            // @InterceptTest val interceptor: TestInterceptor
            testInterceptors += declaration
          } else if (
            burstApis.coroutinesTestInterceptorApis != null &&
            burstApis.coroutinesTestInterceptorApis.isTestInterceptor(declaration)
          ) {
            // @InterceptTest val interceptor: CoroutineTestInterceptor
            coroutineTestInterceptors += declaration
          } else {
            otherInterceptTestProperties += declaration
          }
        }
      }
      if (declaration is IrSimpleFunction) {
        // override fun intercept(testFunction: TestFunction)
        if (declaration.isIntercept(burstApis.testInterceptorApis.intercept.owner)) {
          interceptFunction = declaration
        }

        // override suspend fun intercept(testFunction: CoroutineTestFunction)
        if (
          burstApis.coroutinesTestInterceptorApis != null &&
          declaration.isIntercept(burstApis.coroutinesTestInterceptorApis.intercept.owner)
        ) {
          interceptFunction = declaration
        }

        if (declaration.overriddenSymbols.isEmpty()) {
          // @BeforeTest
          if (burstApis.findBeforeTestAnnotation(declaration) != null) {
            beforeTestFunctions += declaration
          }

          // @AfterTest
          if (burstApis.findAfterTestAnnotation(declaration) != null) {
            afterTestFunctions += declaration
          }
        }

        // @Test
        if (burstApis.findTestAnnotation(declaration) != null) {
          testFunctions += readTestFunction(declaration)
        }
      }
    }

    return TestInterceptorsInput(
      subject = classDeclaration,
      superClassInput = superClassPlan,
      interceptFunction = interceptFunction,
      testInterceptors = testInterceptors,
      coroutineTestInterceptors = coroutineTestInterceptors,
      otherInterceptTestProperties = otherInterceptTestProperties,
      beforeTestFunctions = beforeTestFunctions,
      afterTestFunctions = afterTestFunctions,
      testFunctions = testFunctions,
    )
  }

  private fun readTestFunction(
    function: IrSimpleFunction,
  ): TestInterceptorsInput.Function {
    var runTestCall: IrCall? = null

    function.body?.transform(
      object : IrTransformer<Unit>() {
        override fun visitCall(
          expression: IrCall,
          data: Unit,
        ): IrElement {
          if (runTestCall == null && burstApis.isRunTest(expression)) {
            runTestCall = expression
          }
          return super.visitCall(expression, data)
        }
      },
      Unit,
    )

    return when {
      runTestCall != null -> TestInterceptorsInput.Function.Suspending(function, runTestCall)
      else -> TestInterceptorsInput.Function.NonSuspending(function)
    }
  }

  /** The `intercept()` function declared by this class. Null if it is a fake override. */
  private fun IrSimpleFunction.isIntercept(interfaceFunction: IrSimpleFunction): Boolean {
    return name == interfaceFunction.name &&
      parameters.size == interfaceFunction.parameters.size &&
      parameters[0].isDispatchReceiver &&
      parameters[1].type.classFqName == interfaceFunction.parameters[1].type.classFqName
  }
}
