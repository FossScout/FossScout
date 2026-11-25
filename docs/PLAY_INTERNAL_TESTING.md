# Play Internal Testing

Play internal testing is the next distribution stage after the GitHub beta. The
four Play upload secrets are configured, and a signed AAB was built from
`main` on 2026-11-23. The Play Console app entry, declarations, testers, and
internal testing track remain pending; the public privacy policy and contact
email are ready. Automated upload is not configured.

The account-independent submission package is in
[`docs/play-store/`](play-store/README.md): store listing copy and graphics,
privacy-policy draft, and Play declarations. The listing copy has current F18,
F19, and F20 feature text; the declarations record that permission comparison,
tracker scanning, local APK manifest import, and audit export stay on-device.

## Current status

| Item | Status |
| --- | --- |
| AAB build workflow | Ready: `.github/workflows/play-internal.yml` |
| Play upload secrets | **Done**: `PLAY_KEYSTORE_FILE`, `PLAY_KEYSTORE_PASSWORD`, `PLAY_KEY_ALIAS`, and `PLAY_KEY_PASSWORD` configured on 2026-11-17 |
| Signed AAB | **Done**: [run 45251114616](https://github.com/FossScout/FossScout/actions/runs/45251114616) from `main` at `f9de6cf`; version `0.1.1`/code `2`; artifacts `app-playstore-release.aab` and `.sha256` |
| Play Console app entry and Play App Signing | Pending: app entry not created yet |
| Store listing copy and graphics | Prepared under `docs/play-store/` |
| App content and data safety declarations | Drafted under `docs/play-store/declarations/` |
| Privacy-policy URL and contact email | **Done**: <https://fossscout.github.io/FossScout/privacy/>; `kanzel88@gmail.com` |
| Internal testing track and testers | Pending |
| Device smoke test | CI emulator baseline passes (run [45629123988](https://github.com/FossScout/FossScout/actions/runs/45629123988) on `2915f4b`); physical-device Play build remains pending |

## Signing

Use a dedicated Play upload key. Do not reuse the GitHub beta signing key. A
dedicated key was generated on 2026-11-17 and stored outside the repository;
the four repository secrets are configured. The key alias is `play-upload`.

For a future key replacement, create it outside the repository:

```sh
keytool -genkeypair -v \
  -keystore play-upload.jks \
  -alias play-upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Add these repository secrets:

| Secret | Value |
| --- | --- |
| `PLAY_KEYSTORE_FILE` | Base64-encoded Play upload keystore |
| `PLAY_KEYSTORE_PASSWORD` | Play upload keystore password |
| `PLAY_KEY_ALIAS` | Play upload key alias |
| `PLAY_KEY_PASSWORD` | Play upload key password |

Encode the keystore without line wrapping:

```sh
base64 -w0 play-upload.jks
```

Keep the keystore and passwords out of the repository.

## Build the AAB

The `PLAY_*` repository secrets are configured. No signed AAB is committed. The
**Play Internal AAB** workflow ran on 2026-11-23 from `main` at `f9de6cf` and
produced a signed AAB for version `0.1.1`/code `2` with SHA-256
`eac35b4d6acb610cf24c1867fe0b675e191f299253cab65fcb913cb5af973da8`. The
signer certificate SHA-256 is
`74:C6:CD:F3:6A:57:6B:A5:3C:F7:60:86:75:CD:C3:A5:3C:CF:DD:E5:6E:22:8B:EB:BB:99:78:08:F5:55:3F:E4`.
The Play merged manifest still has only `INTERNET` and the AndroidX
dynamic-receiver permission, with no `QUERY_ALL_PACKAGES`. Run the workflow
manually from GitHub Actions from current `main` for future uploads. Provide:

- `version_name`: a Play version name that does not contain `-beta`
- `version_code`: a positive integer higher than every previously uploaded code

For the first internal upload, `version_name: 0.1.1` and `version_code: 2` match
the current build defaults. Use a higher version code if a build has already
been uploaded to this Play entry.

The workflow:

1. Checks out the repository and shared docs submodule.
2. Validates the Play signing secrets and version inputs.
3. Runs tests.
4. Builds `bundlePlaystoreRelease`.
5. Verifies the AAB with `jarsigner`.
6. Uploads the AAB and SHA-256 checksum as the `play-internal-aab` artifact.

The workflow does not upload to Play. Download the artifact and upload it
manually in Play Console.

## Before upload

- Confirm the AAB is from current `main` and CI passes.
- Confirm the package is `com.fossscout.app`, the version code is higher than
  every previous upload, and the version name has no `-beta` suffix.
- Confirm the Play upload key signed the AAB, not the beta key.
- Run the device smoke test with the Play build. The CI emulator baseline is
  current; physical-device checks remain release blockers.
- Review the store listing and declarations under `docs/play-store/` against
  the uploaded build.
- Confirm the published privacy policy is live at
  <https://fossscout.github.io/FossScout/privacy/> and add that URL and
  `kanzel88@gmail.com` in Play Console.

## Play Console setup

The app entry does not exist yet. Complete these in order in Play Console:

1. Create the app entry for `com.fossscout.app`.
2. Enroll in Play App Signing with a Google-generated app signing key and
   register the Play upload key. The upload certificate fingerprint is recorded
   in the build section above.
3. Complete app content declarations, data safety, store listing, and privacy
   policy requirements using `docs/play-store/declarations/` and
   `docs/play-store/listing/`.
4. Create the internal testing track and add testers.
5. Upload the AAB from the workflow artifact to internal testing.

## Later automation

Automated upload requires a Google Play service account with Play Developer API
access and an upload action or dependency. That is intentionally not added yet.

## Verification

- The AAB is built from current `main` and signed by the Play upload key, not the
  beta key.
- The package name is `com.fossscout.app`.
- The version code is higher than the previous upload.
- The store listing copy matches the uploaded build, including F18 cross-store
  search and F19 permission/tracker comparison, local APK manifest import, and
  audit export.
- Complete the [device smoke test](DEVICE_SMOKE_TEST.md) with the Play build.
- Internal testers can install the build.
- The uploaded artifact matches the SHA-256 recorded next to the AAB.
