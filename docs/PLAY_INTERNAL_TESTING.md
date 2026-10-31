# Play Internal Testing

Play internal testing is the next distribution stage after the GitHub beta. The
repository can build a signed AAB for manual upload, but the Play Console setup
and automated upload are not complete.

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

Run the **Play Internal AAB** workflow manually from GitHub Actions. Provide:

- `version_name`: a Play version name that does not contain `-beta`
- `version_code`: a positive integer higher than every previously uploaded code

The workflow:

1. Checks out the repository and shared docs submodule.
2. Validates the Play signing secrets and version inputs.
3. Runs tests.
4. Builds `bundlePlaystoreRelease`.
5. Verifies the AAB with `jarsigner`.
6. Uploads the AAB and SHA-256 checksum as the `play-internal-aab` artifact.

The workflow does not upload to Play. Download the artifact and upload it
manually in Play Console.

## Play Console setup

Complete these once in Play Console:

1. Create the app entry for `com.fossscout.app`.
2. Enroll in Play App Signing and register the Play upload key.
3. Complete app content declarations, data safety, store listing, and privacy
   policy requirements.
4. Create the internal testing track and add testers.
5. Upload the AAB from the workflow artifact to internal testing.

## Later automation

Automated upload requires a Google Play service account with Play Developer API
access and an upload action or dependency. That is intentionally not added yet.

## Verification

- The AAB is signed by the Play upload key, not the beta key.
- The package name is `com.fossscout.app`.
- The version code is higher than the previous upload.
- Internal testers can install the build.
- The uploaded artifact matches the workflow SHA-256 checksum.
