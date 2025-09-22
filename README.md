Burst
=====

Burst is a Kotlin compiler plug-in for more capable tests. It supports all Kotlin platforms and
works great in multiplatform projects.

 * `@Burst` is used to parameterize unit tests. It is similar to [TestParameterInjector] in
   capability.

 * `@InterceptTest` is used to set up and tear down unit tests. It is similar to [JUnit Rules] in
   capability.


@Burst
------

Annotate your test class with `@Burst`.

Declare a parameter in your test's constructor that calls `burstValues()` as its default.

```kotlin
@Burst
class DrinkSodaTest(
  val soda: String = burstValues("Pepsi", "Coke"),
) {
  ...
}
```

Burst will specialize the test class for each argument to `burstValues()`. The first value is used
when you run the test in the IDE.

### Parameterize Functions

Burst can specialize individual test functions:

```kotlin
@Test
fun drinkSoda(
  soda: String = burstValues("Pepsi", "Coke"),
) {
  ...
}
```

### Booleans and Enums

If your parameter is a boolean or an enum, you don't need to call `burstValues()`. Burst will test
all values. Specify a default value to use that when you launch the test from the IDE.

```kotlin
@Burst
class DrinkSodaTest(
  val ice: Boolean = true,
) {
  ...
}
```

```kotlin
enum class Distribution {
  Fountain, Can, Bottle
}

@Burst
class DrinkSodaTest(
  val distribution: Distribution = Distribution.Can,
) {
  ...
}
```

If the parameter is nullable, Burst will also test with null.

### Multiple Parameters

Use multiple parameters to test all variations.

```kotlin
@Test
fun drinkSoda(
  soda: String = burstValues("Pepsi", "Coke"),
  ice: Boolean,
  distribution: Distribution,
) {
  ...
}
```

The test will be specialized for each combination of arguments.

 * `drinkSoda("Pepsi", true, Distribution.Fountain)`
 * `drinkSoda("Pepsi", true, Distribution.Can)`
 * `drinkSoda("Pepsi", true, Distribution.Bottle)`
 * `drinkSoda("Pepsi", false, Distribution.Fountain)`
 * `drinkSoda("Pepsi", false, Distribution.Can)`
 * `drinkSoda("Pepsi", false, Distribution.Bottle)`
 * `drinkSoda("Coke", true, Distribution.Fountain)`
 * `drinkSoda("Coke", true, Distribution.Can)`
 * `drinkSoda("Coke", true, Distribution.Bottle)`
 * `drinkSoda("Coke", false, Distribution.Fountain)`
 * `drinkSoda("Coke", false, Distribution.Can)`
 * `drinkSoda("Coke", false, Distribution.Bottle)`

@InterceptTest
--------------

Implement the `TestInterceptor` interface. Your `intercept` function should call `testFunction` to
run the subject test function.

```kotlin
class RepeatInterceptor(
  private val attemptCount: Int,
) : TestInterceptor {
  override fun intercept(testFunction: TestFunction) {
    for (i in 0 until attemptCount) {
      println("running $testFunction attempt $i")
      testFunction()
    }
  }
}
```

Next, declare a property for your interceptor in your test class and annotate it `@InterceptTest`:

```kotlin
class DrinkSodaTest {
  @InterceptTest
  val repeatInterceptor = RepeatInterceptor(3)

  @Test
  fun drinkSoda() {
    println("drinking a Pepsi")
  }
}
```

When you execute this test, it is intercepted:

```
running DrinkSodaTest.drinkSoda attempt 0
drinking a Pepsi
running DrinkSodaTest.drinkSoda attempt 1
drinking a Pepsi
running DrinkSodaTest.drinkSoda attempt 2
drinking a Pepsi
```

### BeforeTest and AfterTest

If your test has these functions, the interceptor intercepts them. Here’s such a test:

```kotlin
class DrinkSodaTest {
  @InterceptTest
  val loggingInterceptor = TestInterceptor { testFunction ->
    println("intercepting $testFunction")
    testFunction()
    println("intercepted $testFunction")
  }

  @BeforeTest
  fun beforeTest() {
    println("getting ready")
  }

  @AfterTest
  fun afterTest() {
    println("cleaning up")
  }

  @Test
  fun drinkSoda() {
    println("drinking a Pepsi")
  }
}
```

And here’s its output:

```
intercepting DrinkSodaTest.drinkSoda
getting ready
drinking a Pepsi
cleaning up
intercepted DrinkSodaTest.drinkSoda
```

### Coroutines

If your tests use [kotlinx-coroutines-test], you must use `CoroutineTestInterceptor` instead of
`TestInterceptor`. Its intercept function suspends:

```kotlin
class DrinkSodaTest {
  @InterceptTest
  val loggingInterceptor = CoroutineTestInterceptor { testFunction ->
    println("intercepting $testFunction")
    testFunction()
  }

  @Test
  fun drinkSoda() = runTest {
    println("drinking a Pepsi")
  }
}
```

The `CoroutineTestFunction` has the `TestScope` property so interceptors have the same capabilities
as tests.

You’ll also need this Gradle dependency:

```kotlin
dependencies {
  testImplementation("app.cash.burst:burst-coroutines:2.10.1")
  ...
}
```

### Features and Limitations

You can have multiple test interceptors in each class. They are executed in declaration order.

You can use test interceptors and inheritance together. The superclass interceptors are executed
first.

You can use `try/catch/finally` to execute code when tests fail.

Intercepted test functions must be `final`. Mixing `@InterceptTest` with non-final test functions
will cause a compilation error.

You cannot mix and match `CoroutinesTestInterceptor` and `TestInterceptor` in the same test. You
cannot use `runTest()` with `TestInterceptor`, and you must use it with `CoroutinesTestInterceptor`.


Gradle Setup
------------

Add Burst to your root project's buildscript dependencies:

```kotlin
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("app.cash.burst:burst-gradle-plugin:2.10.1")
  }
}
```

Then add the plugin to a module's `build.gradle`:

```kotlin
plugins {
  id("app.cash.burst")
  ...
}
```

### Compatibility

Since Kotlin compiler plugins are an unstable API, certain versions of Burst only work with
certain versions of Kotlin.

| Kotlin          | Burst         |
|-----------------|---------------|
| 2.2.20          | 2.9.0         |
| 2.2.0 - 2.2.10  | 2.6.0 - 2.8.1 |
| 2.1.20          | 2.5.0         |
| 2.1.0           | 2.2.0 - 2.4.0 |
| 2.0.20 - 2.0.21 | 0.1.0 - 2.1.0 |

Kotlin versions newer than those listed may be supported but have not been tested.



License
-------

    Copyright (C) 2024 Cash App

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[JUnit Rules]: https://junit.org/junit4/javadoc/4.12/org/junit/Rule.html
[TestParameterInjector]: https://github.com/google/TestParameterInjector
[kotlinx-coroutines-test]: https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/
