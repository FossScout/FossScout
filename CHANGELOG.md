# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Project detail screens with description, license, tags, stars, website, and save action.

## [0.1.0-beta.4] - 2026-11-06

### Added

- A curated proprietary-app evidence catalog with vendor terms for common apps, starting with Slack, Facebook, WhatsApp, Instagram, and other widely installed services.

### Fixed

- Play builds can now find user-facing installed apps such as Slack and Facebook during manual checks.

### Changed

- Discover search now matches project descriptions, supports multiple keywords, and accepts `#tag` prefixes.
- Updated the Gradle wrapper to 9.8.2.

## [0.1.0] - 2026-11-05

First stable F-Droid tag. F-Droid publication is pending; the GitHub beta
channel remains at `0.1.0-beta.3`.

### Added

- F-Droid listing metadata and submission-ready build metadata for `com.fossscout.app.fdroid`.

### Changed

- Updated datastore to 1.3.0, core-ktx to 1.19.1, and zxing to 3.5.4.

## [0.1.0-beta.3] - 2026-11-03

### Added

- Tracked agent guidelines, the screenshot checklist, and the headless Compose screenshot set, with a README gallery.
- Friendly installer names and a preinstalled-system-app fallback in inventory and replacement guidance.
- About-screen Liberapay support link using the verified team profile.
- Google Play listing copy, graphics, privacy-policy draft, and declaration drafts.
- Manual CI emulator smoke test covering both debug builds, core UI flows, installer fallbacks, Play visibility, and the F-Droid full scan.

### Changed

- Raised `compileSdk` to 37, upgraded Compose BOM to 2026.09.00, and updated CI, release, and Play workflows to install `platforms;android-37.0`.

## [0.1.0-beta.2] - 2026-11-01

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

[Unreleased]: https://github.com/FossScout/FossScout/compare/v0.1.0-beta.4...HEAD
[0.1.0]: https://github.com/FossScout/FossScout/tree/v0.1.0
[0.1.0-beta.4]: https://github.com/FossScout/FossScout/releases/tag/v0.1.0-beta.4
[0.1.0-beta.3]: https://github.com/FossScout/FossScout/releases/tag/v0.1.0-beta.3
[0.1.0-beta.2]: https://github.com/FossScout/FossScout/releases/tag/v0.1.0-beta.2
[0.1.0-beta.1]: https://github.com/FossScout/FossScout/releases/tag/v0.1.0-beta.1
