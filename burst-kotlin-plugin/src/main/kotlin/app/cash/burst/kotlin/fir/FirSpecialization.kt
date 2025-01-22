package app.cash.burst.kotlin.fir

import app.cash.burst.kotlin.NAME_MAX_LENGTH
import app.cash.burst.kotlin.cartesianProduct
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol

internal class FirSpecialization(
  /** The argument values for this specialization. */
  val arguments: List<FirArgument>,

  /** A string like `Decaf_Oat` with each argument value named. */
  val name: String,
) {
  val isDefault: Boolean = arguments.all { it.isDefault }
}

internal fun specializations(
  session: FirSession,
  function: FirFunctionSymbol<*>,
): List<FirSpecialization> {
  val parameters = function.valueParameterSymbols
  val parameterArguments = parameters.map { parameter ->
    allPossibleArguments(session, parameter)
  }

  val specializations = parameterArguments.cartesianProduct().map { arguments ->
    FirSpecialization(
      arguments = arguments,
      name = arguments.joinToString(separator = "_", transform = FirArgument::name),
    )
  }

  // If all elements already have distinct, short-enough names, we're done.
  if (
    specializations.distinctBy { it.name }.size == specializations.size &&
    specializations.all { it.name.length < NAME_MAX_LENGTH }
  ) {
    return specializations
  }

  // Otherwise, prefix each with its index.
  return specializations.mapIndexed { index, specialization ->
    FirSpecialization(
      arguments = specialization.arguments,
      name = "${index}_${specialization.name}".take(NAME_MAX_LENGTH),
    )
  }
}
