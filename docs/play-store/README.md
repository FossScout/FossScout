# Google Play preparation

This directory contains the account-independent parts of the Google Play
listing. It does not contain a Play Console account, a service account, or
`PLAY_*` secrets.

## Ready

| Item | Path | Notes |
| --- | --- | --- |
| App title | `listing/en-US/title.txt` | 25 characters |
| Short description | `listing/en-US/short-description.txt` | 71 characters |
| Full description | `listing/en-US/full-description.txt` | Under 4,000 characters |
| Internal release notes | `listing/en-US/release-notes-internal.txt` | Under 500 characters |
| App icon | `graphics/app-icon-512.png` | 512 × 512 PNG, no alpha |
| Feature graphic | `graphics/feature-graphic-1024x500.png` | 1024 × 500 PNG, no alpha |
| Phone screenshots | `graphics/phone-screenshots/` | 8 images, 1080 × 1920 |
| Privacy policy | `privacy-policy.md` | Published at <https://fossscout.github.io/FossScout/privacy/> |
| Data safety draft | `declarations/data-safety.md` | Recommended answers |
| Content rating draft | `declarations/content-rating.md` | Questionnaire prep |
| App access | `declarations/app-access.md` | No login |
| Store settings | `declarations/store-settings.md` | Category, tags, declarations |

The published GitHub Pages copy is
[`docs/privacy/index.html`](../privacy/index.html). Update it together with the
Markdown source when the policy changes.

## Account-gated

These cannot be completed without the Play Console account:

- Create the app entry for `com.fossscout.app`.
- Register the Play upload key and enroll in Play App Signing.
- Add the developer contact email.
- Add the published privacy-policy URL in Play Console.
- Submit the content rating questionnaire.
- Submit the data safety form.
- Create the internal testing track and add testers.
- Upload the signed AAB.
- Add tablet screenshots if the app is offered on tablets.
- Optionally add a promo video and additional languages.

## Play build scope

The Play build is `com.fossscout.app` with manual package checks and limited
package visibility. A launcher-intent query makes user-facing apps visible to
those checks. It does not request `QUERY_ALL_PACKAGES`, install packages, or use
an accessibility service. The F-Droid build adds the full installed-app scan.

The listing copy and screenshots therefore describe manual app/package checks
rather than a full device scan. Do not use the inventory full-scan screenshots
from the tracked `screenshots/` directory for the Play listing.

## Graphics

The screenshots are composed onto 1080 × 1920 canvases. Seven are derived from
the tracked headless captures in `screenshots/`; the Stores screenshot is
generated from the passing main-branch Device Smoke Test. The selected source
images are:

| Play screenshot | Source |
| --- | --- |
| `01-discover.png` | `01-discover-light.png` |
| `02-search.png` | `02-discover-search.png` |
| `03-foss-match.png` | `07-match-exact-result.png` |
| `04-stores.png` | Device Smoke Test run [43853540921](https://github.com/FossScout/FossScout/actions/runs/43853540921), commit `d11a2a7`, `store-screenshot` artifact |
| `05-saved-projects.png` | `13-saved-favourites.png` |
| `06-collections.png` | `15-collections.png` |
| `07-contribution-preview.png` | `21-contribution-preview.png` |
| `08-settings.png` | `22-settings-basic.png` |

Seven screenshots contain sample data from the headless capture harness. The
Stores screenshot uses the fresh-install emulator state from the main-branch
run. Recapture the rest after a UI change, and replace them with real-device
screenshots when a device is available.

The Stores graphic is generated from a `main`-branch Device Smoke Test run.
Dispatch the workflow from `main` with `publish_store_screenshot` enabled,
download the `store-screenshot` artifact, and commit `04-stores.png` and `4.png`.
The composition is performed by
[`compose_store_screenshot.py`](../../.github/scripts/compose_store_screenshot.py);
do not edit the images by hand.

## Before uploading

1. Confirm the published privacy policy is live at
   <https://fossscout.github.io/FossScout/privacy/> and add that URL in Play
   Console.
2. Confirm the Play build still has no `QUERY_ALL_PACKAGES`, accessibility
   service, or install-packages permission.
3. Confirm no analytics, ads, or crash-reporting SDK was added.
4. Build the Play AAB with the dedicated Play upload key, not the GitHub beta
   key; follow [Play internal testing](../PLAY_INTERNAL_TESTING.md).
5. Complete the [device smoke test](../DEVICE_SMOKE_TEST.md) with the Play build.
6. Re-check the listing text, declarations, and screenshots against the Play
   build, not the F-Droid build.

See [`../PLAY_INTERNAL_TESTING.md`](../PLAY_INTERNAL_TESTING.md) for the AAB
workflow and signing setup.
