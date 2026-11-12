# Tracker Dataset and Local Scan Policy

F19 compares installed app permissions and known tracker code signatures. The
tracker dataset is bundled with the app; the app does not fetch tracker data or
upload scan results.

## Bundled snapshot

| Field | Value |
| --- | --- |
| Dataset | Exodus Privacy ETIP tracker signatures |
| Version | `2026-11-10` |
| Source | <https://etip.exodus-privacy.eu.org/api/trackers/> |
| License | ODbL 1.0 / DbCL 1.0 |
| Attribution | Tracker data from Exodus Privacy ETIP, licensed under ODbL 1.0 and DbCL 1.0. |
| Snapshot size | 674 trackers / 987 code signatures |
| Resource | [`core/matching/src/main/resources/trackers/exodus-etip.json`](../core/matching/src/main/resources/trackers/exodus-etip.json) |

The resource is a filtered snapshot of the ETIP API. It keeps the tracker name,
categories, website, code signatures, and a confidence marker. It does not
include tracker descriptions, network signatures, or other ETIP metadata.

### Confidence

`KNOWN` means the ETIP entry is in the Exodus tracker list and is not marked for
rework. `CANDIDATE` means the entry is in ETIP but is not listed by Exodus or is
marked for rework. This is confidence in the dataset entry, not a claim about
runtime behaviour.

## License and attribution

The app code remains under the repository's MIT license. The bundled dataset is
separate data under the Open Database License (ODbL) 1.0, with database contents
under the Database Contents License (DbCL) 1.0. Attribution and share-alike
obligations apply to the dataset.

The license texts are included in:

- [`docs/licenses/ODbL-1.0.txt`](licenses/ODbL-1.0.txt)
- [`docs/licenses/DbCL-1.0.txt`](licenses/DbCL-1.0.txt)

The comparison screen shows the dataset name, version, license, source, and
attribution. It links to the dataset source and license.

## Local scan policy

The tracker scan is user-initiated and local-only:

- It starts only when the user opens the permission comparison, and only for the
  installed app selected for that comparison.
- It reads the app's already-local base and split APK files resolved through
  `PackageManager`.
- It scans only `classes*.dex` entries. It does not download an APK, load
  classes, or execute app code.
- It runs on a background dispatcher with a 64 MiB scan cap. Hitting the cap
  produces an incomplete result, not a clean result.
- It never scans the store alternative. FossScout does not download APKs for
  this feature.
- APK bytes, class names, and match results are not uploaded or persisted.
- A signature match is static evidence that a class signature is present. It is
  not proof that the tracker runs or sends data. A missing match is not proof
  that the app has no trackers.
- An unreadable APK or an APK with no DEX files produces an unavailable result.

## Limitations

The scan cannot see trackers or tracking behaviour that is:

- in native code or resources rather than DEX class descriptors;
- loaded from a remote or dynamically generated source;
- obscured by renaming or other code transformation;
- outside the 64 MiB scan cap;
- in an app whose APK files the platform does not expose.

Shared libraries and short or broad signatures can produce a match that is not
specific to one tracker. The UI shows the matched signature and the dataset-entry
confidence so the evidence can be inspected.

## Updating the snapshot

The snapshot is updated manually, never at runtime. The update script uses only
the Python standard library and the ETIP API; it is not part of the app build or
CI.

1. Review the current ETIP licence and the upstream tracker changes.
2. From the repository root, run:

   ```sh
   python3 tools/update_tracker_dataset.py --date YYYY-MM-DD
   ```

3. Run the tracker tests and the normal build verification.
4. Update the snapshot table above and any status documentation that names the
   previous version.

The script sorts trackers and keeps the output deterministic. Do not hand-edit
individual signatures in the generated JSON.
