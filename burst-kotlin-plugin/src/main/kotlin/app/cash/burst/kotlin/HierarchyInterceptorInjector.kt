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

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.ir.isInCurrentModule
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.superClass

/**
 * Adds `@InterceptTest` to the classes in a class hierarchy.
 *
 * This rewrites superclasses first because subclasses must call `super.intercept()` if that exists.
 */
internal class HierarchyInterceptorInjector(
  private val pluginContext: IrPluginContext,
  private val burstApis: BurstApis,
) {
  /**
   * Rewrites [classDeclaration] using [InterceptorInjector] if necessary.
   *
   * This returns the `intercept()` function declared by [classDeclaration], which might be added as
   * a consequence of calling this function. Returns null if this class doesn't declare such a
   * function.
   */
  fun apply(classDeclaration: IrClass): IrSimpleFunction? {
    // If this class directly declares an intercept() function, return that. Otherwise, our injected
    // symbol would collide with that one.
    val existing = classDeclaration.interceptFunction
    if (existing != null) return existing

    val superClass = classDeclaration.superClass
    val superClassInterceptFunction = when {
      // Rewrite the superclass first!
      superClass?.isInCurrentModule() == true -> apply(superClass)

      // Classes from other modules will have 'intercept()' functions injected already.
      else -> classDeclaration.superclasses.firstNotNullOfOrNull { it.interceptFunction }
    }

    val interceptorProperties = classDeclaration.properties.filter {
      it.hasAtTestInterceptor && it.overriddenSymbols.isEmpty()
    }.toList()

    // If this class doesn't participate, we're done.
    if (interceptorProperties.isEmpty() && superClassInterceptFunction == null) {
      return null
    }

    val originalFunctions = classDeclaration.functions.toList()

    val interceptorInjector = InterceptorInjector(
      pluginContext = pluginContext,
      burstApis = burstApis,
      originalParent = classDeclaration,
      interceptorProperties = interceptorProperties,
      superclassIntercept = superClassInterceptFunction,
    )

    for (function in originalFunctions) {
      if (function.overriddenSymbols.isNotEmpty()) continue

      if (burstApis.findBeforeTestAnnotation(function) != null) {
        interceptorInjector.adoptBeforeTest(function)
      }
      if (burstApis.findAfterTestAnnotation(function) != null) {
        interceptorInjector.adoptAfterTest(function)
      }
    }

    val result = interceptorInjector.defineIntercept()

    for (function in originalFunctions) {
      if (function.overriddenSymbols.isNotEmpty()) continue

      if (burstApis.findTestAnnotation(function) != null) {
        interceptorInjector.inject(function)
      }
    }

    return result
  }

  /** Returns a sequence that does not include this class itself. */
  private val IrClass.superclasses: Sequence<IrClass>
    get() = generateSequence(superClass) { it.superClass }

  /** The `intercept()` function declared by this class. */
  private val IrClass.interceptFunction: IrSimpleFunction?
    get() = functions.firstOrNull { burstApis.testInterceptorIntercept in it.overriddenSymbols }
}
