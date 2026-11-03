# Screenshot task checkpoints

## Environment

- [x] Confirm the debug APK exists (`app/build/outputs/apk/playstore/debug/app-playstore-debug.apk`)
- [x] Attempt emulator boot
  - Blocked: the emulator is a glibc binary and segfaults on this Alpine host
    (no KVM)
- [x] Switch to headless Compose screenshot rendering (Android screenshot-test
  tooling)
- [x] Preload `/lib/libgcompat.so.0` for the Gradle/test JVM so layoutlib loads
  on this Alpine/musl host
- [x] Use `com.android.compose.screenshot` `0.0.1-alpha10`; newer validation
  APIs require a newer Kotlin compiler than this project uses
- [x] Populate null `android.os.Build` fields before Compose composition
  (`Build.FINGERPRINT` is read during lazy-list prefetch initialization)

## Capture set

Standard capture: 411 x 891 dp (1079 x 2339 px). Taller canvases are used only
where the content needs them.

- [x] 01 Discover light
- [x] 02 Discover search
- [x] 03 Discover category
- [x] 04 Discover dark
- [x] 05 Inventory overview
- [x] 06 Inventory lookup
- [x] 07 Match exact result
- [x] 08 Match coverage gap
- [x] 09 Companion handoff with QR (411 x 1200 dp)
- [x] 10 Stores overview
- [x] 11 Stores security details
- [x] 12 Stores editor
- [x] 13 Saved favourites
- [x] 14 Saved note editor
- [x] 15 Collections
- [x] 16 Collection detail
- [x] 17 Share collection
- [x] 18 Backup export (411 x 900 dp)
- [x] 19 Backup restore preview (411 x 2100 dp)
- [x] 20 Contribution form (411 x 900 dp)
- [x] 21 Contribution preview (411 x 1000 dp)
- [x] 22 Settings basic (411 x 900 dp)
- [x] 23 Settings advanced
- [x] 24 About

## Verification

- [x] Visually inspect every generated image
- [x] Verify output file list and dimensions (24 files)
- [x] Confirm the real six-destination bottom navigation is present on every
  capture
- [x] Confirm light and dark themes render
- [x] Confirm dialogs render in the headless screenshot harness (note editor,
  share, store security, store editor)
- [x] Confirm the real report builder output appears in the contribution preview
- [x] Confirm the backup restore preview shows counts, included/excluded lists,
  and merge/replace/skip controls
- [x] Restore `app/build.gradle.kts`, `gradle.properties`, and
  `gradle/libs.versions.toml`
- [x] Remove the temporary `app/src/screenshotTest` harness
- [x] Re-run `./gradlew test assemblePlaystoreDebug assembleFdroidDebug` after
  restoring build files
- [x] Present screenshots from the tracked `screenshots/` directory

## Observed UI notes

- [ ] Six bottom-navigation labels wrap at 360 dp. They fit at the 411 dp capture
  width. Needs a release UX decision, not changed in this task.
- [ ] Long package names wrap onto two lines in Saved-project cards at 411 dp.
  Needs a layout decision, not changed in this task.
- [ ] The backup restore preview sits below the export/import cards, so that
  capture is intentionally tall.

## Not verified

- [ ] Real device/emulator rendering, PackageManager, intents, clipboard, share
  sheet, document picker, QR scanning, and browser fallback
- [ ] Live F-Droid/IzzyOnDroid network sync
