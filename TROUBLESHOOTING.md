Burst Troubleshooting
=====================

NoSuchMethodError
-----------------

```
java.lang.NoSuchMethodError: 'void com.example.AbstractFooTest.intercept(app.cash.burst.TestFunction)'
    at com.example.StandardFooTest.intercept(StandardFooTest.kt:100)
```

You’ll get this if the Burst Gradle Plugin wasn’t applied to a class that needs it. Update the
corresponding `build.gradle.kts` to apply it:

```kotlin
plugins {
  id("app.cash.burst")
  ...
}
```
