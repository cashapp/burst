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
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI

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
   * Rewrites the subject of [input] using [InterceptorInjector] if necessary.
   *
   * This returns the updated input with a newly-created `intercept()` function declared by the
   * subject class, which might be added as a consequence of calling this function.
   */
  fun apply(input: TestInterceptorsInput): TestInterceptorsInput {
    val coroutinesApis = burstApis.coroutinesTestInterceptorApis
    val testInterceptorApis =
      when {
        coroutinesApis != null && input.usesCoroutineTestInterceptor -> coroutinesApis
        input.usesTestInterceptor -> burstApis.testInterceptorApis
        else -> return input // This class doesn't use test interceptors.
      }

    // If this class directly declares an intercept() function, do nothing. Otherwise, our injected
    // symbol would collide with that one.
    val existing = input.interceptFunction
    if (existing != null && !existing.isFakeOverride) return input

    // Rewrite the superclass first!
    val superClassPlan = input.superClassInput
    val superClassInput = superClassPlan?.let { apply(it) }

    val interceptorInjector =
      InterceptorInjector(
        pluginContext = pluginContext,
        burstApis = burstApis,
        testInterceptorApis = testInterceptorApis,
        originalParent = input.subject,
        interceptorProperties = input.testInterceptors + input.coroutineTestInterceptors,
        superclassIntercept = superClassInput?.interceptFunction,
        existingIntercept = existing,
      )

    for (function in input.beforeTestFunctions) {
      interceptorInjector.adoptBeforeTest(function)
    }
    for (function in input.afterTestFunctions) {
      interceptorInjector.adoptAfterTest(function)
    }

    interceptorInjector.defineTestFunctionPackageNameProperty()
    interceptorInjector.defineTestFunctionClassNameProperty()
    val newInterceptFunction = interceptorInjector.defineIntercept()

    for (function in input.testFunctions) {
      interceptorInjector.inject(function)
    }

    return input.copy(interceptFunction = newInterceptFunction)
  }
}
