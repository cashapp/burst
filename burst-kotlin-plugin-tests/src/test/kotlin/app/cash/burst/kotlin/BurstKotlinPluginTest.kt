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
package app.cash.burst.kotlin

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class BurstKotlinPluginTest {
  @Test
  fun functionParameters() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(espresso: Espresso, dairy: Dairy) {
            log += "running ${'$'}espresso ${'$'}dairy"
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        enum class Dairy { None, Milk, Oat }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val testClass = result.classLoader.loadClass("CoffeeTest")

    // Burst doesn't make the class non-final as it has no reason to.
    assertThat(Modifier.isFinal(testClass.modifiers)).isTrue()

    val adapterInstance = testClass.constructors.single().newInstance()
    val log = testClass.getMethod("getLog").invoke(adapterInstance) as MutableList<*>

    // Burst drops @Test from the original test function.
    val originalTest = testClass.methods.single { it.name == "test" && it.parameterCount == 2 }
    assertThat(originalTest.isAnnotationPresent(Test::class.java)).isFalse()

    // Burst adds a specialization for each combination of parameters.
    val sampleSpecialization = testClass.getMethod("test_Regular_Milk")
    assertThat(sampleSpecialization.isAnnotationPresent(Test::class.java)).isTrue()
    sampleSpecialization.invoke(adapterInstance)
    assertThat(log).containsExactly("running Regular Milk")
    log.clear()

    // Burst doesn't add a no-parameter function because there's no default specialization.
    assertFailsWith<NoSuchMethodException> {
      testClass.getMethod("test")
    }
  }

  @Test
  fun unexpectedClassArgumentType() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class CoffeeTest(espresso: String) {
          @Test
          fun test() {
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "CoffeeTest.kt:5:18 " +
        "@Burst parameter must be a boolean, an enum, or have a burstValues() default value",
    )
  }

  @Test
  fun unexpectedFunctionArgumentType() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          @Test
          fun test(espresso: String) {
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "CoffeeTest.kt:7:12 " +
        "@Burst parameter must be a boolean, an enum, or have a burstValues() default value",
    )
  }

  @Test
  fun unexpectedDefaultArgumentValue() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        val defaultEspresso = Espresso.Regular

        @Burst
        class CoffeeTest {
          @Test
          fun test(espresso: Espresso = defaultEspresso) {
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "CoffeeTest.kt:9:12 " +
        "@Burst parameter default must be burstValues(), a constant, null, or absent",
    )
  }

  @Test
  fun constructorParameters() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        @Burst
        class CoffeeTest(
          private val espresso: Espresso,
          private val dairy: Dairy,
        ) {
          val log = mutableListOf<String>()

          @BeforeTest
          fun setUp() {
            log += "set up ${'$'}espresso ${'$'}dairy"
          }

          @Test
          fun test() {
            log += "running ${'$'}espresso ${'$'}dairy"
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        enum class Dairy { None, Milk, Oat }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")

    // Burst opens the class because it needs to subclass it.
    assertThat(Modifier.isFinal(baseClass.modifiers)).isFalse()

    // Burst doesn't add a no-arg constructor because there's no default specialization.
    assertFailsWith<NoSuchMethodException> {
      baseClass.getConstructor()
    }

    // It generates a subclass for each specialization.
    val sampleClass = result.classLoader.loadClass("CoffeeTest_Regular_Milk")
    val sampleConstructor = sampleClass.getConstructor()
    val sampleInstance = sampleConstructor.newInstance()
    val sampleLog = sampleClass.getMethod("getLog")
      .invoke(sampleInstance) as MutableList<*>
    sampleClass.getMethod("setUp").invoke(sampleInstance)
    sampleClass.getMethod("test").invoke(sampleInstance)
    assertThat(sampleLog).containsExactly(
      "set up Regular Milk",
      "running Regular Milk",
    )
    sampleLog.clear()
  }

  @Test
  fun defaultArgumentsHonored() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.BeforeTest
        import kotlin.test.Test

        @Burst
        class CoffeeTest(
          private val espresso: Espresso = Espresso.Regular,
        ) {
          val log = mutableListOf<String>()

          @BeforeTest
          fun setUp() {
            log += "set up ${'$'}espresso"
          }

          @Test
          fun test(dairy: Dairy = Dairy.Milk) {
            log += "running ${'$'}espresso ${'$'}dairy"
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        enum class Dairy { None, Milk, Oat }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseConstructor = baseClass.constructors.single { it.parameterCount == 0 }
    val baseInstance = baseConstructor.newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    // The setUp function gets the default parameter value.
    baseClass.getMethod("setUp").invoke(baseInstance)
    assertThat(baseLog).containsExactly("set up Regular")
    baseLog.clear()

    // The test function gets its default parameter value.
    baseClass.getMethod("test").invoke(baseInstance)
    assertThat(baseLog).containsExactly("running Regular Milk")
    baseLog.clear()

    // The default specialization's subclass is not generated.
    assertFailsWith<ClassNotFoundException> {
      result.classLoader.loadClass("CoffeeTest_Regular")
    }

    // Other subclasses are available.
    result.classLoader.loadClass("CoffeeTest_Double")

    // The default test function is also not generated.
    assertFailsWith<NoSuchMethodException> {
      baseClass.getMethod("test_Milk")
    }

    // Other test functions are available.
    baseClass.getMethod("test_Oat")
  }

  @Test
  fun burstValues() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(volume: Int = burstValues(12, 16, 20)) {
            log += "running ${'$'}volume"
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    // The test function gets its default parameter value.
    baseClass.getMethod("test").invoke(baseInstance)
    assertThat(baseLog).containsExactly("running 12")
    baseLog.clear()

    // The default test function is not generated.
    assertFailsWith<NoSuchMethodException> {
      baseClass.getMethod("test_12")
    }

    // Other test functions are available, named by the literal values.
    baseClass.getMethod("test_16").invoke(baseInstance)
    assertThat(baseLog).containsExactly("running 16")
    baseLog.clear()
  }

  @Test
  fun burstValuesJustOne() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          @Test
          fun test(volume: Int = burstValues(12)) {
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    assertThat(baseClass.testSuffixes).isEmpty()
  }

  @Test
  fun burstValuesHasReasonableSymbolName() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.math.PI
        import kotlin.math.abs
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          @Test
          fun test(
            content: Any? = burstValues(
              3, // No name is generated for the first value.
              "5".toInt(),
              "hello",
              "hello".uppercase(),
              CoffeeTest::class,
              Float.MAX_VALUE,
              PI,
              String.CASE_INSENSITIVE_ORDER,
              abs(1),
              null,
            )
          ) {
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    assertThat(baseClass.testSuffixes).containsExactlyInAnyOrder(
      "toInt",
      "hello",
      "uppercase",
      "CoffeeTest",
      // Would have preferred 'MAX_VALUE', but this is constant is inlined!
      "3_4028235E38",
      // Would have preferred 'PI', but this is constant is inlined!
      "3_141592653589793",
      "CASE_INSENSITIVE_ORDER",
      "abs",
      "null",
    )
  }

  /** Confirm that inline function declarations are assigned parents. */
  @Test
  fun burstValuesWithInlineFunctions() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(
            greeting: () -> String = burstValues(
              { "Hello" },
              { "Yo" },
            ),
            subject: () -> String = burstValues(
              { "Burst" },
              { "World" },
            ),
          ) {
            log += "${'$'}{greeting()} ${'$'}{subject()}"
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    baseClass.getMethod("test").invoke(baseInstance)
    baseClass.getMethod("test_0_1").invoke(baseInstance)
    baseClass.getMethod("test_1_0").invoke(baseInstance)
    baseClass.getMethod("test_1_1").invoke(baseInstance)
    assertThat(baseLog).containsExactly(
      "Hello Burst",
      "Hello World",
      "Yo Burst",
      "Yo World",
    )
  }

  /** Confirm that inline class declarations are assigned parents. */
  @Test
  fun burstValuesWithInlineClassDeclarations() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(
            greeting: StringFactory = burstValues(
              StringFactory { "Hello" },
              StringFactory { "Yo" },
            ),
            subject: StringFactory = burstValues(
              StringFactory { "Burst" },
              StringFactory { "World" },
            ),
          ) {
            log += "${'$'}{greeting.create()} ${'$'}{subject.create()}"
          }
        }

        fun interface StringFactory {
          fun create(): String
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    baseClass.getMethod("test").invoke(baseInstance)
    baseClass.getMethod("test_0_1").invoke(baseInstance)
    baseClass.getMethod("test_1_0").invoke(baseInstance)
    baseClass.getMethod("test_1_1").invoke(baseInstance)
    assertThat(baseLog).containsExactly(
      "Hello Burst",
      "Hello World",
      "Yo Burst",
      "Yo World",
    )
  }

  @Test
  fun burstValuesWithNameCollisions() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          @Test
          fun test(
            content: Any? = burstValues(
              3, // No name is generated for the first value.
              "1",
              1,
              1L,
              "CASE_INSENSITIVE_ORDER",
              String.CASE_INSENSITIVE_ORDER,
              true,
              "true"
            )
          ) {
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    assertThat(baseClass.testSuffixes).containsExactlyInAnyOrder(
      "1_1",
      "2_1",
      "3_1",
      "4_CASE_INSENSITIVE_ORDER",
      "5_CASE_INSENSITIVE_ORDER",
      "6_true",
      "7_true",
    )
  }

  @Test
  fun burstValuesWithOverlyLongNames() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.test.Test

        const val x8192 = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

        @Burst
        class CoffeeTest {
          @Test
          fun test(
            x1: String = burstValues("a", x8192),
            x2: String = burstValues("b", x8192),
            x3: String = burstValues("c", x8192),
            x4: String = burstValues("d", x8192),
            x5: String = burstValues("e", x8192),
            x6: String = burstValues("f", x8192),
            x7: String = burstValues("g", x8192),
            x8: String = burstValues("h", x8192),
          ) {
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    assertThat(baseClass.testSuffixes).contains("1_a_b_c_d_e_f_g_${"x".repeat(1024 - 16)}")
    assertThat(baseClass.testSuffixes).contains("255_${"x".repeat(1024 - 4)}")
  }

  @Test
  fun burstValuesReferencesEarlierParameter() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          @Test
          fun test(
            p1: String = burstValues("a", "b"),
            p2: String = burstValues("c", p1.uppercase()),
          ) {
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages).contains(
      "CoffeeTest.kt:10:5 @Burst parameter may not reference other parameters",
    )
  }

  @Test
  fun booleanParameters() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(iced: Boolean) {
            log += "running iced=${'$'}iced"
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    baseClass.getMethod("test_false").invoke(baseInstance)
    assertThat(baseLog).containsExactly("running iced=false")
    baseLog.clear()

    baseClass.getMethod("test_true").invoke(baseInstance)
    assertThat(baseLog).containsExactly("running iced=true")
    baseLog.clear()
  }

  @Test
  fun booleanDefaultValues() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun testDefaultTrue(iced: Boolean = true) {
            log += "running testDefaultTrue iced=${'$'}iced"
          }

          @Test
          fun testDefaultFalse(iced: Boolean = false) {
            log += "running testDefaultFalse iced=${'$'}iced"
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    baseClass.getMethod("testDefaultTrue").invoke(baseInstance)
    baseClass.getMethod("testDefaultTrue_false").invoke(baseInstance)
    baseClass.getMethod("testDefaultFalse").invoke(baseInstance)
    baseClass.getMethod("testDefaultFalse_true").invoke(baseInstance)
    assertThat(baseLog).containsExactly(
      "running testDefaultTrue iced=true",
      "running testDefaultTrue iced=false",
      "running testDefaultFalse iced=false",
      "running testDefaultFalse iced=true",
    )
  }

  @Test
  fun nullableEnumNoDefault() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(espresso: Espresso?) {
            log += "running ${'$'}espresso"
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    baseClass.getMethod("test_Decaf").invoke(baseInstance)
    baseClass.getMethod("test_Regular").invoke(baseInstance)
    baseClass.getMethod("test_Double").invoke(baseInstance)
    baseClass.getMethod("test_null").invoke(baseInstance)
    assertThat(baseLog).containsExactly(
      "running Decaf",
      "running Regular",
      "running Double",
      "running null",
    )
  }

  @Test
  fun nullableBooleanNoDefault() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(iced: Boolean?) {
            log += "running ${'$'}iced"
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    baseClass.getMethod("test_false").invoke(baseInstance)
    baseClass.getMethod("test_true").invoke(baseInstance)
    baseClass.getMethod("test_null").invoke(baseInstance)
    assertThat(baseLog).containsExactly(
      "running false",
      "running true",
      "running null",
    )
  }

  @Test
  fun nullableBurstValuesNotDefault() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(volume: Int? = burstValues(12, 16, 20, null)) {
            log += "running ${'$'}volume"
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    baseClass.getMethod("test").invoke(baseInstance)
    baseClass.getMethod("test_16").invoke(baseInstance)
    baseClass.getMethod("test_20").invoke(baseInstance)
    baseClass.getMethod("test_null").invoke(baseInstance)
    assertThat(baseLog).containsExactly(
      "running 12",
      "running 16",
      "running 20",
      "running null",
    )
  }

  @Test
  fun nullableEnumAsDefault() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(espresso: Espresso? = null) {
            log += "running ${'$'}espresso"
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    baseClass.getMethod("test_Decaf").invoke(baseInstance)
    baseClass.getMethod("test_Regular").invoke(baseInstance)
    baseClass.getMethod("test_Double").invoke(baseInstance)
    baseClass.getMethod("test").invoke(baseInstance)
    assertThat(baseLog).containsExactly(
      "running Decaf",
      "running Regular",
      "running Double",
      "running null",
    )
  }

  @Test
  fun nullableBooleanAsDefault() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(iced: Boolean? = null) {
            log += "running ${'$'}iced"
          }
        }

        enum class Espresso { Decaf, Regular, Double }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    baseClass.getMethod("test_false").invoke(baseInstance)
    baseClass.getMethod("test_true").invoke(baseInstance)
    baseClass.getMethod("test").invoke(baseInstance)
    assertThat(baseLog).containsExactly(
      "running false",
      "running true",
      "running null",
    )
  }

  @Test
  fun nullableBurstValuesAsDefault() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "CoffeeTest.kt",
        """
        import app.cash.burst.Burst
        import app.cash.burst.burstValues
        import kotlin.test.Test

        @Burst
        class CoffeeTest {
          val log = mutableListOf<String>()

          @Test
          fun test(volume: Int? = burstValues(null, 12, 16, 20)) {
            log += "running ${'$'}volume"
          }
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val baseClass = result.classLoader.loadClass("CoffeeTest")
    val baseInstance = baseClass.constructors.single().newInstance()
    val baseLog = baseClass.getMethod("getLog").invoke(baseInstance) as MutableList<*>

    baseClass.getMethod("test_12").invoke(baseInstance)
    baseClass.getMethod("test_16").invoke(baseInstance)
    baseClass.getMethod("test_20").invoke(baseInstance)
    baseClass.getMethod("test").invoke(baseInstance)
    assertThat(baseLog).containsExactly(
      "running 12",
      "running 16",
      "running 20",
      "running null",
    )
  }

  private val Class<*>.testSuffixes: List<String>
    get() = methods.mapNotNull {
      when {
        it.name.startsWith("test_") -> it.name.substring(5)
        else -> null
      }
    }
}

@ExperimentalCompilerApi
fun compile(
  sourceFiles: List<SourceFile>,
  plugin: CompilerPluginRegistrar = BurstCompilerPluginRegistrar(),
): JvmCompilationResult {
  return KotlinCompilation().apply {
    sources = sourceFiles
    compilerPluginRegistrars = listOf(plugin)
    inheritClassPath = true
    kotlincArguments += "-Xverify-ir=error"
    kotlincArguments += "-Xverify-ir-visibility"
  }.compile()
}

@ExperimentalCompilerApi
fun compile(
  sourceFile: SourceFile,
): JvmCompilationResult {
  return compile(listOf(sourceFile), BurstCompilerPluginRegistrar())
}
