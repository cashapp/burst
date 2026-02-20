/*
 * Copyright (C) 2026 Cash App
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
package app.cash.burst.kotlin.diagnostic

import app.cash.burst.kotlin.BurstApis
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirConstructorChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.isBoolean
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol

internal class ClassConstructorParameterChecker : FirConstructorChecker(MppCheckerKind.Common) {
  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirConstructor) {
    val containingClass = declaration.getContainingClass() ?: return
    // We don't check abstract class constructors because the values are provided by concrete
    // implementations
    if (containingClass.isAbstract) return

    if (!containingClass.hasAnnotation(BurstApis.burstAnnotationId, context.session)) return

    declaration.valueParameters.forEach { parameter -> checkParam(parameter) }
  }
}

internal class FunctionParameterChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {
  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirNamedFunction) {
    val isInBurstClass =
      declaration.getContainingClass()?.hasAnnotation(BurstApis.burstAnnotationId, context.session)
        ?: return
    val isTestAnnotated =
      declaration.annotations.any {
        it.toAnnotationClassId(context.session) in BurstApis.testClassIds
      }

    if (!isInBurstClass && !isTestAnnotated) return

    // We can rely on the super declaration being checked!
    if (declaration.isOverride) return

    declaration.valueParameters.forEach { parameter -> checkParam(parameter) }
  }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkParam(declaration: FirValueParameter) {
  val parameterType = declaration.returnTypeRef.toRegularClassSymbol(context.session) ?: return
  if (parameterType.isEnumClass || parameterType.isBoolean()) return

  val defaultArgument = declaration.defaultValue?.toResolvedCallableSymbol(context.session)
  if (defaultArgument?.callableId == BurstApis.burstValuesId) return

  reporter.reportOn(declaration.source, BurstDiagnostics.INVALID_BURST_ARGUMENT)
}
