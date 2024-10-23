package app.cash.burst.kotlin

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Represents a package name without an associated class. */
@JvmInline
value class FqPackageName(val fqName: FqName)

fun FqPackageName(name: String) = FqPackageName(FqName(name))

fun FqPackageName.classId(name: String) = ClassId(fqName, Name.identifier(name))
