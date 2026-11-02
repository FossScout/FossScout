# Device Smoke Test

Use this checklist before announcing a beta, uploading to Play internal testing,
cutting a stable release, or submitting to F-Droid. It covers the checks that
cannot be proven by JVM tests alone.

Feature specifications live in
[`superpowers/specs/features/`](superpowers/specs/features/README.md). Record the
build, device, and result for every run. Treat every unchecked item as a release
blocker unless the release owner records a specific exception.

## Builds

| Build | Package | Use |
| --- | --- | --- |
| GitHub beta | `com.fossscout.app.beta` | Beta APK from the GitHub prerelease |
| Play | `com.fossscout.app` | Play internal-testing AAB |
| F-Droid | `com.fossscout.app.fdroid` | F-Droid build with the full inventory scan |

Do not mix results between builds. The Play build uses manual package checks and
limited package visibility; the F-Droid build uses the full inventory scan.

## Run record

| Field | Value |
| --- | --- |
| Date | |
| Tester | |
| Build / package | |
| Version name / code | |
| Device / model | |
| Android version / API | |
| Profile (personal/work) | |
| Network | |
| Result | Pass / fail |
| Issues filed | |

## 1. Install and launch

- [ ] Install the build on a clean profile. The app launches without a crash.
- [ ] Install the same build over the previous version. Existing data remains.
- [ ] Launch from cold start and from recents. No blank screen or ANR.
- [ ] Rotate the device and change font scale. Content remains usable.
- [ ] Switch light/dark theme. Text and controls remain readable.
- [ ] Navigate every bottom destination and return with Back. No lost state or
      duplicate screens.
- [ ] Kill the process and relaunch. Persisted state is restored.
- [ ] Run with the network disabled. Offline screens explain the state and do not
      crash.

## 2. Inventory and installer source (F01)

- [ ] On Play, check one app by package name, one by label, and one by a shared
      store link. No full-scan action is offered.
- [ ] On F-Droid, run a full scan. The result reports complete or partial
      coverage and the last-scan time.
- [ ] Filter system, disabled, and work-profile apps. The list changes without a
      rescan.
- [ ] Hide and unhide an app. The choice survives a process restart.
- [ ] Leave a snapshot for more than the stale threshold. It is visibly marked
      stale.
- [ ] Open an installed app result and verify the version, installer, and signer
      rows. The installer row shows the raw installer package (for example
      `com.android.vending` or `org.fdroid.fdroid`).
- [ ] Check an app installed from Google Play, one from F-Droid, one sideloaded
      with an unknown installer, and one whose installer is not visible. The
      last two show the "installer unknown (possibly sideloaded)" fallback.
- [ ] Verify the installer source is shown only as context. It does not change
      the FOSS/Unknown/Unavailable classification.
- [ ] On a work profile, confirm the work-profile badge and profile separation.
- [ ] On F-Droid, confirm the full scan does not include packages the platform
      hides. Record any inaccessible-app count.

## 3. Store registry and index sync (F04/F05)

- [ ] F-Droid and IzzyOnDroid are present after a fresh install with current
      URLs and fingerprints.
- [ ] Add, edit, disable, reorder, and delete a custom store. Changes affect the
      next match without an app restart.
- [ ] Add an unsupported store. It is saved as link-only with a visible reason.
- [ ] Add an HTTP store. The warning and explicit opt-in are required.
- [ ] Sync a store over the network. Signature, fingerprint, hash, and size
      verification succeed.
- [ ] Sync again with an unchanged index. ETag/diff reuse avoids a full download
      where supported.
- [ ] Disable the network and open cached results. The app uses the cached
      snapshot and explains freshness.
- [ ] Cancel a sync. The previous complete snapshot remains usable.
- [ ] Set the storage budget below the index size. The app fails safely and
      reports the limit.
- [ ] Change a pinned fingerprint or simulate a signer mismatch. Sync blocks
      until the user approves the new fingerprint or removes the store.
- [ ] Open store security details. Verification state, source, fingerprint,
      index version, last sync, and failure reason are readable.

## 4. Matching, coverage, and alternatives (F02/F03/F06)

- [ ] Match an app with an exact package and FOSS metadata. It is shown as FOSS
      with evidence.
- [ ] Match an app with only a fork or variant. It is not claimed as the same
      app.
- [ ] Match an app with missing metadata. It stays Unknown with an explanation.
- [ ] Match an app absent from every enabled store. "Not found" is shown only
      when coverage is complete.
- [ ] Disable one store or use a stale index. The result is marked incomplete and
      does not claim "not found".
- [ ] Open the evidence card. Store, package, license, source, version, signer,
      and trust state are visible.
- [ ] Verify alternatives are labelled as different apps and are capped at five.
      Hide and undo work for the session.

## 5. Matching correction (F20)

- [ ] Search cached enabled-store catalogs for a correction target.
- [ ] Replace a match and confirm the corrected badge and retained automatic
      result.
