# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Manual Play internal-testing AAB build workflow with a dedicated upload key.

### Changed

- Upgraded to Gradle 9.6.1, AGP 9.5.0, Kotlin 2.4.21, Compose BOM 2026.06.00, coroutines 1.11.0, and serialization 1.12.0.
- Hardened CI workflows by pinning GitHub Actions to commit SHAs.
- Documented the protected `main` branch and shared documentation submodule workflow.

## [0.1.0-beta.1] - 2026-10-29

### Added

- First public GitHub beta release.
- Beta build type with application ID `com.fossscout.app.beta`.
- Signed GitHub prerelease workflow for `v*-beta*` tags.

[Unreleased]: https://github.com/FossScout/FossScout/compare/v0.1.0-beta.1...HEAD
[0.1.0-beta.1]: https://github.com/FossScout/FossScout/releases/tag/v0.1.0-beta.1
