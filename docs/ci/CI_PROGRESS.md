# CI Progress & Status

Last updated: 2026-11-16
Branch: main
Feature baseline: main @ `de110e1`; F-Droid target `v0.1.1-vanilla` (version `0.1.1`/code `2`) is prepared for tagging; current beta release `v0.1.0-beta.4`. The standardized Stores screenshot workflow and the latest dependency updates are on `main`.

## Completed
| Task | Status | Details |
| --- | --- | --- |
| CI workflow (build/test/lint) | Done | `.github/workflows/ci.yml` - JDK 17, Android SDK, gradle cache, runs test/lint/assemblePlaystoreDebug/assembleVanillaDebug, uploads APKs |
| Action pinning | Done | Workflow actions are pinned to commit SHAs with version comments; CI explicitly uses read-only repository permissions. |
| Issue templates | Done | `.github/ISSUE_TEMPLATE/bug_report.md`, `feature_request.md` |
| PR template | Done | `.github/PULL_REQUEST_TEMPLATE.md` includes the shared contributing guide link and verification checklist |
| Dependabot | Done | `.github/dependabot.yml` for gradle and github-actions (weekly) |
| CI badge in README | Done | Added CI badge at top of README.md |
| Versioning from CI/env | Done | `app/build.gradle.kts` reads `CI_VERSION_CODE`/`GITHUB_RUN_NUMBER` and `CI_VERSION_NAME`; local builds default to `0.1.1`/`2` |
| Release signing config (conditional) | Done | `signingConfigs.release` reads env secrets; applied to release and beta builds if the keystore exists |
| Beta build type | Done | `playstoreBeta` publishes as `com.fossscout.app.beta` with a `-beta` version-name suffix, so it can be installed alongside production |
| Beta release workflow | Done | `.github/workflows/release.yml` validates signing secrets through environment variables, has `contents: write`, runs only for `v*-beta*` tags, checks out submodules, verifies the beta APK signature/checksum, and creates a GitHub prerelease. Run [42239255577](https://github.com/FossScout/FossScout/actions/runs/42239255577) passed for `v0.1.0-beta.4`. |
| Shared docs submodule | Done | `tools/best-practices` is pinned to `532512f`; CI and release workflows check out submodules. |
| Play internal AAB build | Done | `.github/workflows/play-internal.yml` builds a signed AAB with dedicated Play upload secrets, verifies it, and uploads it as an artifact; Play Console upload remains manual. |
| Toolchain upgrade | Done | Gradle 9.8.2, AGP 9.5.0, Kotlin 2.4.21, Compose BOM 2026.09.00, coroutines 1.11.0, and serialization 1.12.0; `kotlinOptions` migrated to `compilerOptions`. |
| Gradle wrapper 9.8.2 | Done | Wrapper JAR matches the official Gradle SHA-256; `test`, `lint`, and main CI passed. |
| Dependency updates | Done | PRs [#68](https://github.com/FossScout/FossScout/pull/68)–[#71](https://github.com/FossScout/FossScout/pull/71) updated AGP to 9.5.1, the Gradle wrapper to 9.9.0, setup-java to 6.2.0, and action-gh-release to 3.0.4; `main` CI passed. |
| CI device smoke test | Done | `.github/workflows/device-smoke.yml` is a manual API 36 AOSP x86_64 emulator run. Run [43867944411](https://github.com/FossScout/FossScout/actions/runs/43867944411) passed on `9f82284` for version `0.1.1` and includes both debug APKs, the F19 fixture store, tracker match/no-match, local APK manifest import and package-mismatch rejection, and audit JSON export. The Stores screenshot artifact was not requested for this run. |
| F-Droid release readiness | Ready to tag | The selected target is `v0.1.1-vanilla` (`com.fossscout.app.vanilla`, version `0.1.1`/code `2`), which includes the F19 tracker/audit/manifest slices and dependency updates. The `fdroiddata` metadata and `changelogs/2.txt` match the target. Tag the release commit after this version-bump PR merges, then rebuild from the tag. F-Droid's `sdkmanager` includes `platforms;android-37.0`, so buildserver API 37 support is confirmed. See [`docs/FDROID.md`](../FDROID.md) and [`docs/fdroid/`](../fdroid/). |
| F-Droid stable tag | Target | `v0.1.1-vanilla` supersedes the historical `v0.1.0-vanilla` tag; create and push it from the release commit. F-Droid-hosted signing remains the plan. |
| F19 tracker-signature verification | Done | PR [#72](https://github.com/FossScout/FossScout/pull/72): local test/lint/debug/release builds passed (581 tests across 82 result files, 0 failures); CI run [43218372408](https://github.com/FossScout/FossScout/actions/runs/43218372408) passed for `23e05fe`. The 212,848-byte tracker dataset is present in both debug and release APKs for both flavors. Real-device tracker UI checks remain in the smoke checklist. |
| F19 emulator comparison smoke | Done | PR [#75](https://github.com/FossScout/FossScout/pull/75): run [43734657567](https://github.com/FossScout/FossScout/actions/runs/43734657567) passed for `d2ebe37` on the API 36 emulator. The run creates a custom HTTPS F-Droid v2 fixture store, syncs it, verifies the Exodus ETIP header plus an Aarki match and a no-match vanilla scan, selects a matching local APK manifest, rejects a package mismatch, and reads the exported audit JSON from the app cache. Local `./gradlew test` and `./gradlew lint` passed. |

## Pending / Manual
| Task | Status | Details |
| --- | --- | --- |
| GitHub Secrets for release | Done | `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` configured on 2026-10-29 |
| Play upload secrets | Pending | `PLAY_KEYSTORE_FILE`, `PLAY_KEYSTORE_PASSWORD`, `PLAY_KEY_ALIAS`, and `PLAY_KEY_PASSWORD` are not configured |
| Branch protection rules | Done | `main` ruleset requires a PR, the `build` check, an up-to-date branch, and conversation resolution; blocks force pushes and deletion. `best-practices` has a PR-only ruleset. |
| Release smoke test | Done | `v0.1.0-beta.4` published as a GitHub prerelease; APK package `com.fossscout.app.beta`, version `0.1.0-beta.4` (version code 12), SHA-256 `7e42bb6d2d9f900c17aa57bfcf64522487f64a54411fc84ac7bad738764e6148`, certificate SHA-256 `96:6B:04:56:B3:E6:F6:3B:D8:BD:64:3A:4F:53:D4:9E:E4:31:DE:0B:4A:16:86:07:DA:E0:6D:4B:4A:5A:57:16`. |
| Real-device smoke test | Partial | The CI emulator baseline passes (run [43867944411](https://github.com/FossScout/FossScout/actions/runs/43867944411) on `9f82284`). Remaining checks are in [`docs/DEVICE_SMOKE_TEST.md`](../DEVICE_SMOKE_TEST.md): release signing and Play internal track, real store clients, work profiles, store index/signing, matching correction, replacement handoff, sharing/import, backup/restore, stale/hide behavior, and real-device performance. |
| Release notes/changelog | Partial | `CHANGELOG.md` is maintained manually; automation remains optional |
| Play Console setup | Pending | App entry, Play App Signing, declarations, store listing, internal track, and tester setup remain manual |
| Play upload automation | Pending | Requires a Play Developer API service account and an approved upload action or dependency |
| F-Droid submission | Pending | Create and push `v0.1.1-vanilla`, complete real-device verification, then open the `fdroiddata` merge request. Metadata, changelog, and buildserver API 37 check are prepared. See [`docs/FDROID.md`](../FDROID.md). |
| Compose BOM 2026.09.00 | Done | `compileSdk = 37`; CI, release, and Play workflows install `platforms;android-37.0` |
| Built-in Kotlin migration | Pending | AGP 9.5 bundles Kotlin 2.2.10; the project opts out to keep Kotlin 2.4.21. Revisit before AGP 10 |
| Coverage reporting | Pending | Optional: add jacoco/report upload |

## Verification commands (local)
```bash
# Test versioning logic (dry run)
CI_VERSION_CODE=123 CI_VERSION_NAME=1.0.0 ./gradlew :app:printVersion 2>&1 | tail -5

# Build both flavors debug
./gradlew assemblePlaystoreDebug assembleVanillaDebug

# Build the unsigned vanilla release for metadata checks
./gradlew assembleVanillaRelease

# Run tests/lint
./gradlew test lint
```

## Completeness check against STATUS.md
- CI/build matrix: **Complete** (both flavors, debug/release/beta paths, manual Play AAB)
- Toolchain: **Complete for approved API 37**; built-in Kotlin migration remains pending
- Versioning/flavors/variants: **Complete** (CI-driven, flavor support, beta application ID)
- Issues/PR/workflow: **Complete** (templates + dependabot + branch workflow guidance)
- Release/signing: **Complete for GitHub beta; F-Droid `v0.1.1-vanilla` release metadata is prepared for tagging**; Play AAB build uses dedicated secrets, but Play Console upload and F-Droid submission remain pending
- Documentation: **Complete for the beta channel, Play AAB build, CI device smoke baseline, and F-Droid submission preparation, including API 37 confirmation**; the real-device checklist, `v0.1.1-vanilla` tag, and `fdroiddata` merge request remain pending
