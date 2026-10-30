# Build and CI

## Outcome or responsibility

Own the Gradle multi-module scaffold and the GitHub Actions workflow that is the only build authority: lint, static analysis, unit tests, and assembling a debug APK on every push and pull request.

## Current verified status

**Status:** Implemented

Verified by a green CI run of `.github/workflows/ci.yml` on the slice 1 branch: all five verification steps pass and the debug APK artifact is uploaded.

## Architecture dependencies

- [Stack and versions](../architecture.md#stack-and-versions)
- [Module structure and dependency direction](../architecture.md#module-structure-and-dependency-direction)
- [Verification boundaries](../architecture.md#verification-boundaries)

## Local rules and implications

### Stack and versions

The version catalog (`gradle/libs.versions.toml`) is the single place versions are declared. CI re-verifies the catalog against registries by resolving dependencies; a resolution failure is a red build. The Gradle wrapper JAR is generated in CI, never hand-written.

### Module structure and dependency direction

The scaffold creates the modules listed in the architecture as they are needed, starting with `:app` and `:domain`. `:domain` is a pure Kotlin/JVM module so it cannot accidentally import Android APIs.

### Verification boundaries

The workflow runs ktlint, detekt, Android Lint, unit tests, and `assembleDebug`. Each runs as a distinct step so failures are attributable. Gradle runs on JDK 21 (Robolectric's SDK 36 sandbox requires it) while bytecode targets Java 17. Robolectric JVM tests need `--add-opens` for `java.base` internals, configured in `app/build.gradle.kts`. `:domain` uses JUnit 5; Robolectric tests in `:app` and the unit tests in `:engine:media3` and `:engine:captions` use JUnit 4. The app packages only the `arm64-v8a` and `armeabi-v7a` ABIs because the speech recognizer's x86_64 library is not 16 KB aligned.

## Related blueprints

### Required

None

### Impact checks

None

## Relevant implementation and tests

- `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `.editorconfig`, `config/detekt/detekt.yml` — build configuration
- `.github/workflows/ci.yml` — CI workflow
- `app/src/test/kotlin/com/framecraft/editor/MainActivityTest.kt` — launch test

## Acceptance or verification criteria

- [x] CI workflow triggers on push to any branch and on pull requests to `main`.
- [x] ktlint, detekt, Android Lint, unit tests, and `assembleDebug` each pass as separate steps.
- [x] The debug APK is uploaded as a workflow artifact.
- [x] The app launches to the empty home screen (verified by a Robolectric Compose UI test).

## Remaining gaps and unknowns

- Instrumented (emulator) tests in CI are not planned yet; decide when the engine slice needs them.
- Release signing is out of scope until a release slice exists.
