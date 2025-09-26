# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jaunch is a native launcher for applications that run inside non-native runtimes (JVM, Python, or both). It consists of two main components:

1. **Native launcher** (C code in `src/c/`) - A minimal C executable that loads runtime libraries dynamically
2. **Configurator** (Kotlin Native code in `src/commonMain/kotlin/`) - Handles the complex configuration logic and argument processing

The launcher invokes the configurator as a subprocess, which outputs launch directives that the native launcher then executes.

## Build System Commands

**Primary build command:**
```bash
make dist
```
This builds both the C launcher and Kotlin configurator for all supported platforms and creates a complete distribution in the `dist/` folder.

**Individual build steps:**
```bash
make compile-launcher     # Build C launcher only
make compile-configurator # Build Kotlin configurator only
make compile-all          # Build both components
make demo                 # Create demo applications (requires dist)
make test                 # Run tests using prysk (requires demo)
make clean                # Clean build artifacts
```

**Testing:**
```bash
./gradlew allTests        # Run all Kotlin unit tests across platforms
./gradlew linuxX64Test    # Run unit tests for specific platform
make test                 # Run integration tests (requires demo)
```

**Unit tests:**
- Kotlin unit tests are in `src/commonTest/kotlin/` (FileTest.kt, JvmTest.kt, PlatformTest.kt)
- Use Kotlin's built-in testing framework
- Run via Gradle tasks like `allTests` or platform-specific tasks

**Integration tests:**
- Located in the `test/` directory and use the [prysk](https://prysk.readthedocs.io/) testing framework
- Install prysk with: `uv tool install prysk`
- Tests require demo apps to be built first (`make demo`)
  - Some demo assembly steps (e.g. icon injection) are not needed for tests
  - Set `JAUNCH_APPIFY_FASTER=1` to assemble demo faster by skipping those steps

## Architecture

### Core Components

- **main.kt** (`src/commonMain/kotlin/main.kt`): Entry point for the configurator that processes arguments and generates launch directives
- **jaunch.c** (`src/c/jaunch.c`): Entry point for the native launcher that loads runtime libraries
- **config.kt**: TOML configuration parsing and management
- **jvm.kt**: JVM-specific runtime configuration and argument handling
- **python.kt**: Python-specific runtime configuration and argument handling

### Configuration System

Jaunch uses TOML files for configuration:
- **common.toml**: Base configuration with general-purpose defaults
- **{app-name}.toml**: Application-specific configuration that extends common.toml
- Configuration supports includes, variable interpolation, conditional logic based on "hints" (OS, architecture, user options)

### Platform Support

The build system supports the following platforms:
- Linux: arm64 (partial), x64
- macOS arm64, x64
- Windows arm64 (partial), x64

For platform-specific code:
* C platform code is strictly partitioned into platform-specific header files, which the main `jaunch.c` includes once at the top.
  * **IMPORTANT** Do not add `#ifdef` platform cases! Use existing platform-specific header files, and `common.h` for shared function definitions.
* Kotlin platform code is organized using Kotlin Multiplatform source sets with intermediate `posixMain` for Unix-like systems.

### Key Concepts

- **Hints**: String identifiers used for conditional configuration (OS, architecture, user flags)
- **Variables**: Configuration values that can be interpolated into arguments using `${variable}` syntax
- **Directives**: Instructions from configurator to launcher (JVM, PYTHON, ERROR, ABORT)
- **Modes**: Configuration presets that can be activated to change behavior

## Development Workflow

1. Make changes to C code (`src/c/`) or Kotlin code (`src/commonMain/kotlin/`)
2. Run `make dist` to build complete distribution
3. Use `make test` to run integration tests
4. For debugging, use the `--debug` flag with any launcher to see verbose output

When working on the C code, test more quickly by running `make compile-launcher && cp dist/launcher-linux-x64 demo/hi-linux-x64 && demo/hi-linux-x64`, replacing `linux-x64` with current platform suffix and `hi` with demo app to be tested.

## Testing Strategy

**Unit Tests:**
- Test core utility functions (File operations, JVM argument parsing, platform detection)
- Located in `src/commonTest/kotlin/` using Kotlin's test framework
- Run with `./gradlew allTests` or platform-specific tasks

**Integration Tests:**
- Test complete launcher behavior using real binaries and demo applications
- Verify argument processing, runtime discovery, and launch behavior across platforms
- Each test configuration (e.g., `hi.toml`, `jy.toml`) demonstrates different use cases
- Use prysk framework for shell-based testing

When working with this codebase, focus on understanding the two-stage architecture: the minimal C launcher that delegates complex logic to the Kotlin configurator, which processes TOML configuration and user arguments to generate runtime-specific launch directives.
