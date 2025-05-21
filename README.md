Burst
=====

Burst is a library for parameterizing unit tests.

It is similar to [TestParameterInjector] in usage, but Burst is implemented as a Kotlin compiler
plug-in. Burst supports all Kotlin platforms and works great in multiplatform projects.


Usage
-----

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

Gradle Setup
------------

Add Burst to your root project's buildscript dependencies:

```kotlin
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("app.cash.burst:burst-gradle-plugin:2.5.0")
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
| 2.2.0-RC        | (unreleased)  |
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

[TestParameterInjector]: https://github.com/google/TestParameterInjector
