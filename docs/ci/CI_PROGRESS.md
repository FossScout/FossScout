# CI Progress & Status

Last updated: 2026-10-31
Branch: main
Feature baseline: main @ f02f9cf; beta release `v0.1.0-beta.1`. Docs/CI changes after this baseline are tracked below.

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
| Beta release workflow | Done | `.github/workflows/release.yml` validates signing secrets through environment variables, has `contents: write`, runs only for `v*-beta*` tags, checks out submodules, verifies the beta APK signature/checksum, and creates a GitHub prerelease. Run [40850806405](https://github.com/FossScout/FossScout/actions/runs/40850806405) passed before submodule checkout was added; the next beta tag must re-verify it. |
| Shared docs submodule | Done | `tools/best-practices` is pinned to `532512f`; CI and release workflows check out submodules. |
| Play internal AAB build | Done | `.github/workflows/play-internal.yml` builds a signed AAB with dedicated Play upload secrets, verifies it, and uploads it as an artifact; Play Console upload remains manual. |
| Toolchain upgrade | Done | Gradle 9.6.1, AGP 9.5.0, Kotlin 2.4.21, Compose BOM 2026.06.00, coroutines 1.11.0, and serialization 1.12.0; `kotlinOptions` migrated to `compilerOptions`. |

## Pending / Manual
| Task | Status | Details |
| --- | --- | --- |
| GitHub Secrets for release | Done | `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` configured on 2026-10-29 |
| Play upload secrets | Pending | `PLAY_KEYSTORE_FILE`, `PLAY_KEYSTORE_PASSWORD`, `PLAY_KEY_ALIAS`, and `PLAY_KEY_PASSWORD` are not configured |
| Branch protection rules | Done | `main` ruleset requires a PR, the `build` check, an up-to-date branch, and conversation resolution; blocks force pushes and deletion. `best-practices` has a PR-only ruleset. |
| Release smoke test | Done | `v0.1.0-beta.1` published as a GitHub prerelease; APK package `com.fossscout.app.beta`, version `0.1.0-beta.1` (version code 9), checksum and certificate verified. |
| Release notes/changelog | Partial | `CHANGELOG.md` is maintained manually; automation remains optional |
| Play Console setup | Pending | App entry, Play App Signing, declarations, store listing, internal track, and tester setup remain manual |
| Play upload automation | Pending | Requires a Play Developer API service account and an approved upload action or dependency |
| Compose BOM 2026.09.00 | Pending | Requires `compileSdk = 37`; API 36 remains the approved compile SDK |
| Built-in Kotlin migration | Pending | AGP 9.5 bundles Kotlin 2.2.10; the project opts out to keep Kotlin 2.4.21. Revisit before AGP 10 |
| Coverage reporting | Pending | Optional: add jacoco/report upload |

## Verification commands (local)
```bash
# Test versioning logic (dry run)
CI_VERSION_CODE=123 CI_VERSION_NAME=1.0.0 ./gradlew :app:printVersion 2>&1 | tail -5

# Build both flavors debug
./gradlew assemblePlaystoreDebug assembleFdroidDebug

# Run tests/lint
./gradlew test lint
```

## Completeness check against STATUS.md
- CI/build matrix: **Complete** (both flavors, debug/release/beta paths, manual Play AAB)
- Toolchain: **Complete for approved API 36**; Compose BOM 2026.09 and built-in Kotlin migration remain pending
- Versioning/flavors/variants: **Complete** (CI-driven, flavor support, beta application ID)
- Issues/PR/workflow: **Complete** (templates + dependabot + branch workflow guidance)
- Release/signing: **Complete for GitHub beta**; Play AAB build uses dedicated secrets, but Play Console upload and F-Droid signing remain pending
- Documentation: **Complete for the beta channel and Play AAB build**; F-Droid runbook remains pending
