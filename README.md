Burst
=====

Burst is a unit testing library for parameterizing unit tests.

It is similar to [TestParameterInjector] in usage, but Burst is implemented as a Kotlin compiler
plug-in. Burst supports all Kotlin platforms and works great in multiplatform projects.


Usage
-----

Annotate your test class with `@Burst`.

Declare a parameter in your test constructor that uses `burstValues()` for its tested values:

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

### Enum Parameters

If your parameter is an enum type, you don't need to call `burstValues()`. Burst will test each
value of that enum.

```kotlin
enum class Distribution {
  Fountain, Can, Bottle
}
```

```kotlin
@Burst
class DrinkSodaTest(
  val distribution: Distribution,
) {
  ...
}
```

If you specify a default value for the enum, Burst will use that when running in the IDE.

```kotlin
@Burst
class DrinkSodaTest(
  val distribution: Distribution = Distribution.Can,
) {
  ...
}
```

### Multiple Parameters

Use multiple parameters to test all variations.

```kotlin
@Test
fun drinkSoda(
  soda: String = burstValues("Pepsi", "Coke"),
  distribution: Distribution,
) {
  ...
}
```

The test will be specialized for each combination of arguments.

 * `drinkSoda("Pepsi", Distribution.Fountain)`
 * `drinkSoda("Pepsi", Distribution.Can)`
 * `drinkSoda("Pepsi", Distribution.Bottle)`
 * `drinkSoda("Coke", Distribution.Fountain)`
 * `drinkSoda("Coke", Distribution.Can)`
 * `drinkSoda("Coke", Distribution.Bottle)`

Gradle Setup
------------

Add Burst to your root project's buildscript dependencies:

```kotlin
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("app.cash.burst:burst-gradle-plugin:0.6.0")
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
