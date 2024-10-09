/*
 * Copyright (C) 2024 Cash App
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
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

fun readTestSuite(xmlFile: File): TestSuite {
  val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
  val document = builder.parse(xmlFile)
  return document.documentElement.toTestSuite()
}

internal fun Element.toTestSuite(): TestSuite {
  val testCases = mutableListOf<TestCase>()
  for (i in 0 until childNodes.length) {
    val item = childNodes.item(i)
    if (item !is Element || item.tagName != "testcase") continue
    testCases += item.toTestCase()
  }

  return TestSuite(
    name = getAttribute("name"),
    testCases = testCases,
  )
}

internal fun Element.toTestCase(): TestCase {
  var skipped = false
  for (i in 0 until childNodes.length) {
    val item = childNodes.item(i)
    if (item is Element && item.tagName == "skipped") {
      skipped = true
    }
  }

  return TestCase(
    name = getAttribute("name"),
    skipped = skipped,
  )
}

class TestSuite(
  val name: String,
  val testCases: List<TestCase>,
)

class TestCase(
  val name: String,
  val skipped: Boolean,
)
