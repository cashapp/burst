package app.cash.burst.kotlin.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.classId

internal class FirArgument(
  /** True if this argument matches the default parameter value. */
  val isDefault: Boolean,

  /** A string that's safe to use in a declaration name. */
  val name: String,
)

internal fun allPossibleArguments(
  session: FirSession,
  parameter: FirValueParameterSymbol,
): List<FirArgument> {
  // It's a call to burstValues().
  val burstValuesCall = parameter.resolvedDefaultValue as? FirFunctionCall
  if (burstValuesCall?.calleeReference?.name?.identifier == "app.cash.burst.burstValues") {
    return burstValuesArguments(parameter, burstValuesCall)
  }

  val classId = parameter.resolvedReturnType.classId

  // It's an enum.
  if (classId != null) {
    val classSymbol = session.findClassSymbol(classId)
    if (classSymbol?.isEnumClass == true) {
      return enumValueArguments(classSymbol, parameter)
    }
  }

  // It's a boolean.
  if (classId == session.builtinTypes.booleanType.id) {
    return booleanArguments(parameter)
  }

  // It's some other type. This will fail elsewhere.
  return listOf()
}

internal fun burstValuesArguments(
  parameter: FirValueParameterSymbol,
  burstValuesCall: FirFunctionCall
): List<FirArgument> {
  return listOf()
}

private fun enumValueArguments(
  classSymbol: FirClassSymbol<*>,
  parameter: FirValueParameterSymbol
): List<FirArgument> {
  // TODO: default value
  // TODO: add null argument

  val enumEntries = classSymbol.declarationSymbols.filterIsInstance<FirEnumEntrySymbol>()
  return buildList {
    for (enumEntry in enumEntries) {
      add(FirArgument(false, enumEntry.name.identifier))
    }
  }
}

private fun booleanArguments(
  parameter: FirValueParameterSymbol
): List<FirArgument> {
  // TODO: default value
  // TODO: null
  return listOf(
    FirArgument(false, "false"),
    FirArgument(false, "true"),
  )
}
