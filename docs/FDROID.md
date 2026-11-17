# F-Droid Release Readiness

F-Droid is the second distribution channel after the GitHub beta. The
`v0.1.1-vanilla` release tag is created from the `0.1.1` release commit on
`main` (`42e8cc7`). It includes the F19 tracker, audit, and local APK manifest
work plus the dependency updates. The public `fdroiddata` fork branch is ready
and its pipeline passed on 2026-11-17. The merge request is not open yet because
the real-device check is still pending.

The GitHub beta channel remains at `v0.1.0-beta.4`. The old `v0.1.0` tag is for
the previous `fdroid` flavor; `v0.1.0-vanilla` is the historical first vanilla
tag and predates the current feature set.

Do not publish the GitHub beta APK or the Play AAB to F-Droid. The F-Droid
variant is a separate package built from source with the `vanilla` flavor.

## Current state

| Item | Status |
| --- | --- |
| Vanilla flavor | Ready: `com.fossscout.app.vanilla`, full installed-app scan |
| Stable tag | **Done**: `v0.1.1-vanilla` at `42e8cc7` |
| F-Droid release build | **Done**: clean tag build passed locally on 2026-11-16; the historical `v0.1.0-vanilla` build passed on 2026-11-10 |
| Fastlane listing metadata | Current `main` copy includes F18/F19; `changelogs/2.txt` matches `v0.1.1-vanilla` |
| `fdroiddata` metadata | Prepared in [`docs/fdroid/com.fossscout.app.vanilla.yml`](fdroid/com.fossscout.app.vanilla.yml); matches the verified fork copy at `0939a9b` |
| F-Droid fork pipeline | **Done**: branch `com.fossscout.app.vanilla`; [pipeline #3077952136](https://gitlab.com/kanzel88/fdroiddata/-/pipelines/3077952136) passed all 9 jobs on 2026-11-17, including `fdroid build` and `check apk` |
| F-Droid signing | F-Droid-hosted signing; no project key |
| F-Droid submission | **Pending**: fork branch and pipeline are green; complete real-device verification, then open the `fdroiddata` merge request; buildserver API 37 is confirmed |

## Release tag

`v0.1.1-vanilla` includes the current F18/F19 feature set, the F20 matching
correction, and the dependency updates. `v0.1.0-vanilla` remains a historical
tag; do not open the merge request against it. The release commit bumped the
Gradle defaults to version name `0.1.1` and version code `2`; the vanilla flavor
produces `0.1.1-vanilla`.

The tag is created and pushed. Open the merge request only after the
real-device checks pass.

## Previous v0.1.0 build (before rename)

The old `v0.1.0` tag used the retired `fdroid` flavor. It was verified locally
on 2026-11-05:

```sh
./gradlew assembleFdroidRelease
```

The build succeeded and produced an **unsigned** APK:

- Path: `app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk`
- Package: `com.fossscout.app.fdroid`
- Version name: `0.1.0-fdroid`
- Version code: `1`
- SHA-256: `7ac11a2b7c861609b207f1aef5e53c948faa254d43a7255112bb895b20d7d8da`

This build does not describe the current `vanilla` flavor. Its merged manifest
contained `QUERY_ALL_PACKAGES` and `INTERNET`, plus AndroidX's dynamic-receiver
permission, and no install, uninstall, or accessibility entries.

## Prepared v0.1.0-vanilla build

Run from the `v0.1.0-vanilla` tag:

```sh
./gradlew assembleVanillaRelease
```

A clean build from the `v0.1.0-vanilla` tag was verified locally on 2026-11-10
and produced this **unsigned** APK:

- Path: `app/build/outputs/apk/vanilla/release/app-vanilla-release-unsigned.apk`
- Package: `com.fossscout.app.vanilla`
- Version name: `0.1.0-vanilla`
- Version code: `1`
- SHA-256: `fd1160b817bc4a14a5568f68518438cdef205fed50e5a458878288a49d589cd9`

Local build checksums vary with the build environment. This one records the tag
check only; it is not a release artifact or a reproducibility claim.

The vanilla merged manifest uses `QUERY_ALL_PACKAGES` and `INTERNET`, plus
AndroidX's dynamic-receiver permission. It must not contain install, uninstall,
or accessibility entries. The Play flavor keeps limited package visibility and
does not declare `QUERY_ALL_PACKAGES`.

This is a local build check, not a distributable artifact. F-Droid will build
and sign its own APK.

## v0.1.1-vanilla tag build check

A clean build from the pushed tag was verified locally on 2026-11-16:

- Path: `app/build/outputs/apk/vanilla/release/app-vanilla-release-unsigned.apk`
- Package: `com.fossscout.app.vanilla`
- Version name: `0.1.1-vanilla`
- Version code: `2`
- SHA-256: `ee43493e8ecbbd6abbd206a43e3b2cfe3ca278a47cafc49a1448d3fcbb96c90c`

The merged manifest uses `QUERY_ALL_PACKAGES` and `INTERNET`, plus AndroidX's
dynamic-receiver permission, with no install, uninstall, or accessibility
entries. Local checksums vary with the build environment.

## Before submission

1. **Use the pushed `v0.1.1-vanilla` tag.** Confirm the metadata `commit`,
   `CurrentVersion`, `versionName`, and `versionCode` match the tagged build
   before opening the merge request.
2. **API 37 is available on the F-Droid buildserver.** The app uses
   `compileSdk = 37`. F-Droid's
   [`sdkmanager`](https://gitlab.com/fdroid/sdkmanager/-/blob/master/.gitlab-ci.yml)
   installs `platforms;android-37.0` (verified 2026-11-10). The first fork
   pipeline resolved `android-37` normally on 2026-11-17. If the official
   buildserver build cannot resolve `android-37`, add the known `fdroiddata`
   workaround to the build entry:
   `ln -s $$SDK$$/platforms/android-37.0 $$SDK$$/platforms/android-37`. Do not
   lower the SDK only for F-Droid.
3. **Review the listing.** The Fastlane text and graphics are in
   `fastlane/metadata/android/en-US/`. The screenshots are shared with the Play
   listing. Recapture the full-scan screen from the vanilla build if it adds
   useful evidence.
4. **Use F-Droid-hosted signing.** Do not reuse the beta or Play keys. See
   [Signing](#signing).
5. **Recheck the manifest and permissions.** The vanilla build intentionally
   requests broad package visibility for the full scan. Confirm the merged
   manifest still has no install, uninstall, or accessibility entries.

## Signing

The chosen path is **F-Droid-hosted signing**. F-Droid builds and signs the APK
with its own key; the project does not supply an F-Droid key, `Binaries`, or
`AllowedAPKSigningKeys`. This keeps the beta, Play, and F-Droid signing material
separate.

Reproducible builds with a dedicated project F-Droid key are deferred. If they
are added later, the signing path must not reuse the beta or Play keys, and the
metadata must add `Binaries` and `AllowedAPKSigningKeys`.

## Metadata

Use
[`docs/fdroid/com.fossscout.app.vanilla.yml`](fdroid/com.fossscout.app.vanilla.yml).
The prepared file targets `v0.1.1-vanilla`, matches the verified fork copy at
`0939a9b`, and contains:

- `commit: 42e8cc71b0245f51a5cd12af0f93ce9261ab11b4`, the full release commit
  hash required by `fdroiddata`;
- `versionName: 0.1.1-vanilla` because the vanilla flavor appends the suffix;
- `versionCode: 2`;
- `AutoUpdateMode: None` and `UpdateCheckMode: Static`;
- a `scandelete` entry for `.github/fixtures/f19/repo/entry.jar`; the fixture
  JAR is test-only and triggers the F-Droid binary scanner;
- a `QUERY_ALL_PACKAGES` explanation in `MaintainerNotes`;
- no `Binaries` or `AllowedAPKSigningKeys` because F-Droid signs the build.

Keep `commit`, `versionName`, `versionCode`, `CurrentVersion`, and
`CurrentVersionCode` together when the release changes, and add the matching
`changelogs/<versionCode>.txt`.

The descriptive listing is taken from `fastlane/metadata/android/en-US/`:
`short_description.txt`, `full_description.txt`, `images/icon.png`,
`images/phoneScreenshots/`, and `changelogs/<versionCode>.txt`.

## Submission

The public fork is [`kanzel88/fdroiddata`](https://gitlab.com/kanzel88/fdroiddata)
and the branch is `com.fossscout.app.vanilla`; its pipeline passed on
2026-11-17. This environment has no GitLab token, so opening the merge request
remains manual.

1. Confirm the fork branch still points at `0939a9b` and the metadata matches
   the prepared file.
2. In an F-Droid buildserver container, run:

   ```sh
   fdroid readmeta
   fdroid rewritemeta com.fossscout.app.vanilla
   fdroid lint com.fossscout.app.vanilla
   fdroid build com.fossscout.app.vanilla
   ```

3. Fix any metadata or build errors. The expected Gradle task is
   `assembleVanillaRelease`; the expected unsigned output is
   `app/build/outputs/apk/vanilla/release/app-vanilla-release-unsigned.apk`.
4. Commit with `New App: com.fossscout.app.vanilla` and open a merge request
   titled `New app: FossScout` with the `New App` label.
5. After the metadata is merged, wait for the F-Droid build cycle and then
   install the published build for verification.

## Verification after publication

- The F-Droid listing shows package `com.fossscout.app.vanilla`, the version
  chosen for submission, and the expected icon and screenshots.
- The published APK is signed by F-Droid, not the beta or Play key.
- The full scan works, the scan stays local, and the app does not request
  install, uninstall, or accessibility permissions.
- Installing the new version over the previous F-Droid build preserves data.
- The [device smoke test](DEVICE_SMOKE_TEST.md) records the vanilla build
  separately from the Play and GitHub beta builds.

## Open items

- Open the `fdroiddata` merge request for `v0.1.1-vanilla` and respond to
  review.
- The first fork pipeline resolved `android-37` without the documented
  workaround; no metadata change is needed. Watch the post-merge official
  build.
- Decide whether to add F-Droid-specific full-scan screenshots.
- Revisit `UpdateCheckMode` after the first stable vanilla build and metadata
  test.
