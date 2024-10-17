# Change Log

## Unreleased

## Version 0.5.0 *(2024-10-17)*

 * Fix: Apply specializations for Kotlin/JS and Kotlin/Native. We had bugs that caused our compiler
   plug-in to skip non-JVM platforms.


## Version 0.4.0 *(2024-10-16)*

 * New: Require JDK 1.8+.
 * New: Run the first specialization when launching from the IDE.


## Version 0.3.0 *(2024-10-15)*

 * Fix: Don't generate invalid bytecode by attaching a test class constructor to its enclosing file.


## Version 0.3.0 *(2024-10-10)*

 * New: Support both class constructor parameters and test function parameters.


## Version 0.1.0 *(2024-10-08)*

Initial release. We're rebooting the [Burst] project that released 1.0 ten years ago.

[Burst]: https://github.com/square/burst
