# CI and Release Delivery

This is the maintainer runbook and status tracker for CI, signing, beta
releases, and store delivery.

Last updated: **2026-10-28**

## Status

| Phase | Status | Notes |
| --- | --- | --- |
| CI checks | **In progress** | Workflow added on `ci/github-actions`; pending PR/merge. |
| Android 16 / API 36 | **Planned** | Play requires API 36 for new apps and updates; current target is 35. |
| Versioning and signing | **Planned** | Release builds are currently unsigned and version values are hardcoded. |
| GitHub beta release | **Planned** | Recommended first beta channel; needs a release keystore and secrets. |
| Play internal testing | **Blocked** | Needs a Play Console account, app declarations, and a service account. |
| F-Droid | **Not started** | Submit after a stable release; F-Droid builds and signs from source. |

## Baseline

- Distribution flavors: `playstore` (`com.fossscout.app`) and `fdroid`
  (`com.fossscout.app.fdroid`).
- Current `versionCode` / `versionName`: `1` / `0.1.0`.
- Current `compileSdk` / `targetSdk`: `35` / `35`.
- Current AGP: `8.7.3`; it supports up to API 35.
- Current Gradle wrapper: `8.11.1`.
- There are no release tags and no signing configuration.
- JVM unit tests are available; there are no instrumented tests yet.

## Phase 1 — CI checks

The `.github/workflows/ci.yml` workflow runs on pull requests, pushes to
`main`, and manual dispatch. It:

- uses JDK 17 and the Android SDK;
- runs `./gradlew test lint assemblePlaystoreDebug assembleFdroidDebug`;
- uploads test/lint reports and debug APKs as workflow artifacts.

Make `CI / verify` a required branch-protection check after the workflow has
passed once.

This phase needs no repository secrets.

## Phase 2 — Android 16 / API 36

Google Play requires new apps and app updates to target Android 16 (API 36)
from **31 August 2026**. The extension deadline is **1 November 2026**.

Required changes:

- `compileSdk = 36`
- `targetSdk = 36`
- AGP **8.9.1 or newer**
- Build Tools **36.0.0**

The Gradle wrapper is already new enough for AGP 8.9. The app modules currently
use `compileSdk = 35`; the app module is the one that must target 36. Align
library modules if a dependency requires it.

## Phase 3 — Versioning and signing

### Versioning

Release builds accept Gradle properties:

```sh
./gradlew assemblePlaystoreRelease \
  -PversionCode=20001 \
  -PversionName=0.2.0-beta.1
```

Keep `versionCode` monotonic. Never reuse a Play version code.

### Signing

Generate the release keystore outside the repository and back it up offline.
Do not commit the keystore or passwords.

Required GitHub secrets:

| Secret | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore. |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password. |
| `ANDROID_KEY_ALIAS` | Key alias. |
| `ANDROID_KEY_PASSWORD` | Key password. |

Put these in a protected GitHub Environment named `release`. The release job
must not run for pull requests.

The local signing configuration reads `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
Without them, release builds remain unsigned so normal CI still works.

## Phase 4 — GitHub beta release

This is the recommended first beta channel because it needs no store review.

- Build the `playstore` flavor with the `beta` build type.
- The beta application ID is `com.fossscout.app.beta`, so it can coexist with
  Play and F-Droid builds without signature/update conflicts.
- The release workflow is manual (`workflow_dispatch`) and requires
  `versionName` and `versionCode`.
- It signs the APK, verifies it with `apksigner`, generates a SHA-256 checksum,
  and creates a GitHub prerelease at tag `v<versionName>`.
- Testers sideload the APK. GitHub releases are public; use Play internal
  testing for a private beta.

Do not attach an AAB to a GitHub release; testers cannot install it.

## Phase 5 — Play internal testing

Prerequisites:

- Google Play developer account.
- App listing and declarations: privacy policy, data safety, content rating,
  target audience, ads, app access, and store listing assets.
- Play App Signing.
- First AAB uploaded manually.
- Google Cloud service account with Play Developer API access.
- Secret `PLAY_SERVICE_ACCOUNT_JSON`.

Recommended automation: upload `bundlePlaystoreRelease` to the `internal` track
with the Play Developer API. Promote to closed testing, then production, only
after the app and declarations are ready. New personal developer accounts may
need a closed test with testers before production access.

## Phase 6 — F-Droid

F-Droid builds and signs from source. Submit metadata to the separate
`fdroiddata` repository after a stable release.

- Use the `fdroid` flavor and the existing application ID
  `com.fossscout.app.fdroid`.
- Include the build recipe, tag, version code/name, license, source URL, and
  issue tracker.
- Do not distribute a self-signed APK under the F-Droid application ID as an
  official F-Droid build; signatures will conflict.
- Check current F-Droid policy for broad package visibility before submission.

## Release checklist

- [ ] CI is green on `main`.
- [ ] Version code/name are set and monotonic.
- [ ] Keystore and secrets are available in the protected `release` environment.
- [ ] APK signature and SHA-256 checksum are verified.
- [ ] Release notes are written.
- [ ] Beta is tested on a real device.
- [ ] Play declarations and track are ready before uploading an AAB.
- [ ] F-Droid metadata is updated separately for stable releases.
