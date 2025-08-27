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
import org.jetbrains.kotlin.backend.common.ir.moveBodyTo
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
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
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.IMPLICIT_ARGUMENT
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.VARIABLE_AS_FUNCTION
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.addFakeOverrides
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

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
 *     TestFunction(
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
 * override fun intercept(testFunction: TestFunction) {
 *   interceptor.intercept(
 *     TestInterceptor.Test(
 *       packageName = testFunction.packageName,
 *       className = testFunction.className,
 *       functionName = testFunction.functionName,
 *       block = {
 *         testFunction()
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
  private val superclassIntercept: IrSimpleFunction?,
  private val existingIntercept: IrSimpleFunction?,
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

  fun defineIntercept(): IrSimpleFunction {
    check(interceptFunctionSymbol == null) { "already defined?!" }

    // If there's already a fake override, remove it.
    if (existingIntercept != null) {
      originalParent.declarations.remove(existingIntercept)
    }

    // TODO: don't add 'implements TestInterceptor' if it already does.
    originalParent.superTypes += burstApis.testInterceptorType

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
        name = Name.identifier("testFunction")
        type = burstApis.testFunctionType
      }
    }

    function.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = originalParent.symbol,
    ) {
      val packageNameLocal = irTemporary(
        value = irCall(burstApis.testFunctionPackageName.owner.getter!!).apply {
          origin = IrStatementOrigin.Companion.GET_PROPERTY
          dispatchReceiver = irGet(function.parameters[1])
        },
        nameHint = "packageName",
      )

      val classNameLocal = irTemporary(
        value = irCall(burstApis.testFunctionClassName.owner.getter!!).apply {
          origin = IrStatementOrigin.Companion.GET_PROPERTY
          dispatchReceiver = irGet(function.parameters[1])
        },
        nameHint = "className",
      )

      val functionNameLocal = irTemporary(
        value = irCall(burstApis.testFunctionFunctionName.owner.getter!!).apply {
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
            for (functionSymbol in beforeTestFunctionSymbols) {
              +irCall(
                callee = functionSymbol,
              ).apply {
                dispatchReceiver = irGet(function.dispatchReceiverParameter!!).apply {
                  origin = IMPLICIT_ARGUMENT
                }
              }
            }

            // Execute the test body.
            +irCall(burstApis.testFunctionInvoke).apply {
              dispatchReceiver = irGet(function.parameters[1]).apply {
                origin = VARIABLE_AS_FUNCTION
              }
            }
          },
        )

        // Call each function annotated `@AfterTest` in alphabetical order.
        for (functionSymbol in afterTestFunctionSymbols) {
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

      for (interceptor in interceptorProperties.reversed()) {
        val testFunctionClass = createTestFunctionClass(
          nameHint = "${interceptor.name.asString().capitalizeAsciiOnly()}TestFunction",
          packageName = packageNameLocal,
          className = classNameLocal,
          functionName = functionNameLocal,
          proceed = proceed,
        )
        +testFunctionClass
        proceed = callInterceptorIntercept(
          receiverLocal = function.dispatchReceiverParameter!!,
          interceptorProperty = interceptor,
          testInstance = irCall(callee = testFunctionClass.constructors.single()),
        )
      }

      if (superclassIntercept != null) {
        val testFunctionClass = createTestFunctionClass(
          nameHint = "CallSuperTestFunction",
          packageName = packageNameLocal,
          className = classNameLocal,
          functionName = functionNameLocal,
          proceed = proceed,
        )
        +testFunctionClass
        proceed = callSuperIntercept(
          superclassIntercept,
          interceptFunction = function,
          testInstance = irCall(callee = testFunctionClass.constructors.single()),
        )
      }

      +proceed
    }

    function.patchDeclarationParents()

    originalParent.addDeclaration(function)

    this.interceptFunctionSymbol = function.symbol

    // Make this function visible to subclasses in other modules.
    if (existingIntercept == null) {
      pluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(function)
    }

    return function
  }

  /** Passes [testInstance] to the `intercept` function of [interceptorProperty]. */
  private fun IrBlockBodyBuilder.callInterceptorIntercept(
    receiverLocal: IrValueDeclaration,
    interceptorProperty: IrProperty,
    testInstance: IrExpression,
  ): IrExpression {
    val interceptorInstance = irCall(interceptorProperty.getter!!)
      .apply {
        origin = IrStatementOrigin.Companion.GET_PROPERTY
        dispatchReceiver = irGet(receiverLocal).apply {
          origin = IMPLICIT_ARGUMENT
        }
      }

    return irCall(
      callee = burstApis.testInterceptorIntercept,
    ).apply {
      dispatchReceiver = interceptorInstance
      arguments[1] = testInstance
    }
  }

  /** Calls `super.intercept()`. */
  private fun IrBlockBodyBuilder.callSuperIntercept(
    superclassIntercept: IrSimpleFunction,
    interceptFunction: IrSimpleFunction,
    testInstance: IrExpression,
  ): IrCall {
    return irCall(
      callee = superclassIntercept,
      superQualifierSymbol = originalParent.superClass!!.symbol,
    ).apply {
      dispatchReceiver = irGet(interceptFunction.dispatchReceiverParameter!!).apply {
        origin = VARIABLE_AS_FUNCTION
      }
      arguments[1] = testInstance
    }
  }

  /** Rewrite [original] to call the interceptors. */
  fun inject(original: IrSimpleFunction) {
    val interceptFunctionSymbol = this.interceptFunctionSymbol
      ?: error("call defineIntercept() first")

    // If there's no function body to rewrite, we're probably looking at a superclass from another
    // module. The rewrite won't be emitted anywhere, but we still need to generate its symbols for
    // subclasses to call.
    if (original.body == null) return

    if (original.modality != Modality.FINAL && originalParent.modality != Modality.FINAL) {
      unexpectedOpenFunction(original)
    }

    original.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = original.symbol,
    ) {
      val testFunctionClass = createTestFunctionClass(
        nameHint = "${original.name.asString().capitalizeAsciiOnly()}TestFunction",
        packageName = irString(packageName),
        className = irString(className),
        functionName = irString(original.name.asString()),
        buildBody = { body = original.moveBodyTo(this, mapOf()) },
      )
      +testFunctionClass
      val testFunctionInstance = irCall(testFunctionClass.constructors.single())

      +irCall(
        callee = interceptFunctionSymbol,
      ).apply {
        dispatchReceiver = irGet(original.dispatchReceiverParameter!!).apply {
          origin = IMPLICIT_ARGUMENT
        }
        arguments[1] = testFunctionInstance
      }
    }

    original.patchDeclarationParents()
  }

  private fun IrBlockBodyBuilder.createTestFunctionClass(
    nameHint: String,
    packageName: IrValueDeclaration,
    className: IrValueDeclaration,
    functionName: IrValueDeclaration,
    proceed: IrExpression,
  ): IrClass {
    return createTestFunctionClass(
      nameHint = nameHint,
      packageName = irGet(packageName),
      className = irGet(className),
      functionName = irGet(functionName),
    ) {
      irFunctionBody(
        context = context,
        scopeOwnerSymbol = scope.scopeOwnerSymbol,
      ) {
        +proceed
      }
    }
  }

  /** Create a subclass of `TestFunction` and returns its constructor. */
  private fun createTestFunctionClass(
    nameHint: String,
    packageName: IrExpression,
    className: IrExpression,
    functionName: IrExpression,
    buildBody: IrSimpleFunction.() -> Unit,
  ): IrClass {
    val testFunctionClass = pluginContext.irFactory.buildClass {
      initDefaults(originalParent)
      name = Name.identifier(nameHint)
      visibility = DescriptorVisibilities.LOCAL
    }.apply {
      parent = originalParent
      superTypes = listOf(burstApis.testFunction.defaultType)
      createThisReceiverParameter()
    }

    testFunctionClass.addConstructor {
      initDefaults(originalParent)
    }.apply {
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = burstApis.testFunction.constructors.single(),
        ) {
          arguments[0] = packageName
          arguments[1] = className
          arguments[2] = functionName
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = testFunctionClass.symbol,
        )
      }
    }

    val invokeFunction = testFunctionClass.addFunction {
      initDefaults(originalParent)
      name = burstApis.testFunctionInvoke.owner.name
      returnType = pluginContext.irBuiltIns.unitType
    }.apply {
      parameters += buildReceiverParameter {
        initDefaults(originalParent)
        type = testFunctionClass.defaultType
      }
      overriddenSymbols = listOf(burstApis.testFunctionInvoke)
      buildBody()
    }

    testFunctionClass.addFakeOverrides(
      IrTypeSystemContextImpl(pluginContext.irBuiltIns),
      listOf(invokeFunction),
    )

    return testFunctionClass
  }

  private fun unexpectedOpenFunction(function: IrFunction): Nothing {
    throw BurstCompilationException(
      "@InterceptTest cannot target test functions that are non-final",
      function,
    )
  }
}
