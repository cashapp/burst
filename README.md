Burst
=====

Burst is a unit testing library that uses enums to parameterize unit tests.

It is similar to [TestParameterInjector] in usage, but Burst is implemented as a Kotlin compiler
plug-in. Burst supports all Kotlin platforms and works great in multiplatform projects.


Usage
-----

Define an enum for the property you wish to vary.

```kotlin
enum class Soda {
  Pepsi, Coke
}
```

The enum can be simple as above, or contain data and methods specific to what you are testing.

```kotlin
enum class Collections {
  MutableSetOf {
    override fun <T> create(): MutableCollection<T> {
      return mutableSetOf()
    }
  },
  MutableListOf {
    override fun <T> create(): MutableCollection<T> {
      return mutableListOf()
    }
  },
  NewArrayDeque {
    override fun <T> create(): MutableCollection<T> {
      return ArrayDeque()
    }
  };

  abstract fun <T> create(): MutableCollection<T>?
}
```

Annotate your test class with `@Burst`, and accept an enum as a constructor parameter:

```kotlin
@Burst
class DrinkSodaTest(
  val soda: Soda,
) {
  ...
}
```

Burst will specialize the test class for each value in the enum.

Burst can also specialize individual test functions:

```kotlin
@Test
fun drinkFavoriteSodas(soda: Soda) {
  ...
}
```

Use multiple enums for the combination of their variations.

```kotlin
@Test
fun collectSodas(soda: Soda, collectionsFactory: CollectionFactory) {
  ...
}
```

The test will be specialized for each combination of arguments.

 * `collectSodas(Soda.Pepsi, CollectionFactory.MutableSetOf)`
 * `collectSodas(Soda.Pepsi, CollectionFactory.MutableListOf)`
 * `collectSodas(Soda.Pepsi, CollectionFactory.NewArrayDeque)`
 * `collectSodas(Soda.Coke, CollectionFactory.MutableSetOf)`
 * `collectSodas(Soda.Coke, CollectionFactory.MutableListOf)`
 * `collectSodas(Soda.Coke, CollectionFactory.NewArrayDeque)`

Gradle Setup
------------

Add Burst to your root project's buildscript dependencies:

```kotlin
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("app.cash.burst:burst-gradle-plugin:0.5.0")
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
