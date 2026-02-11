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
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
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
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
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
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isSuspend
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

/**
 * This starts with an interceptor property like this:
 * ```kotlin
 * @InterceptTest
 * val interceptor: TestInterceptor
 * ```
 *
 * And a test function like this:
 * ```kotlin
 * @Test
 * fun happyPath() {
 *   assertThat(5 + 5).isEqualTo(10)
 * }
 * ```
 *
 * It rewrites the test function to call an `intercept` function:
 * ```kotlin
 * @Test
 * fun happyPath() {
 *   intercept(
 *     object : TestFunction(
 *       packageName = burstTestFunctionPackageName(),
 *       className = burstTestFunctionClassName(),
 *       functionName = "happyPath",
 *     ) {
 *       override fun invoke() {
 *         assertThat(5 + 5).isEqualTo(10)
 *       }
 *     }
 *   )
 * }
 * ```
 *
 * And it declares an `intercept` function on the test class:
 * ```kotlin
 * override fun intercept(testFunction: TestFunction) {
 *   interceptor.intercept(
 *     object : TestFunction(
 *       packageName = testFunction.packageName,
 *       className = testFunction.className,
 *       functionName = testFunction.functionName,
 *     ) {
 *       override fun invoke() {
 *         testFunction()
 *       }
 *     }
 *   )
 * }
 * ```
 *
 * If there are multiple interceptors, they are applied in order.
 *
 * The `burstTestFunctionPackageName()` and `burstTestFunctionClassName()` functions are declared so
 * the runtime test metadata can be used if this test is subclassed.
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
  private val testInterceptorApis: TestInterceptorApis,
  private val originalParent: IrClass,
  private val interceptorProperties: List<IrProperty>,
  private val superclassIntercept: IrSimpleFunction?,
  private val existingIntercept: IrSimpleFunction?,
) {
  private val packageName = originalParent.packageFqName?.asString() ?: ""
  private val className =
    generateSequence(originalParent) { it.parentClassOrNull }
      .toList()
      .reversed()
      .joinToString(".") { it.name.asString() }

  /** The previously-annotated `@BeforeTest` functions that we must call in the interceptor. */
  private var beforeTestFunctionSymbols = mutableListOf<IrSimpleFunctionSymbol>()

  /** The previously-annotated `@AfterTest` functions that we must call in the interceptor. */
  private var afterTestFunctionSymbols = mutableListOf<IrSimpleFunctionSymbol>()

  /** The getter or the test class package name. */
  private var testFunctionPackageNameGetterSymbol: IrSimpleFunctionSymbol? = null

  /** The getter or the test class name. */
  private var testFunctionClassNameGetterSymbol: IrSimpleFunctionSymbol? = null

  /** The `intercept()` function we defined. */
  private var interceptFunctionSymbol: IrSimpleFunctionSymbol? = null

  /** Remove the `@BeforeTest` annotation on [function]. */
  fun adoptBeforeTest(function: IrSimpleFunction) {
    function.annotations =
      function.annotations.filter { it.type.classOrNull !in burstApis.beforeTestSymbols }

    beforeTestFunctionSymbols += function.symbol
  }

  /** Remove the `@AfterTest` annotation on [function]. */
  fun adoptAfterTest(function: IrSimpleFunction) {
    function.annotations =
      function.annotations.filter { it.type.classOrNull !in burstApis.afterTestSymbols }

    afterTestFunctionSymbols += function.symbol
  }

  /**
   * Declare a function that returns the current class's package name. If this test is subclassed,
   * this function will be overridden to return the subclass's package name.
   *
   * ```
   * protected open fun burstTestFunctionPackageName(): String = "com.example"
   * ```
   */
  fun defineTestFunctionPackageNameProperty() {
    check(testFunctionPackageNameGetterSymbol == null) { "already defined?!" }

    testFunctionPackageNameGetterSymbol =
      defineTestFunctionNameProperty(Name.identifier("burstTestFunctionPackageName"), packageName)
  }

  /**
   * Declare a function that returns the current class's name. If this test is subclassed, this
   * function will be overridden to return the subclass's name.
   *
   * ```
   * protected open fun burstTestFunctionClassName(): String = "SampleTest"
   * ```
   */
  fun defineTestFunctionClassNameProperty() {
    check(testFunctionClassNameGetterSymbol == null) { "already defined?!" }

    testFunctionClassNameGetterSymbol =
      defineTestFunctionNameProperty(Name.identifier("burstTestFunctionClassName"), className)
  }

  private fun defineTestFunctionNameProperty(name: Name, value: String): IrSimpleFunctionSymbol {
    val overridden = originalParent.superClass?.functions?.singleOrNull { it.name == name }

    // If there's a fake override, remove it.
    originalParent.declarations.removeAll {
      it is IrSimpleFunction && it.name == name && it.isFakeOverride
    }

    val function =
      originalParent.factory
        .buildFun {
          initDefaults(originalParent)
          this.name = name
          visibility = DescriptorVisibilities.PROTECTED
          origin = BURST_ORIGIN
          returnType = pluginContext.irBuiltIns.stringType
        }
        .apply {
          if (overridden != null) {
            this.overriddenSymbols += overridden.symbol
          }
          parameters += buildReceiverParameter { type = originalParent.defaultType }
          irFunctionBody(context = pluginContext) { +irReturn(irString(value)) }
        }

    originalParent.addDeclaration(function)
    pluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(function)
    return function.symbol
  }

  fun defineIntercept(): IrSimpleFunction {
    check(interceptFunctionSymbol == null) { "already defined?!" }

    // If there's already a fake override, remove it.
    if (existingIntercept != null) {
      originalParent.declarations.remove(existingIntercept)
    }

    // TODO: don't add 'implements TestInterceptor' if it already does.
    originalParent.superTypes += testInterceptorApis.interceptorType

    val function =
      originalParent.factory
        .buildFun {
          initDefaults(originalParent)
          name = Name.identifier("intercept")
          returnType = pluginContext.irBuiltIns.unitType
          isSuspend = testInterceptorApis.intercept.isSuspend
        }
        .apply {
          modality = Modality.OPEN
          overriddenSymbols += testInterceptorApis.intercept
          parameters += buildReceiverParameter { type = originalParent.defaultType }
          addValueParameter {
            initDefaults(originalParent)
            name = Name.identifier("testFunction")
            type = testInterceptorApis.functionType
          }
        }

    function.irFunctionBody(context = pluginContext) {
      val testScopeLocal =
        testInterceptorApis.testScope?.let {
          irTemporary(
            value =
              irCall(testInterceptorApis.testScope.owner.getter!!).apply {
                origin = IrStatementOrigin.Companion.GET_PROPERTY
                dispatchReceiver = irGet(function.parameters[1])
              },
            nameHint = "testScope",
          )
        }

      val packageNameLocal =
        irTemporary(
          value =
            irCall(testInterceptorApis.packageName.owner.getter!!).apply {
              origin = IrStatementOrigin.Companion.GET_PROPERTY
              dispatchReceiver = irGet(function.parameters[1])
            },
          nameHint = "packageName",
        )

      val classNameLocal =
        irTemporary(
          value =
            irCall(testInterceptorApis.className.owner.getter!!).apply {
              origin = IrStatementOrigin.Companion.GET_PROPERTY
              dispatchReceiver = irGet(function.parameters[1])
            },
          nameHint = "className",
        )

      val classAnnotationsLocal =
        irTemporary(
          value =
            irCall(testInterceptorApis.classAnnotations.owner.getter!!).apply {
              origin = IrStatementOrigin.Companion.GET_PROPERTY
              dispatchReceiver = irGet(function.parameters[1])
            },
          nameHint = "classAnnotations",
        )

      val functionNameLocal =
        irTemporary(
          value =
            irCall(testInterceptorApis.functionName.owner.getter!!).apply {
              origin = IrStatementOrigin.Companion.GET_PROPERTY
              dispatchReceiver = irGet(function.parameters[1])
            },
          nameHint = "functionName",
        )

      val functionAnnotationsLocal =
        irTemporary(
          value =
            irCall(testInterceptorApis.functionAnnotations.owner.getter!!).apply {
              origin = IrStatementOrigin.Companion.GET_PROPERTY
              dispatchReceiver = irGet(function.parameters[1])
            },
          nameHint = "functionAnnotations",
        )

      var proceed: IrExpression = irBlock {
        val failure =
          irTemporary(
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
              +irCall(callee = functionSymbol).apply {
                dispatchReceiver =
                  irGet(function.dispatchReceiverParameter!!).apply { origin = IMPLICIT_ARGUMENT }
              }
            }

            // Execute the test body.
            +irCall(testInterceptorApis.invoke).apply {
              dispatchReceiver =
                irGet(function.parameters[1]).apply { origin = VARIABLE_AS_FUNCTION }
            }
          },
        )

        // Call each function annotated `@AfterTest` in alphabetical order.
        for (functionSymbol in afterTestFunctionSymbols) {
          +irAccumulateFailure(
            burstApis,
            failure,
            irCall(callee = functionSymbol).apply {
              dispatchReceiver =
                irGet(function.dispatchReceiverParameter!!).apply { origin = IMPLICIT_ARGUMENT }
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
        val testFunctionClass =
          createTestFunctionClass(
            nameHint = "${interceptor.name.asString().capitalizeAsciiOnly()}TestFunction",
            testScope = testScopeLocal,
            packageName = packageNameLocal,
            className = classNameLocal,
            classAnnotations = classAnnotationsLocal,
            functionName = functionNameLocal,
            functionAnnotations = functionAnnotationsLocal,
            proceed = proceed,
          )
        +testFunctionClass
        proceed =
          callInterceptorIntercept(
            receiverLocal = function.dispatchReceiverParameter!!,
            interceptorProperty = interceptor,
            testInstance = irCall(callee = testFunctionClass.constructors.single()),
          )
      }

      if (superclassIntercept != null) {
        val testFunctionClass =
          createTestFunctionClass(
            nameHint = "CallSuperTestFunction",
            testScope = testScopeLocal,
            packageName = packageNameLocal,
            className = classNameLocal,
            classAnnotations = classAnnotationsLocal,
            functionName = functionNameLocal,
            functionAnnotations = functionAnnotationsLocal,
            proceed = proceed,
          )
        +testFunctionClass
        proceed =
          callSuperIntercept(
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
    val interceptorInstance =
      irCall(interceptorProperty.getter!!).apply {
        origin = IrStatementOrigin.Companion.GET_PROPERTY
        dispatchReceiver = irGet(receiverLocal).apply { origin = IMPLICIT_ARGUMENT }
      }

    return irCall(callee = testInterceptorApis.intercept).apply {
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
      )
      .apply {
        dispatchReceiver =
          irGet(interceptFunction.dispatchReceiverParameter!!).apply {
            origin = VARIABLE_AS_FUNCTION
          }
        arguments[1] = testInstance
      }
  }

  /**
   * Rewrite [input] to call the interceptors.
   *
   * If we're looking for a `CoroutinesTestFunction`, this rewrites calls to `runTest()` which is
   * the bridge into coroutines' test functions.
   *
   * If we're looking for a `TestFunction` instead, this rewrites the entire function body.
   */
  fun inject(input: TestFunction) {
    val original = input.function
    // If there's no function body to rewrite, we're probably looking at a superclass from another
    // module. The rewrite won't be emitted anywhere, but we still need to generate its symbols for
    // subclasses to call.
    if (original.body == null) return

    when (input) {
      is TestFunction.Suspending -> {
        val runTestCall = input.runTestCall
        runTestCall.arguments[runTestCall.arguments.size - 1] =
          interceptTestBodyExpression(
            testFunction = original,
            testBodyLambda = runTestCall.arguments[runTestCall.arguments.size - 1]!!,
          )
      }

      else -> {
        original.irFunctionBody(context = pluginContext) {
          +callInterceptWithTestBody(original = original, testScope = null) {
            body = original.moveBodyTo(this, mapOf())
          }
        }
      }
    }

    original.patchDeclarationParents()
  }

  /**
   * Given a call to `runTest()` with a block as the block parameter, this returns a new value for
   * that parameter that applies interceptors.
   */
  private fun interceptTestBodyExpression(
    testFunction: IrSimpleFunction,
    testBodyLambda: IrExpression,
  ): IrFunctionExpression {
    // Create a lambda for the new test body. The new lambda's body creates a `TestFunction` that
    // delegates to the original test body.
    return irTestBodyLambda(
      context = pluginContext,
      burstApis = burstApis,
      original = originalParent,
    ) { testScope ->
      +callInterceptWithTestBody(original = testFunction, testScope = irGet(testScope)) {
        irFunctionBody(context = context) {
          +irCall(
              callee = pluginContext.irBuiltIns.suspendFunctionN(1).symbol.functionByName("invoke")
            )
            .apply {
              arguments[0] = testBodyLambda
              arguments[1] = irGet(testScope)
            }
        }
      }
    }
  }

  /**
   * Declare a class like `HappyPathTestFunction` that extends from either `TestFunction` or
   * `CoroutineTestFunction`, construct it, and call its intercept function.
   */
  private fun IrStatementsBuilder<*>.callInterceptWithTestBody(
    original: IrSimpleFunction,
    testScope: IrExpression?,
    buildBody: IrSimpleFunction.() -> Unit,
  ): IrCall {
    val interceptFunctionSymbol = interceptFunctionSymbol ?: error("call defineIntercept() first")
    val testFunctionPackageNameGetterSymbol =
      testFunctionPackageNameGetterSymbol
        ?: error("call defineTestFunctionClassNameProperty() first")
    val testFunctionClassNameGetterSymbol =
      testFunctionClassNameGetterSymbol ?: error("call defineTestFunctionClassNameProperty() first")

    val testFunctionClass =
      createTestFunctionClass(
        nameHint = "${original.name.asString().capitalizeAsciiOnly()}TestFunction",
        testScope = testScope,
        packageName =
          irCall(testFunctionPackageNameGetterSymbol).apply {
            dispatchReceiver =
              irGet(original.dispatchReceiverParameter!!).apply { origin = IMPLICIT_ARGUMENT }
          },
        className =
          irCall(testFunctionClassNameGetterSymbol).apply {
            dispatchReceiver =
              irGet(original.dispatchReceiverParameter!!).apply { origin = IMPLICIT_ARGUMENT }
          },
        classAnnotations =
          irCall(burstApis.listOfSymbol).apply {
            arguments[0] =
              irVararg(burstApis.annotationSymbol.defaultType, originalParent.annotations)
          },
        functionName = irString(original.name.asString()),
        functionAnnotations =
          irCall(burstApis.listOfSymbol).apply {
            arguments[0] = irVararg(burstApis.annotationSymbol.defaultType, original.annotations)
          },
        buildBody = buildBody,
      )
    +testFunctionClass
    val testFunctionInstance = irCall(testFunctionClass.constructors.single())

    return irCall(callee = interceptFunctionSymbol).apply {
      dispatchReceiver =
        irGet(original.dispatchReceiverParameter!!).apply { origin = IMPLICIT_ARGUMENT }
      arguments[1] = testFunctionInstance
    }
  }

  private fun IrBlockBodyBuilder.createTestFunctionClass(
    nameHint: String,
    testScope: IrValueDeclaration?,
    packageName: IrValueDeclaration,
    className: IrValueDeclaration,
    classAnnotations: IrValueDeclaration,
    functionName: IrValueDeclaration,
    functionAnnotations: IrValueDeclaration,
    proceed: IrExpression,
  ): IrClass {
    return createTestFunctionClass(
      nameHint = nameHint,
      testScope = testScope?.let { irGet(it) },
      packageName = irGet(packageName),
      className = irGet(className),
      classAnnotations = irGet(classAnnotations),
      functionName = irGet(functionName),
      functionAnnotations = irGet(functionAnnotations),
    ) {
      irFunctionBody(context = context) { +proceed }
    }
  }

  /** Create a subclass of `TestFunction` and returns its constructor. */
  private fun createTestFunctionClass(
    nameHint: String,
    testScope: IrExpression?,
    packageName: IrExpression,
    className: IrExpression,
    classAnnotations: IrExpression,
    functionName: IrExpression,
    functionAnnotations: IrExpression,
    buildBody: IrSimpleFunction.() -> Unit,
  ): IrClass {
    val testFunctionClass =
      pluginContext.irFactory
        .buildClass {
          initDefaults(originalParent)
          name = Name.identifier(nameHint)
          visibility = DescriptorVisibilities.LOCAL
        }
        .apply {
          parent = originalParent
          superTypes = listOf(testInterceptorApis.function.defaultType)
          createThisReceiverParameter()
        }

    testFunctionClass
      .addConstructor { initDefaults(originalParent) }
      .apply {
        irConstructorBody(pluginContext) { statements ->
          statements +=
            irDelegatingConstructorCall(
              context = pluginContext,
              symbol = testInterceptorApis.function.constructors.single(),
            ) {
              arguments.clear()
              if (testScope != null) arguments += testScope
              arguments += packageName
              arguments += className
              arguments += classAnnotations
              arguments += functionName
              arguments += functionAnnotations
            }
          statements +=
            irInstanceInitializerCall(
              context = pluginContext,
              classSymbol = testFunctionClass.symbol,
            )
        }
      }

    val invokeFunction =
      testFunctionClass
        .addFunction {
          initDefaults(originalParent)
          name = testInterceptorApis.invoke.owner.name
          returnType = pluginContext.irBuiltIns.unitType
          isSuspend = testInterceptorApis.invoke.isSuspend
        }
        .apply {
          parameters += buildReceiverParameter {
            initDefaults(originalParent)
            type = testFunctionClass.defaultType
          }
          overriddenSymbols = listOf(testInterceptorApis.invoke)
          buildBody()
        }

    testFunctionClass.addFakeOverrides(
      IrTypeSystemContextImpl(pluginContext.irBuiltIns),
      mapOf(testFunctionClass to listOf(invokeFunction)),
    )

    return testFunctionClass
  }
}
