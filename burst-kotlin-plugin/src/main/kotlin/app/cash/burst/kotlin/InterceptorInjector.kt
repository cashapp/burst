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
package app.cash.burst.kotlin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildReceiverParameter
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.IMPLICIT_ARGUMENT
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.VARIABLE_AS_FUNCTION
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.Name

/**
 * This starts with an interceptor property like this:
 *
 * ```kotlin
 * @InterceptTest
 * val interceptor: TestInterceptor
 * ```
 *
 * And a test function like this:
 *
 * ```kotlin
 * @Test
 * fun happyPath() {
 *   assertThat(5 + 5).isEqualTo(10)
 * }
 * ```
 *
 * It rewrites the test function to call an `intercept` function:
 *
 * ```kotlin
 * @Test
 * fun happyPath() {
 *   intercept(
 *     TestInterceptor.Test(
 *       packageName = "com.example",
 *       className = "SampleTest",
 *       functionName = "happyPath",
 *       block = {
 *         assertThat(5 + 5).isEqualTo(10)
 *       }
 *     )
 *   )
 * }
 * ```
 *
 * And it declares an `intercept` function on the test class:
 *
 * ```kotlin
 * override fun intercept(test: TestInterceptor.Test) {
 *   interceptor.intercept(
 *     TestInterceptor.Test(
 *       packageName = test.packageName,
 *       className = test.className,
 *       functionName = test.functionName,
 *       block = {
 *         test()
 *       }
 *     )
 *   )
 * }
 * ```
 *
 * If there are multiple interceptors, they are applied in order.
 *
 * If there are functions annotated `@BeforeTest`, that annotation is removed and the function is
 * called directly in `intercept()`.
 *
 * If there are functions annotated `@AfterTest`, that annotation is removed and the function is
 * called directly in `intercept()`.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class InterceptorInjector(
  private val pluginContext: IrPluginContext,
  private val burstApis: BurstApis,
  private val originalParent: IrClass,
  private val interceptorProperties: List<IrProperty>,
) {
  private val packageName = originalParent.packageFqName?.asString() ?: ""
  private val className = generateSequence(originalParent) { it.parentClassOrNull }
    .toList()
    .reversed()
    .joinToString(".") { it.name.asString() }

  /** The previously-annotated `@BeforeTest` functions that we must call in the interceptor. */
  private var beforeTestFunctionSymbols = mutableListOf<IrSimpleFunctionSymbol>()

  /** The previously-annotated `@AfterTest` functions that we must call in the interceptor. */
  private var afterTestFunctionSymbols = mutableListOf<IrSimpleFunctionSymbol>()

  /** The `intercept()` function we defined. */
  private var interceptFunctionSymbol: IrSimpleFunctionSymbol? = null

  /** Remove the `@BeforeTest` annotation on [function]. */
  fun adoptBeforeTest(function: IrSimpleFunction) {
    function.annotations = function.annotations.filter {
      it.type.classOrNull !in burstApis.beforeTestSymbols
    }

    beforeTestFunctionSymbols += function.symbol
  }

  /** Remove the `@AfterTest` annotation on [function]. */
  fun adoptAfterTest(function: IrSimpleFunction) {
    function.annotations = function.annotations.filter {
      it.type.classOrNull !in burstApis.afterTestSymbols
    }

    afterTestFunctionSymbols += function.symbol
  }

  fun defineIntercept() {
    check(interceptFunctionSymbol == null) { "already defined?!" }

    // TODO: don't add 'implements TestInterceptor' if it already does.
    originalParent.superTypes += burstApis.testInterceptor.defaultType

    val function = originalParent.factory.buildFun {
      initDefaults(originalParent)
      name = Name.identifier("intercept")
      returnType = pluginContext.irBuiltIns.unitType
    }.apply {
      modality = Modality.OPEN
      overriddenSymbols += burstApis.testInterceptorIntercept
      parameters += buildReceiverParameter {
        type = originalParent.defaultType
      }
      addValueParameter {
        initDefaults(originalParent)
        name = Name.identifier("test")
        type = burstApis.testInterceptorTest.defaultType
      }
    }

    function.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = originalParent.symbol,
    ) {
      val packageNameLocal = irTemporary(
        value = irCall(burstApis.testInterceptorTestPackageName.owner.getter!!).apply {
          origin = IrStatementOrigin.Companion.GET_PROPERTY
          dispatchReceiver = irGet(function.parameters[1])
        },
        nameHint = "packageName",
      )

      val classNameLocal = irTemporary(
        value = irCall(burstApis.testInterceptorTestClassName.owner.getter!!).apply {
          origin = IrStatementOrigin.Companion.GET_PROPERTY
          dispatchReceiver = irGet(function.parameters[1])
        },
        nameHint = "className",
      )

      val functionNameLocal = irTemporary(
        value = irCall(burstApis.testInterceptorTestFunctionName.owner.getter!!).apply {
          origin = IrStatementOrigin.Companion.GET_PROPERTY
          dispatchReceiver = irGet(function.parameters[1])
        },
        nameHint = "functionName",
      )

      var proceed: IrExpression = irBlock {
        val failure = irTemporary(
          value = irNull(),
          irType = pluginContext.irBuiltIns.throwableType.makeNullable(),
          nameHint = "failure",
          isMutable = true,
        )

        +irAccumulateFailure(
          burstApis,
          failure,
          irBlock {
            // Call each function annotated `@BeforeTest` in reverse alphabetical order.
            for (functionSymbol in beforeTestFunctionSymbols.sortedBy { it.owner.name }.reversed()) {
              +irCall(
                callee = functionSymbol,
              ).apply {
                dispatchReceiver = irGet(function.dispatchReceiverParameter!!).apply {
                  origin = IMPLICIT_ARGUMENT
                }
              }
            }

            // Execute the test body.
            +irCall(burstApis.testInterceptorTestInvoke).apply {
              dispatchReceiver = irGet(function.parameters[1]).apply {
                origin = VARIABLE_AS_FUNCTION
              }
            }
          },
        )

        // Call each function annotated `@AfterTest` in alphabetical order.
        for (functionSymbol in afterTestFunctionSymbols.sortedBy { it.owner.name }) {
          +irAccumulateFailure(
            burstApis,
            failure,
            irCall(
              callee = functionSymbol,
            ).apply {
              dispatchReceiver = irGet(function.dispatchReceiverParameter!!).apply {
                origin = IMPLICIT_ARGUMENT
              }
            },
          )
        }

        // If something threw an exception, propagate it.
        +irIfThen(
          context.irBuiltIns.unitType,
          irNot(irEqualsNull(irGet(failure))),
          irThrow(irGet(failure)),
        )
      }

      for (interceptor in interceptorProperties.sortedBy { it.name }) {
        proceed = wrapWithInterceptor(
          receiverLocal = function.dispatchReceiverParameter!!,
          interceptorProperty = interceptor,
          packageName = packageNameLocal,
          className = classNameLocal,
          functionName = functionNameLocal,
          proceed = proceed,
        )
      }

      +proceed
    }

    function.patchDeclarationParents()

    originalParent.addDeclaration(function)

    this.interceptFunctionSymbol = function.symbol
  }

  /** Wraps [proceed] to apply the interceptor [interceptorProperty]. */
  private fun IrBlockBodyBuilder.wrapWithInterceptor(
    receiverLocal: IrValueDeclaration,
    interceptorProperty: IrProperty,
    packageName: IrValueDeclaration,
    className: IrValueDeclaration,
    functionName: IrValueDeclaration,
    proceed: IrExpression,
  ): IrExpression {
    val interceptorInstance = irCall(interceptorProperty.getter!!)
      .apply {
        origin = IrStatementOrigin.Companion.GET_PROPERTY
        dispatchReceiver = irGet(receiverLocal).apply {
          origin = IMPLICIT_ARGUMENT
        }
      }

    val testInstance = irCall(
      callee = burstApis.testInterceptorTest.constructors.single(),
    ).apply {
      arguments[0] = irGet(packageName)
      arguments[1] = irGet(className)
      arguments[2] = irGet(functionName)
      arguments[3] = localLambda(scope.scopeOwnerSymbol) {
        +proceed
      }
    }

    return irCall(
      callee = burstApis.testInterceptorIntercept,
    ).apply {
      dispatchReceiver = interceptorInstance
      arguments[1] = testInstance
    }
  }

  /** Rewrite [original] to call the interceptors. */
  fun inject(original: IrSimpleFunction) {
    val interceptFunctionSymbol = this.interceptFunctionSymbol
      ?: error("call defineIntercept() first")

    original.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = original.symbol,
    ) {
      val testInstance = irCall(
        callee = burstApis.testInterceptorTest.constructors.single(),
      ).apply {
        arguments[0] = irString(packageName)
        arguments[1] = irString(className)
        arguments[2] = irString(original.name.asString())
        arguments[3] = localLambda(original.symbol) {
          +original.body!!.statements
        }
      }

      +irCall(
        callee = interceptFunctionSymbol,
      ).apply {
        dispatchReceiver = irGet(original.dispatchReceiverParameter!!).apply {
          origin = IMPLICIT_ARGUMENT
        }
        arguments[1] = testInstance
      }
    }

    original.patchDeclarationParents()
  }
}
