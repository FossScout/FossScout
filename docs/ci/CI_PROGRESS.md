# CI Progress & Status

Last updated: 2026-11-05
Branch: main
Feature baseline: main @ 21d0f51; F-Droid tag `v0.1.0` @ `e4262a1`; current beta release `v0.1.0-beta.3`. Docs/CI changes after this baseline are tracked below.

## Completed
| Task | Status | Details |
| --- | --- | --- |
| CI workflow (build/test/lint) | Done | `.github/workflows/ci.yml` - JDK 17, Android SDK, gradle cache, runs test/lint/assemblePlaystoreDebug/assembleFdroidDebug, uploads APKs |
| Action pinning | Done | Workflow actions are pinned to commit SHAs with version comments; CI explicitly uses read-only repository permissions. |
| Issue templates | Done | `.github/ISSUE_TEMPLATE/bug_report.md`, `feature_request.md` |
| PR template | Done | `.github/PULL_REQUEST_TEMPLATE.md` includes the shared contributing guide link and verification checklist |
| Dependabot | Done | `.github/dependabot.yml` for gradle and github-actions (weekly) |
| CI badge in README | Done | Added CI badge at top of README.md |
| Versioning from CI/env | Done | `app/build.gradle.kts` reads `CI_VERSION_CODE`/`GITHUB_RUN_NUMBER` and `CI_VERSION_NAME`; local builds default to `0.1.0` |
| Release signing config (conditional) | Done | `signingConfigs.release` reads env secrets; applied to release and beta builds if the keystore exists |
| Beta build type | Done | `playstoreBeta` publishes as `com.fossscout.app.beta` with a `-beta` version-name suffix, so it can be installed alongside production |
| Beta release workflow | Done | `.github/workflows/release.yml` validates signing secrets through environment variables, has `contents: write`, runs only for `v*-beta*` tags, checks out submodules, verifies the beta APK signature/checksum, and creates a GitHub prerelease. Run [41648987372](https://github.com/FossScout/FossScout/actions/runs/41648987372) passed for `v0.1.0-beta.3`. |
| Shared docs submodule | Done | `tools/best-practices` is pinned to `532512f`; CI and release workflows check out submodules. |
| Play internal AAB build | Done | `.github/workflows/play-internal.yml` builds a signed AAB with dedicated Play upload secrets, verifies it, and uploads it as an artifact; Play Console upload remains manual. |
| Toolchain upgrade | Done | Gradle 9.8.2, AGP 9.5.0, Kotlin 2.4.21, Compose BOM 2026.09.00, coroutines 1.11.0, and serialization 1.12.0; `kotlinOptions` migrated to `compilerOptions`. |
| Gradle wrapper 9.8.2 | Done | Wrapper JAR matches the official Gradle SHA-256; `test`, `lint`, and main CI passed. |
| CI device smoke test | Done | `.github/workflows/device-smoke.yml` is a manual API 36 AOSP x86_64 emulator run. Run [41644684403](https://github.com/FossScout/FossScout/actions/runs/41644684403) passed with both debug APKs: APK checks, launch/navigation, favourites persistence, rotation, font scale, theme, offline Stores, installer fallbacks, Play visibility, and the F-Droid full scan. |
| F-Droid release readiness | Done | `./gradlew assembleFdroidRelease` passed locally on 2026-11-05 from `v0.1.0` (`e4262a1`); output is `com.fossscout.app.fdroid`, version `0.1.0-fdroid` (code 1), with `QUERY_ALL_PACKAGES` and no install/uninstall/accessibility entries. Fastlane listing metadata and a submission-ready `fdroiddata` entry are prepared in [`docs/FDROID.md`](../FDROID.md) and [`docs/fdroid/`](../fdroid/). |
| F-Droid stable tag | Done | `v0.1.0` points at `e4262a1`; the build uses F-Droid-hosted signing, no project key, and creates no GitHub release. |

## Pending / Manual
| Task | Status | Details |
| --- | --- | --- |
| GitHub Secrets for release | Done | `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` configured on 2026-10-29 |
| Play upload secrets | Pending | `PLAY_KEYSTORE_FILE`, `PLAY_KEYSTORE_PASSWORD`, `PLAY_KEY_ALIAS`, and `PLAY_KEY_PASSWORD` are not configured |
| Branch protection rules | Done | `main` ruleset requires a PR, the `build` check, an up-to-date branch, and conversation resolution; blocks force pushes and deletion. `best-practices` has a PR-only ruleset. |
| Release smoke test | Done | `v0.1.0-beta.3` published as a GitHub prerelease; APK package `com.fossscout.app.beta`, version `0.1.0-beta.3` (version code 11), SHA-256 `ef76fe01c48f116b156b6c472360d47b40fedbacdde0b9cde1dafcd8c154e332`, certificate SHA-256 `96:6B:04:56:B3:E6:F6:3B:D8:BD:64:3A:4F:53:D4:9E:E4:31:DE:0B:4A:16:86:07:DA:E0:6D:4B:4A:5A:57:16`. |
| Real-device smoke test | Partial | The CI emulator baseline passes (run [41644684403](https://github.com/FossScout/FossScout/actions/runs/41644684403)). Remaining checks are in [`docs/DEVICE_SMOKE_TEST.md`](../DEVICE_SMOKE_TEST.md): release signing and Play internal track, real store clients, work profiles, store index/signing, matching correction, replacement handoff, sharing/import, backup/restore, stale/hide behavior, and real-device performance. |
| Release notes/changelog | Partial | `CHANGELOG.md` is maintained manually; automation remains optional |
| Play Console setup | Pending | App entry, Play App Signing, declarations, store listing, internal track, and tester setup remain manual |
| Play upload automation | Pending | Requires a Play Developer API service account and an approved upload action or dependency |
| F-Droid submission | Ready | Stable `v0.1.0` tag, F-Droid-hosted signing, and prepared metadata are ready; needs an `fdroiddata` merge request and buildserver API 37 confirmation. See [`docs/FDROID.md`](../FDROID.md). |
| Compose BOM 2026.09.00 | Done | `compileSdk = 37`; CI, release, and Play workflows install `platforms;android-37.0` |
| Built-in Kotlin migration | Pending | AGP 9.5 bundles Kotlin 2.2.10; the project opts out to keep Kotlin 2.4.21. Revisit before AGP 10 |
| Coverage reporting | Pending | Optional: add jacoco/report upload |

## Verification commands (local)
```bash
# Test versioning logic (dry run)
CI_VERSION_CODE=123 CI_VERSION_NAME=1.0.0 ./gradlew :app:printVersion 2>&1 | tail -5

# Build both flavors debug
./gradlew assemblePlaystoreDebug assembleFdroidDebug

# Build the unsigned F-Droid release for metadata checks
./gradlew assembleFdroidRelease

# Run tests/lint
./gradlew test lint
```

## Completeness check against STATUS.md
- CI/build matrix: **Complete** (both flavors, debug/release/beta paths, manual Play AAB)
- Toolchain: **Complete for approved API 37**; built-in Kotlin migration remains pending
- Versioning/flavors/variants: **Complete** (CI-driven, flavor support, beta application ID)
- Issues/PR/workflow: **Complete** (templates + dependabot + branch workflow guidance)
- Release/signing: **Complete for GitHub beta and the F-Droid source tag**; Play AAB build uses dedicated secrets, but Play Console upload and F-Droid submission remain pending
- Documentation: **Complete for the beta channel, Play AAB build, CI device smoke baseline, and F-Droid submission preparation**; the real-device checklist and `fdroiddata` merge request remain pending
