# CI Progress & Status

Last updated: 2026-10-29
Branch: chore/beta-hardening
Baseline: main @ 3e19f78 (post CI merge)

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
| Beta release workflow | Fixed | `.github/workflows/release.yml` validates signing secrets through environment variables, has `contents: write`, runs only for `v*-beta*` tags, and verifies the beta APK signature/checksum; a real tag run still needs signing secrets. |

## Pending / Manual
| Task | Status | Details |
| --- | --- | --- |
| GitHub Secrets for release | Pending (manual) | Add `KEYSTORE_FILE` (base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in repo settings |
| Branch protection rules | Pending (manual) | Require PR reviews, require status checks (CI), restrict pushes to main |
| Release smoke test | Pending (manual) | Add signing secrets, push a disposable `v*-beta*` tag, and verify the `com.fossscout.app.beta` APK signature, checksum, and GitHub prerelease. |
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
- Release/signing: **Complete in code** (beta path; secrets needed to activate)
- Documentation: **In progress** (README badge and beta profile added; CI docs added)
