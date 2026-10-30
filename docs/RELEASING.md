# Releasing

## Beta channel

GitHub beta releases use the `playstoreBeta` variant:

- Application ID: `com.fossscout.app.beta`
- Version-name suffix: `-beta` unless the version name already contains `-beta`
- Capability profile: `playstore` (Play-safe feature set)
- Artifact: signed APK plus SHA-256 checksum
- Distribution: GitHub prerelease

The separate application ID allows the beta to be installed alongside a
production build.

## Required repository secrets

Add these in GitHub repository settings before the first release:

| Secret | Value |
| --- | --- |
| `KEYSTORE_FILE` | Base64-encoded release keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Release key alias |
| `KEY_PASSWORD` | Release key password |

Encode the keystore without line wrapping:

```sh
base64 -w0 release.jks
```

Keep the keystore and passwords out of the repository.

## Create a beta release

1. Merge the release changes to `main` and wait for CI to pass.
2. Create a tag using the beta version name, for example `v0.2.0-beta.1`.
3. Push the tag:

```sh
git tag v0.2.0-beta.1
git push origin v0.2.0-beta.1
```

The beta release workflow builds `assemblePlaystoreBeta`, signs the APK,
verifies the signature with `apksigner`, writes a `.sha256` file, uploads the
artifact, and creates a GitHub prerelease. Only tags containing `-beta` trigger
this workflow; stable tags are intentionally not enabled yet.

The workflow checks out the shared docs submodule. The next beta tag is the
first release that must exercise this checkout; confirm the run succeeds and
record it in `docs/ci/CI_PROGRESS.md`.

The workflow uses `GITHUB_RUN_NUMBER` as the Android version code.

## Verify the release

Before announcing the beta:

- Confirm the GitHub release is marked as a prerelease.
- Download the APK and verify the checksum.
- Confirm the package name is `com.fossscout.app.beta`.
- Install it alongside the production app and smoke-test startup, inventory,
  matching, favourites, collections, and backup.
- Confirm the release workflow run passed signature verification.

## Release history

| Version | Date | Channel | Result |
| --- | --- | --- | --- |
| [`v0.1.0-beta.1`](https://github.com/FossScout/FossScout/releases/tag/v0.1.0-beta.1) | 2026-10-29 | GitHub prerelease | Published; APK package `com.fossscout.app.beta`, version code 9, checksum and certificate verified. |

## Later channels

Play internal testing and F-Droid are separate follow-up stages. Do not publish
the beta APK to Play or F-Droid: Play App Signing and official F-Droid builds
have different signing and application-ID requirements.

The beta signing key is dedicated to GitHub beta builds. Do not reuse it for
Play App Signing or F-Droid.
