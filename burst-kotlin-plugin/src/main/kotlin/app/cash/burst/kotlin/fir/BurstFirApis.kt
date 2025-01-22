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

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object BurstGeneratedDeclarationKey : GeneratedDeclarationKey() {
  override fun toString() = "BurstKey"
}

fun Name.toJavaSetter(): Name {
  val name = asString()
  return Name.identifier("set" + name[0].uppercase() + name.substring(1))
}

val atBurst = ClassId.topLevel(FqName("app.cash.burst.Burst"))
val junitAtTest = ClassId.topLevel(FqName("org.junit.Test"))
val junit5AtTest = ClassId.topLevel(FqName("org.junit.jupiter.api.Test"))
val kotlinTestAtTest = ClassId.topLevel(FqName("kotlin.test.Test"))
