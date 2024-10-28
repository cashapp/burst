# Change Log

## [Unreleased]
[Unreleased]: https://github.com/cashapp/burst/compare/0.6.0...HEAD

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