- [ ] Mark an app as having no suitable match.
- [ ] Add a private note and reopen the correction. The note persists.
- [ ] Select a target that becomes unavailable. The warning is visible and the
      correction fails safely.
- [ ] Undo a correction.
- [ ] Export a backup with a correction, restore it, and confirm the override
      survives.

## 6. Replacement guidance and handoff (F07/F08)

- [ ] Compare an installed app with an update, a replacement, an alternative, and
      a no-update case.
- [ ] Verify signer, version, installer, data-loss, uninstall, system, and
      cross-store warnings.
- [ ] Confirm a signer-mismatch replacement requires explicit confirmation.
- [ ] Detect F-Droid, Droid-ify, and Neo Store when installed.
- [ ] Open a store app page with `market://details?id=<package>`.
- [ ] Add a repository by descriptor, QR, combined copy, URL copy, and
      fingerprint copy.
- [ ] Remove or disable the preferred client. The install and browser fallbacks
      work.
- [ ] Verify no install, uninstall, or delete action exists.

## 7. Favourites, collections, and sharing (F11/F12)

- [ ] Add and remove a favourite. Notes, tags, and preferred store persist.
- [ ] Search favourites by name, note, and tag.
- [ ] Install or uninstall an app while the Saved screen is open. The advisory
      badge updates after the documented refresh path.
- [ ] Create, rename, reorder, and edit a collection.
- [ ] Share a collection as JSON, text, and file. Notes are excluded by default.
- [ ] Import a collection with merge, replace, and skip. Conflicts are visible.
- [ ] Import an unsupported version. It is rejected without corrupting data.

## 8. Backup and migration (F13)

- [ ] Export an unencrypted backup through the system share sheet.
- [ ] Import it through the document picker.
- [ ] Restore with merge, replace, and skip. The preview counts match the result.
- [ ] Export and import an encrypted backup with the correct passphrase.
- [ ] Try a wrong passphrase and a corrupted checksum. Both fail safely.
- [ ] Restore custom-store fingerprints and trust state.
- [ ] Test Android auto-backup rules with the documented `sharedpref` and
      `file/datastore/` paths.
- [ ] Test a large profile and a failure during restore. The app reports the
      failure and leaves usable data.

## 9. Security details and trust (F14)

- [ ] Open security details for a verified store. Fingerprint, signature, hash,
      version, source, and last successful sync are visible.
- [ ] Simulate a fingerprint rotation. Approve it and confirm sync resumes.
- [ ] Remove the store instead of approving the rotation. The store is removed
      safely.
- [ ] Trigger a signer mismatch. Sync is blocked until resolved.
- [ ] Confirm the bounded verification history is readable and long fingerprints
      and URLs wrap without clipping.
- [ ] Confirm match evidence does not claim APK hash or signer continuity in v1.

## 10. Contribution reports (F16)

- [ ] Open the contribution form from Settings, Stores, and a match result.
- [ ] Preview a report. Inventory, notes, account data, identifiers, raw errors,
      query tokens, and local paths are absent.
- [ ] Copy and share the report while offline.
- [ ] Open the prefilled GitHub issue in the browser.
- [ ] Enable debug details. The exported JSON remains redacted.
- [ ] Disable the contribution feature. The contribution UI is gated.

## 11. Settings and feature configuration (F17)

- [ ] Toggle each feature. Its real UI is gated and the choice persists.
- [ ] Change the index storage budget. The value is validated and applied.
- [ ] Reset one feature and then reset all. Defaults are restored.
- [ ] Confirm the read-only settings snapshot matches the backup payload.

## 12. About and support (F22)

- [ ] The About screen shows the support section with the Liberapay link.
- [ ] Tapping the link opens the browser at
      `https://liberapay.com/FossScout/`.
- [ ] No payment SDK, donation prompt, or feature gating appears.

## 13. Play-specific checks

- [ ] The Play build has no `QUERY_ALL_PACKAGES`, install-packages permission, or
      accessibility service.
- [ ] Manual package checks work under Play package visibility.
- [ ] The full inventory scan is not offered on Play.
- [ ] The Play AAB is signed by the Play upload key, not the beta or F-Droid key.
- [ ] An internal tester can install the build from Play.
- [ ] The listing screenshots and copy match the Play build, not the F-Droid
      build.

## 14. F-Droid-specific checks

- [ ] The F-Droid build has the expected broad package visibility and performs
      the full scan.
- [ ] The F-Droid build installs and upgrades over the previous F-Droid build.
- [ ] The F-Droid signing key is separate from the beta and Play keys.
- [ ] The build matches the F-Droid metadata and reproducible-build plan.

## Exit criteria

- [ ] No crash, ANR, or data-loss defect remains.
- [ ] Every item above passes, or the release owner records a specific exception
      with a filed issue.
- [ ] Play and F-Droid package/signing constraints are verified for their builds.
- [ ] The run record and evidence are attached to the release or PR.
