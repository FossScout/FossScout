# F-Droid Release Readiness

F-Droid is the second distribution channel after the GitHub beta. The source
build, listing metadata, and submission metadata are prepared, but the existing
`v0.1.0-vanilla` tag predates the latest F19 tracker, audit, and manifest work.
The `fdroiddata` merge request is not open yet; choose the submission target
first.

The GitHub beta channel remains at `v0.1.0-beta.4`. The old `v0.1.0` tag is for
the previous `fdroid` flavor; the `v0.1.0-vanilla` tag contains the vanilla
rename and the earlier F-Droid metadata.

Do not publish the GitHub beta APK or the Play AAB to F-Droid. The F-Droid
variant is a separate package built from source with the `vanilla` flavor.

## Current state

| Item | Status |
| --- | --- |
| Vanilla flavor | Ready: `com.fossscout.app.vanilla`, full installed-app scan |
| Stable tag | **Decision required**: `v0.1.0-vanilla` contains the vanilla rename but predates the latest F19 slices and dependency updates |
| F-Droid release build | `v0.1.0-vanilla` passed locally on 2026-11-10; rebuild from the chosen tag before submission |
| Fastlane listing metadata | Current `main` copy includes F18/F19. `changelogs/1.txt` matches `v0.1.0-vanilla`; a new tag needs a new changelog |
| `fdroiddata` metadata | Prepared in [`docs/fdroid/com.fossscout.app.vanilla.yml`](fdroid/com.fossscout.app.vanilla.yml); currently targets `v0.1.0-vanilla` |
| F-Droid signing | F-Droid-hosted signing; no project key |
| F-Droid submission | **Pending**: choose the tag/version, complete real-device verification, then open the `fdroiddata` merge request; buildserver API 37 is confirmed |

## Submission target decision

Current `main` includes the F19 tracker, audit, and local APK manifest work plus
the dependency updates; `v0.1.0-vanilla` does not. Choose one path before
opening the merge request:

1. **Submit `v0.1.0-vanilla` as the first F-Droid release.** No build-file change
   or retag is needed, and the prepared metadata and `changelogs/1.txt` match.
   Use the tag's existing listing copy. This omits the latest F19 slices and
   dependency updates.
2. **Create a new vanilla tag from current `main` (recommended).** This includes
   the current feature set and current listing copy but requires a version
   name/code bump. The current Gradle default is `0.1.0`/`1`; update it only with
   project-owner approval, then update the `fdroiddata` `commit`, `versionName`,
   `versionCode`, and `CurrentVersion`, and add
   `fastlane/metadata/android/en-US/changelogs/<code>.txt`. Rebuild from the new
   tag before submission.

Do not open the merge request until the target is chosen and the real-device
checks pass.

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

## Before submission

1. **Choose the submission tag.** Either submit the existing
   `v0.1.0-vanilla` tag or create a new vanilla tag/version from current `main`.
   Confirm the metadata `commit`, `CurrentVersion`, `versionName`, and
   `versionCode` match the tagged build before opening the merge request.
2. **API 37 is available on the F-Droid buildserver.** The app uses
   `compileSdk = 37`. F-Droid's
   [`sdkmanager`](https://gitlab.com/fdroid/sdkmanager/-/blob/master/.gitlab-ci.yml)
   installs `platforms;android-37.0` (verified 2026-11-10). If the first
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
The prepared file currently targets `v0.1.0-vanilla` and contains:

- `commit: v0.1.0-vanilla`, the source tag;
- `versionName: '0.1.0-vanilla'` because the vanilla flavor appends the suffix;
- `versionCode: 1`;
- `UpdateCheckMode: Static` until automatic version extraction is tested;
- a `QUERY_ALL_PACKAGES` explanation in `MaintainerNotes`;
- no `Binaries` or `AllowedAPKSigningKeys` because F-Droid signs the build.

If a new tag/version is chosen, update all four tag/version fields together and
add the matching `changelogs/<versionCode>.txt`.

The descriptive listing is taken from `fastlane/metadata/android/en-US/`:
`short_description.txt`, `full_description.txt`, `images/icon.png`,
`images/phoneScreenshots/`, and `changelogs/<versionCode>.txt`.

## Submission

The project does not have GitLab access in this environment. Opening the
`fdroiddata` merge request is a manual step.

1. Fork `fdroiddata` and create a branch for `com.fossscout.app.vanilla`.
2. Copy the prepared metadata to `metadata/com.fossscout.app.vanilla.yml`.
3. In an F-Droid buildserver container, run:

   ```sh
   fdroid readmeta
   fdroid rewritemeta com.fossscout.app.vanilla
   fdroid lint com.fossscout.app.vanilla
   fdroid build com.fossscout.app.vanilla
   ```

4. Fix any metadata or build errors. The expected Gradle task is
   `assembleVanillaRelease`; the expected unsigned output is
   `app/build/outputs/apk/vanilla/release/app-vanilla-release-unsigned.apk`.
5. Commit with `New App: com.fossscout.app.vanilla` and open a merge request.
6. After the metadata is merged, wait for the F-Droid build cycle and then
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

- Choose whether to submit `v0.1.0-vanilla` or create a new vanilla tag/version
  from current `main`; update the metadata and changelog to match.
- Watch the first F-Droid build for the `android-37` resolution workaround;
  API 37 packages are available on the buildserver.
- Open the `fdroiddata` merge request and respond to review.
- Decide whether to add F-Droid-specific full-scan screenshots.
- Revisit `UpdateCheckMode` after the first stable vanilla build and metadata
  test.
