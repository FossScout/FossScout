# F-Droid Release Readiness

F-Droid is the second distribution channel after the GitHub beta. The source
build, listing metadata, and submission metadata are prepared. The stable
`v0.1.0` tag exists, but the `fdroiddata` merge request is not open yet.

The GitHub beta channel remains at `v0.1.0-beta.3`. The stable tag is for the
F-Droid source build only; it does not create a GitHub release or enable Play.

Do not publish the GitHub beta APK or the Play AAB to F-Droid. The F-Droid
variant is a separate package built from source with the `fdroid` flavor.

## Current state

| Item | Status |
| --- | --- |
| F-Droid flavor | Ready: `com.fossscout.app.fdroid`, full installed-app scan |
| Stable tag | `v0.1.0` at `e4262a13eb6eab09ff2fa4674e3f4d705999053c` |
| F-Droid release build | Verified locally from the tag on 2026-11-05 |
| Fastlane listing metadata | Added under `fastlane/metadata/android/en-US/` |
| `fdroiddata` metadata | Prepared in [`docs/fdroid/com.fossscout.app.fdroid.yml`](fdroid/com.fossscout.app.fdroid.yml) and points at the stable commit |
| F-Droid signing | F-Droid-hosted signing; no project key |
| F-Droid submission | `fdroiddata` merge request not opened |

## Verified local build

From the `v0.1.0` tag on 2026-11-05:

```sh
./gradlew assembleFdroidRelease
```

The build succeeded and produced an **unsigned** APK:

- Path: `app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk`
- Package: `com.fossscout.app.fdroid`
- Version name: `0.1.0-fdroid`
- Version code: `1`
- SHA-256: `7ac11a2b7c861609b207f1aef5e53c948faa254d43a7255112bb895b20d7d8da`

The merged manifest contains `QUERY_ALL_PACKAGES` and `INTERNET`, plus
AndroidX's dynamic-receiver permission. It does not contain install, uninstall,
or accessibility entries. The Play flavor keeps limited package visibility and
does not declare `QUERY_ALL_PACKAGES`.

This is a local build check, not a distributable artifact. The build is not a
reproducible artifact; the checksum is a local verification value. F-Droid will
build and sign its own APK.

## Before submission

1. **The stable tag is ready.** `v0.1.0` points at
   `e4262a13eb6eab09ff2fa4674e3f4d705999053c`, and the F-Droid build uses
   version name `0.1.0-fdroid` and version code `1`. The tag does not trigger
   the beta release workflow or create a GitHub release.
2. **Confirm the F-Droid build server supports API 37.** The app uses
   `compileSdk = 37`. Check the current buildserver image before opening the
   submission; if platform 37 is unavailable, wait for it rather than lowering
   the SDK only for F-Droid.
3. **Review the listing.** The Fastlane text and graphics are in
   `fastlane/metadata/android/en-US/`. The screenshots are shared with the Play
   listing. Recapture the full-scan screen from the F-Droid build if it adds
   useful evidence.
4. **Use F-Droid-hosted signing.** Do not reuse the beta or Play keys. See
   [Signing](#signing).
5. **Recheck the manifest and permissions.** The F-Droid build intentionally
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

Use [`docs/fdroid/com.fossscout.app.fdroid.yml`](fdroid/com.fossscout.app.fdroid.yml).
The prepared file already contains:

- `commit: e4262a13eb6eab09ff2fa4674e3f4d705999053c`, the full stable commit
  hash rather than the tag name;
- `versionName: '0.1.0-fdroid'` because the `fdroid` flavor appends the suffix;
- `versionCode: 1`;
- `UpdateCheckMode: Static` until automatic version extraction is tested;
- a `QUERY_ALL_PACKAGES` explanation in `MaintainerNotes`;
- no `Binaries` or `AllowedAPKSigningKeys` because F-Droid signs the build.

The descriptive listing is taken from `fastlane/metadata/android/en-US/`:
`short_description.txt`, `full_description.txt`, `images/icon.png`,
`images/phoneScreenshots/`, and `changelogs/1.txt`.

## Submission

The project does not have GitLab access in this environment. Opening the
`fdroiddata` merge request is a manual step.

1. Fork `fdroiddata` and create a branch for `com.fossscout.app.fdroid`.
2. Copy the prepared metadata to `metadata/com.fossscout.app.fdroid.yml`.
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

- The F-Droid listing shows package `com.fossscout.app.fdroid`, version
  `0.1.0-fdroid`, and the expected icon and screenshots.
- The published APK is signed by F-Droid, not the beta or Play key.
- The full scan works, the scan stays local, and the app does not request
  install, uninstall, or accessibility permissions.
- Installing the new version over the previous F-Droid build preserves data.
- The [device smoke test](DEVICE_SMOKE_TEST.md) records the F-Droid build
  separately from the Play and GitHub beta builds.

## Open items

- Confirm that the F-Droid buildserver has Android API 37 before submitting.
- Open the `fdroiddata` merge request and respond to review.
- Decide whether to add F-Droid-specific full-scan screenshots.
- Revisit `UpdateCheckMode` after the first stable build and metadata test.
