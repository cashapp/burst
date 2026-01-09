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
package app.cash.burst.gradle

import java.io.File
import kotlin.metadata.KmClass
import kotlinx.metadata.klib.KlibModuleFragmentReadStrategy
import kotlinx.metadata.klib.KlibModuleMetadata
import kotlinx.metadata.klib.KlibModuleMetadata.MetadataLibraryProvider
import org.jetbrains.kotlin.konan.file.File as KonanFile
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.ToolingSingleFileKlibResolveStrategy
import org.jetbrains.kotlin.library.components.metadata
import org.jetbrains.kotlin.util.DummyLogger

/** Decode a `.klib` file using APIs from the embeddable compiler. */
fun readKlib(klib: File): KotlinLibrary =
  ToolingSingleFileKlibResolveStrategy.resolve(KonanFile(klib.toPath()), DummyLogger)

/** Decode the metadata from a library. */
fun KotlinLibrary.moduleMetadata() =
  KlibModuleMetadata.read(asMetadataLibraryProvider(), KlibModuleFragmentReadStrategy.DEFAULT)

val KlibModuleMetadata.classes: Sequence<KmClass>
  get() = fragments.asSequence().flatMap { it.classes }

private fun KotlinLibrary.asMetadataLibraryProvider(): MetadataLibraryProvider {
  val original = this
  return object : MetadataLibraryProvider {
    override val moduleHeaderData: ByteArray
      get() = original.metadata.moduleHeaderData

    override fun packageMetadata(fqName: String, partName: String) =
      original.metadata.getPackageFragment(fqName, partName)

    override fun packageMetadataParts(fqName: String): Set<String> =
      original.metadata.getPackageFragmentNames(fqName)
  }
}
