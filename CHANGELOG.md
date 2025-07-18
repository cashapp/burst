# Change Log

## [Unreleased]
[Unreleased]: https://github.com/cashapp/burst/compare/2.6.0...HEAD

Nothing yet!


## [2.6.0] *(2025-06-23)*
[2.6.0]: https://github.com/cashapp/burst/releases/tag/2.6.0

**Added**

* Support Kotlin 2.2.0

**Changed**

* In-development snapshots are now published to the Central Portal Snapshots repository at https://central.sonatype.com/repository/maven-snapshots/.


## [2.5.0] *(2025-03-20)*
[2.5.0]: https://github.com/cashapp/burst/releases/tag/2.5.0

**Added**

* Support Kotlin 2.1.20

**Fixed**

 * Don't fail for constructors that have value class parameters. Unfortunately we can't run such tests from the IDE.


## [2.4.0] *(2025-01-24)*
[2.4.0]: https://github.com/cashapp/burst/releases/tag/2.4.0

**Fixed**

 * Don't fail the compile when a `@Burst` class is subclassed. We had a bug where overridden tests were incorrectly being processed by Burst.


## [2.3.0] *(2025-01-22)*
[2.3.0]: https://github.com/cashapp/burst/releases/tag/2.3.0

**Fixed**

 * Include Burst’s generated functions in Kotlin metadata on Kotlin/Native.


## [2.2.0] *(2024-11-06)*
[2.2.0]: https://github.com/cashapp/burst/releases/tag/2.2.0

**Added**

 * Support Kotlin 2.1.0

**Fixed**

 * Match enum values by name, to ensure they can be matched across compilation units.


## [2.1.0] *(2024-11-06)*
[2.1.0]: https://github.com/cashapp/burst/releases/tag/2.1.0

**Added**

 * Basic support for JUnit 5. Burst doesn't support JUnit 5 tests that populate parameters from extensions.


## [2.0.0] *(2024-10-30)*
[2.0.0]: https://github.com/cashapp/burst/releases/tag/2.0.0

**Added**

 * Add support for booleans, and nullable booleans.
 * Add support for nullable enums.

**Fixed**

 * Don't crash if burstValues() has only one argument


## [0.7.0] *(2024-10-28)*
[0.7.0]: https://github.com/cashapp/burst/releases/tag/0.7.0

**Added**

 * New: Use `burstValues()` for test parameters of any type.

**Fixed**

 * Do not attempt to parameterize constructors of `abstract` test classes.


## [0.6.0] *(2024-10-23)*
[0.6.0]: https://github.com/cashapp/burst/releases/tag/0.6.0

**Added**

 * New: Use default parameter values to configure which specialization runs in the IDE.


## [0.5.0] *(2024-10-17)*
[0.5.0]: https://github.com/cashapp/burst/releases/tag/0.5.0

**Fixed**

* Fix: Apply specializations for Kotlin/JS and Kotlin/Native. We had bugs that caused our compiler
  plug-in to skip non-JVM platforms.


## [0.4.0] *(2024-10-16)*
[0.4.0]: https://github.com/cashapp/burst/releases/tag/0.4.0

**Added**

 * New: Require JDK 1.8+.
 * New: Run the first specialization when launching from the IDE.


## [0.3.0] *(2024-10-15)*
[0.3.0]: https://github.com/cashapp/burst/releases/tag/0.3.0

**Fixed**

 * Fix: Don't generate invalid bytecode by attaching a test class constructor to its enclosing file.


## [0.2.0] *(2024-10-10)*
[0.2.0]: https://github.com/cashapp/burst/releases/tag/0.2.0

**Added**

 * New: Support both class constructor parameters and test function parameters.


## [0.1.0] *(2024-10-08)*
[0.1.0]: https://github.com/cashapp/burst/releases/tag/0.1.0

Initial release. We're rebooting the [Burst] project that released 1.0 ten years ago.

[Burst]: https://github.com/square/burst
