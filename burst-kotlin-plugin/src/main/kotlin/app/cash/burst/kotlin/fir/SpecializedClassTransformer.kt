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
package app.cash.burst.kotlin.fir

import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.copy
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol

/**
 * This matches classes annotated `@Burst` that have 1 or more constructor parameters.
 *
 * This makes the class open.
 *
 * If there is no default specialization this makes the test class abstract.
 *
 * This makes the constructor protected.
 *
 * This removes default arguments from the constructor.
 */
class SpecializedClassTransformer(
  session: FirSession,
) : FirStatusTransformerExtension(session) {
  override fun needTransformStatus(declaration: FirDeclaration): Boolean {

    if (false) {
      if (declaration is FirConstructor && declaration.isPrimary) {
        val containingClass = declaration.getContainingClass()
        val annotation =
          containingClass?.getAnnotationByClassId(atBurst, session)
        return annotation != null
      }
    }

    return false
  }

  override fun transformStatus(
    status: FirDeclarationStatus,
    constructor: FirConstructor,
    containingClass: FirClassLikeSymbol<*>?,
    isLocal: Boolean
  ): FirDeclarationStatus {
    if (false) {
//      constructor.originalVisibility = status.visibility
      return when (status.visibility) {
        Visibilities.Private -> status
        else -> status.copy(visibility = Visibilities.Private)
      }
    }

    return status
  }
}
