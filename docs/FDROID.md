# F-Droid Release Readiness

F-Droid is the second distribution channel after the GitHub beta. The source
build, listing metadata, and a draft `fdroiddata` entry are prepared here, but
the app is **not submitted**. F-Droid can only build a stable release; the
latest release is `v0.1.0-beta.3`.

Do not publish the GitHub beta APK or the Play AAB to F-Droid. The F-Droid
variant is a separate package built from source with the `fdroid` flavor.

## Current state

| Item | Status |
| --- | --- |
| F-Droid flavor | Ready: `com.fossscout.app.fdroid`, full installed-app scan |
| F-Droid release build | Verified locally on 2026-11-05 from `1290d7e` |
| Fastlane listing metadata | Added under `fastlane/metadata/android/en-US/` |
| `fdroiddata` metadata | Draft in [`docs/fdroid/com.fossscout.app.fdroid.yml`](fdroid/com.fossscout.app.fdroid.yml) |
| Stable release and tag | Not created; stable releases remain disabled |
| F-Droid signing plan | Not decided; see [Signing](#signing) |
| F-Droid submission | Not started |

## Verified local build

From the repository root on 2026-11-05:

```sh
./gradlew assembleFdroidRelease
```

The build succeeded and produced an **unsigned** APK:

- Path: `app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk`
- Package: `com.fossscout.app.fdroid`
- Version name: `0.1.0-fdroid`
- Version code: `1`
- SHA-256: `09d163b48d13cb134808e2a3b6f8e22d5a8a3f400a7d711c280415d36c62a3f4`

The merged manifest contains `QUERY_ALL_PACKAGES` and `INTERNET`, plus
AndroidX's dynamic-receiver permission. It does not contain install, uninstall,
or accessibility entries. The Play flavor keeps limited package visibility and
does not declare `QUERY_ALL_PACKAGES`.

This is a local build check, not a distributable artifact. Version code `1` is
the local default and must be replaced with a unique release code before the
first F-Droid submission if another F-Droid build has already been published.

## Before submission

1. **Cut a stable release.** F-Droid must not build `v0.1.0-beta.3`. Create a
   stable tag such as `v0.1.0` on a commit whose F-Droid build has version name
   `0.1.0-fdroid`. Use a version code higher than any previously published
   F-Droid build.
2. **Confirm the F-Droid build server supports API 37.** The app uses
   `compileSdk = 37`. Check the current F-Droid buildserver image before opening
   the submission; if platform 37 is unavailable, wait for it rather than
   lowering the SDK only for F-Droid.
3. **Review the listing.** The Fastlane text and graphics are in
   `fastlane/metadata/android/en-US/`. The screenshots are shared with the Play
   listing. Recapture the full-scan screen from the F-Droid build if it adds
   useful evidence.
4. **Keep the signing keys separate.** Do not use the GitHub beta key or the
   Play upload key for an F-Droid build. See [Signing](#signing).
5. **Recheck the manifest and permissions.** The F-Droid build intentionally
   requests broad package visibility for the full scan. Confirm the merged
   manifest still has no install, uninstall, or accessibility entries.

## Signing

F-Droid has two supported paths:

- **F-Droid signs the build.** This is the default. The project does not need an
  F-Droid key, and F-Droid publishes the APK signed with its own key.
- **Reproducible build with a project key.** The project signs the `fdroid`
  release APK with a dedicated F-Droid key, publishes that APK and its checksum,
  and adds `Binaries` plus `AllowedAPKSigningKeys` to the metadata. F-Droid
  verifies the upstream binary before using it.

The current `app/build.gradle.kts` has one release signing configuration shared
by the Play and F-Droid flavors. Do not point that configuration at the F-Droid
key. A separate F-Droid signing path is a build-file change and needs explicit
approval before it is implemented.

If the project chooses the reproducible path, the published binary must be
versioned and reproducible from the same commit, and the metadata needs the
lowercase SHA-256 fingerprint of the F-Droid signing certificate.

## Metadata

Use [`docs/fdroid/com.fossscout.app.fdroid.yml`](fdroid/com.fossscout.app.fdroid.yml)
as the starting point. Before submission:

- Replace `REPLACE_WITH_STABLE_COMMIT` with the full commit hash of the stable
  tag. F-Droid does not accept a branch name or a tag name in `commit`.
- Keep `versionName` as `0.1.0-fdroid` because the `fdroid` flavor appends the
  suffix. Update both version fields when the stable release changes.
- Leave `UpdateCheckMode: Static` until the version extraction has been tested.
  The default version comes from `CI_VERSION_NAME`/`CI_VERSION_CODE` and the
  flavor suffix; automatic extraction with `Tags` is not yet proven.
- Add `Binaries` and `AllowedAPKSigningKeys` only if reproducible builds are
  enabled.
- Keep the `QUERY_ALL_PACKAGES` note in `MaintainerNotes`. It explains that the
  permission is used for the on-device full scan and not for installing apps.

The descriptive listing is taken from `fastlane/metadata/android/en-US/`:
`short_description.txt`, `full_description.txt`, `images/icon.png`,
`images/phoneScreenshots/`, and `changelogs/1.txt`.

## Submission

1. Fork `fdroiddata` and create a branch for `com.fossscout.app.fdroid`.
2. Copy the draft to `metadata/com.fossscout.app.fdroid.yml` and replace the
   commit placeholder.
3. In an F-Droid buildserver container, run:

   ```sh
   fdroid readmeta
   fdroid rewritemeta com.fossscout.app.fdroid
   fdroid lint com.fossscout.app.fdroid
   fdroid build com.fossscout.app.fdroid
   ```

4. Fix any metadata or build errors. The expected Gradle task is
   `assembleFdroidRelease`; the expected unsigned output is
   `app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk`.
5. Commit with `New App: com.fossscout.app.fdroid` and open a merge request.
6. After the metadata is merged, wait for the F-Droid build cycle and then
   install the published build for verification.

## Verification after publication

- The F-Droid listing shows package `com.fossscout.app.fdroid`, the stable
  version, and the expected icon and screenshots.
- The published APK is signed by the expected F-Droid or project key, not the
  beta or Play key.
- The full scan works, the scan stays local, and the app does not request
  install, uninstall, or accessibility permissions.
- Installing the new version over the previous F-Droid build preserves data.
- The [device smoke test](DEVICE_SMOKE_TEST.md) records the F-Droid build
  separately from the Play and GitHub beta builds.

## Open decisions

- Choose F-Droid signing (F-Droid key) or reproducible builds (dedicated project
  key). The latter needs a build-file change and approval.
- Confirm that the F-Droid buildserver has Android API 37 before submitting.
- Decide whether to add F-Droid-specific full-scan screenshots.
- Revisit `UpdateCheckMode` after the first stable build and metadata test.
