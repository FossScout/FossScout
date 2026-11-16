# Play Internal Testing

Play internal testing is the next distribution stage after the GitHub beta. The
repository can build a signed AAB for manual upload, but the Play upload
secrets, Play Console setup, and automated upload are not complete.

The account-independent submission package is in
[`docs/play-store/`](play-store/README.md): store listing copy and graphics,
privacy-policy draft, and Play declarations. The listing copy has current F18,
F19, and F20 feature text; the declarations record that permission comparison,
tracker scanning, local APK manifest import, and audit export stay on-device.

## Current status

| Item | Status |
| --- | --- |
| AAB build workflow | Ready: `.github/workflows/play-internal.yml` |
| Play upload secrets | Pending: `PLAY_KEYSTORE_FILE`, `PLAY_KEYSTORE_PASSWORD`, `PLAY_KEY_ALIAS`, `PLAY_KEY_PASSWORD` |
| Play Console app entry and Play App Signing | Pending |
| Store listing copy and graphics | Prepared under `docs/play-store/` |
| App content and data safety declarations | Drafted under `docs/play-store/declarations/` |
| Privacy-policy URL and contact email | Pending |
| Internal testing track and testers | Pending |
| Device smoke test | CI emulator baseline passes; physical-device Play build remains pending |

## Signing

Use a dedicated Play upload key. Do not reuse the GitHub beta signing key.

Create the key outside the repository:

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

The `PLAY_*` repository secrets are not configured yet. No signed AAB is
committed. After the secrets are configured, run the **Play Internal AAB**
workflow manually from GitHub Actions from current `main`. Provide:

- `version_name`: a Play version name that does not contain `-beta`
- `version_code`: a positive integer higher than every previously uploaded code

For the first internal upload, `version_name: 0.1.0` and `version_code: 1` match
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
- Host `docs/play-store/privacy-policy.md` and add the URL and contact email in
  Play Console.

## Play Console setup

Complete these once in Play Console:

1. Create the app entry for `com.fossscout.app`.
2. Enroll in Play App Signing and register the Play upload key.
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
