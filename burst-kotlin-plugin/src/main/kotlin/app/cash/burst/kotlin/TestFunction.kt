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

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.visitors.IrTransformer

/** A function annotated `@Test`. */
sealed interface TestFunction {
  val function: IrSimpleFunction
  val testAnnotation: IrClassSymbol

  /** A test that calls `runTest()` in its body. */
  data class Suspending(
    override val function: IrSimpleFunction,
    override val testAnnotation: IrClassSymbol,
    val runTestCall: IrCall,
  ) : TestFunction

  /** A test that does not call `runTest()`. */
  data class NonSuspending(
    override val function: IrSimpleFunction,
    override val testAnnotation: IrClassSymbol,
  ) : TestFunction

  /** Drop `@Test` from this function's annotations. */
  fun dropAtTest() {
    function.annotations = function.annotations.filter {
      it.type.classOrNull != testAnnotation
    }
  }
}

internal class TestFunctionReader(
  val burstApis: BurstApis,
) {
  /** Returns non-null if [function] is annotated `@Test`. */
  fun readOrNull(function: IrSimpleFunction): TestFunction? {
    val testAnnotation = burstApis.findTestAnnotation(function) ?: return null
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
      runTestCall != null -> TestFunction.Suspending(function, testAnnotation, runTestCall)
      else -> TestFunction.NonSuspending(function, testAnnotation)
    }
  }
}
