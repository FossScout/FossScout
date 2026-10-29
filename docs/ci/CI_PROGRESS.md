# CI Progress & Status

Last updated: 2026-10-29
Branch: main
Baseline: main @ 0fc3447; beta release `v0.1.0-beta.1`

## Completed
| Task | Status | Details |
| --- | --- | --- |
| CI workflow (build/test/lint) | Done | `.github/workflows/ci.yml` - JDK 17, Android SDK, gradle cache, runs test/lint/assemblePlaystoreDebug/assembleFdroidDebug, uploads APKs |
| Issue templates | Done | `.github/ISSUE_TEMPLATE/bug_report.md`, `feature_request.md` |
| PR template | Done | `.github/PULL_REQUEST_TEMPLATE.md` aligned to AGENTS.md |
| Dependabot | Done | `.github/dependabot.yml` for gradle and github-actions (weekly) |
| CI badge in README | Done | Added CI badge at top of README.md |
| Versioning from CI/env | Done | `app/build.gradle.kts` reads `CI_VERSION_CODE`/`GITHUB_RUN_NUMBER` and `CI_VERSION_NAME`; local builds default to `0.1.0` |
| Release signing config (conditional) | Done | `signingConfigs.release` reads env secrets; applied to release and beta builds if the keystore exists |
| Beta build type | Done | `playstoreBeta` publishes as `com.fossscout.app.beta` with a `-beta` version-name suffix, so it can be installed alongside production |
| Beta release workflow | Done | `.github/workflows/release.yml` validates signing secrets through environment variables, has `contents: write`, runs only for `v*-beta*` tags, verifies the beta APK signature/checksum, and creates a GitHub prerelease. Run [40850806405](https://github.com/FossScout/FossScout/actions/runs/40850806405) passed. |

## Pending / Manual
| Task | Status | Details |
| --- | --- | --- |
| GitHub Secrets for release | Done | `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` configured on 2026-10-29 |
| Branch protection rules | Pending (manual) | Require PR reviews, require status checks (CI), restrict pushes to main |
| Release smoke test | Done | `v0.1.0-beta.1` published as a GitHub prerelease; APK package `com.fossscout.app.beta`, version `0.1.0-beta.1` (version code 9), checksum and certificate verified. |
| Release notes/changelog | Pending | Consider release drafter or auto-generate from commits/PRs |
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
- CI/build matrix: **Complete** (both flavors, debug/release/beta paths)
- Versioning/flavors/variants: **Complete** (CI-driven, flavor support, beta application ID)
- Issues/PR/workflow: **Complete** (templates + dependabot + branch workflow guidance)
- Release/signing: **Complete for GitHub beta**; Play internal and F-Droid signing remain pending
- Documentation: **Complete for the beta channel**; Play/F-Droid runbooks remain pending
