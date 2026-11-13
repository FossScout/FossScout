# F19 device-smoke fixture

This is a test-only F-Droid v2 index used by the manual `Device Smoke Test`
workflow. It lets the emulator match the installed debug builds without
depending on a live repository's contents.

The checked-in `repo/` directory contains a signed `entry.jar` and its
`index-v2.json`. The signer is a disposable self-signed certificate; the private
key is not committed. The smoke test adds the repository without a pinned
fingerprint, so the app records the index as unverified but activatable.

Fixture packages:

- `com.fossscout.app`
- `com.fossscout.app.vanilla`
- `dev.fossscout.smoke.tracker`

The tracker package is built at workflow time by
`build_f19_fixture_apk.py`. It contains a `com.aarki` class so the real tracker
scanner has one positive signature match. The other two packages exercise the
no-match state.

Regenerate the signed index after changing fixture packages or permissions:

```bash
python3 .github/scripts/build_f19_fixture_repo.py \
  --output .github/fixtures/f19/repo \
  --java-home "$JAVA_HOME"
```

The workflow serves this directory from
`https://raw.githubusercontent.com/<repository>/<commit>/.github/fixtures/f19/repo`,
so it does not require a local HTTP server or cleartext traffic.
