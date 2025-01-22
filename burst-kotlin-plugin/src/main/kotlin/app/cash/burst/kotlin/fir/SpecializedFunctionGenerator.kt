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

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * This matches functions annotated `@Test` that have 1 or more constructor parameters. Either the
 * function or its enclosing class must also be annotated `@Burst`.
 *
 * This generates a function for each specialization of the function.
 *
 * The default specialization gets the same name as the original test. Unlike that function, the
 * default specialization has zero arguments.
 */
class SpecializedFunctionGenerator(
  session: FirSession,
) : FirDeclarationGenerationExtension(session) {
  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    val classHasAtBurst = classSymbol.hasAtBurst
    val burstFunctions = classSymbol.declarationSymbols
      .filterIsInstance<FirFunctionSymbol<*>>()
      .filter { it.hasAtTest  && it.valueParameterSymbols.isNotEmpty() }
      .filter { classHasAtBurst || it.hasAtBurst }

    return buildSet {
      for (burstFunction in burstFunctions) {
        for (specialization in specializations(session, burstFunction)) {
          add(specialization.name(burstFunction))
        }
      }
    }
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    val owner = context?.owner ?: return listOf()

    val function = createMemberFunction(
      owner = owner,
      key = BurstGeneratedDeclarationKey,
      name = callableId.callableName,
      returnType = StandardClassIds.Unit.constructClassLikeType(),
    )

    return listOf(function.symbol)
  }

  private val FirBasedSymbol<*>.hasAtBurst
    get() = hasAnnotation(atBurst, session)

  private val FirBasedSymbol<*>.hasAtTest
    get() = hasAnnotation(junitAtTest, session) ||
      hasAnnotation(junit5AtTest, session) ||
      hasAnnotation(kotlinTestAtTest, session)
}

private fun FirSpecialization.name(base: FirFunctionSymbol<*>): Name {
  return when {
    isDefault -> base.name
    else -> Name.identifier("${base.name}_$name")
  }
}
