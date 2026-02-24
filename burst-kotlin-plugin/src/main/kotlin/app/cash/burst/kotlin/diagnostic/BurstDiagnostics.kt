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

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.NAME_IDENTIFIER
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.PARAMETER_DEFAULT_VALUE
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtElement

object BurstDiagnostics : KtDiagnosticsContainer() {
  val INVALID_BURST_ARGUMENT by error0<KtElement>(NAME_IDENTIFIER)

  val INVALID_DEFAULT_VALUE by error0<KtElement>(PARAMETER_DEFAULT_VALUE)

  val PARAMETER_REFERENCE_NOT_ALLOWED by error0<KtElement>(NAME_IDENTIFIER)

  override fun getRendererFactory(): BaseDiagnosticRendererFactory {
    return BurstErrorMessages
  }

  private object BurstErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by
      KtDiagnosticFactoryToRendererMap("Burst") { map ->
        map.put(
          INVALID_BURST_ARGUMENT,
          "@Burst parameter must be a boolean, an enum, or have a burstValues() default value",
        )

        map.put(
          INVALID_DEFAULT_VALUE,
          "@Burst parameter default must be burstValues(), a constant, null, or absent",
        )

        map.put(
          PARAMETER_REFERENCE_NOT_ALLOWED,
          "@Burst parameter may not reference other parameters",
        )
      }
  }
}
