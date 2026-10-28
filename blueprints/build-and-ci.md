# Build and CI

## Outcome or responsibility

Own the Gradle multi-module scaffold and the GitHub Actions workflow that is the only build authority: lint, static analysis, unit tests, and assembling a debug APK on every push and pull request.

## Current verified status

**Status:** Planned

No build files or workflow exist yet.

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

The workflow runs ktlint, detekt, Android Lint, JUnit 5 unit tests, and `assembleDebug`. Each runs as a distinct step so failures are attributable.

## Related blueprints

### Required

None

### Impact checks

None

## Relevant implementation and tests

Planned: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.github/workflows/ci.yml`, `app/`, `domain/`.

## Acceptance or verification criteria

- [ ] CI workflow triggers on push and pull request to `main`.
- [ ] ktlint, detekt, Android Lint, unit tests, and `assembleDebug` each pass as separate steps.
- [ ] The debug APK is uploaded as a workflow artifact.
- [ ] The app launches to a placeholder-free empty home screen (verified by a Compose UI test).

## Remaining gaps and unknowns

- Instrumented (emulator) tests in CI are not planned yet; decide when the engine slice needs them.
- Release signing is out of scope until a release slice exists.
