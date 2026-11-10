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

Keep the keystore and passwords out of the repository. The Play upload key uses
separate secrets; see [Play internal testing](PLAY_INTERNAL_TESTING.md).

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

The workflow checks out the shared docs submodule. Confirm the run succeeds and
record it in `docs/ci/CI_PROGRESS.md`.

The workflow uses `GITHUB_RUN_NUMBER` as the Android version code.

## Verify the release

Before announcing the beta:

- Confirm the GitHub release is marked as a prerelease.
- Download the APK and verify the checksum.
- Confirm the package name is `com.fossscout.app.beta`.
- Complete the [device smoke test](DEVICE_SMOKE_TEST.md), including installing
  alongside the production app.
- Confirm the release workflow run passed signature verification.

## Release history

| Version | Date | Channel | Result |
| --- | --- | --- | --- |
| [`v0.1.0-vanilla`](https://github.com/FossScout/FossScout/tree/v0.1.0-vanilla) | 2026-11-10 | F-Droid source tag | Tagged from `main` after the vanilla rename; no GitHub release. F-Droid metadata points to this tag; publication is pending. |
| [`v0.1.0-beta.4`](https://github.com/FossScout/FossScout/releases/tag/v0.1.0-beta.4) | 2026-11-06 | GitHub prerelease | Published; APK package `com.fossscout.app.beta`, version `0.1.0-beta.4` (version code 12), SHA-256 `7e42bb6d2d9f900c17aa57bfcf64522487f64a54411fc84ac7bad738764e6148`, certificate verified. |
| [`v0.1.0`](https://github.com/FossScout/FossScout/tree/v0.1.0) | 2026-11-05 | F-Droid source tag | Created on `e4262a1`; no GitHub release. Predates the vanilla rename, so a new tag is required before `fdroiddata` submission. |
| [`v0.1.0-beta.3`](https://github.com/FossScout/FossScout/releases/tag/v0.1.0-beta.3) | 2026-11-03 | GitHub prerelease | Published; APK package `com.fossscout.app.beta`, version `0.1.0-beta.3` (version code 11), SHA-256 `ef76fe01c48f116b156b6c472360d47b40fedbacdde0b9cde1dafcd8c154e332`, certificate verified. |
| [`v0.1.0-beta.2`](https://github.com/FossScout/FossScout/releases/tag/v0.1.0-beta.2) | 2026-11-01 | GitHub prerelease | Published; APK package `com.fossscout.app.beta`, version `0.1.0-beta.2` (version code 10), checksum and certificate verified. |
| [`v0.1.0-beta.1`](https://github.com/FossScout/FossScout/releases/tag/v0.1.0-beta.1) | 2026-10-29 | GitHub prerelease | Published; APK package `com.fossscout.app.beta`, version code 9, checksum and certificate verified. |

## Later channels

Play internal testing and F-Droid are separate follow-up stages. Do not publish
the beta APK to Play or F-Droid: Play App Signing and official F-Droid builds
have different signing and application-ID requirements.

The `v0.1.0-vanilla` tag contains the vanilla rename and the refreshed F-Droid
metadata. Stable tags do not trigger the beta release workflow or create a
GitHub release. The F-Droid source build is not submitted yet.

The beta signing key is dedicated to GitHub beta builds. Do not reuse it for
Play App Signing or F-Droid.

See [Play internal testing](PLAY_INTERNAL_TESTING.md) for the manual AAB build
workflow and Play Console setup. See [F-Droid release readiness](FDROID.md) for
the source-build verification, listing metadata, and submission checklist.
