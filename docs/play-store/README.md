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
| Privacy policy | `privacy-policy.md` | Needs a public URL |
| Data safety draft | `declarations/data-safety.md` | Recommended answers |
| Content rating draft | `declarations/content-rating.md` | Questionnaire prep |
| App access | `declarations/app-access.md` | No login |
| Store settings | `declarations/store-settings.md` | Category, tags, declarations |

## Account-gated

These cannot be completed without the Play Console account:

- Create the app entry for `com.fossscout.app`.
- Register the Play upload key and enroll in Play App Signing.
- Add the developer contact email.
- Host the privacy policy at a public URL.
- Submit the content rating questionnaire.
- Submit the data safety form.
- Create the internal testing track and add testers.
- Upload the signed AAB.
- Add tablet screenshots if the app is offered on tablets.
- Optionally add a promo video and additional languages.

## Play build scope

The Play build is `com.fossscout.app` with manual package checks and limited
package visibility. It does not request `QUERY_ALL_PACKAGES`, install packages,
or use an accessibility service. The F-Droid build adds the full installed-app
scan.

The listing copy and screenshots therefore describe manual app/package checks
rather than a full device scan. Do not use the inventory full-scan screenshots
from the ignored `screenshots/` directory for the Play listing.

## Graphics

The screenshots are derived from the existing ignored captures in
`screenshots/` and recomposed onto 1080 × 1920 canvases. The selected source
images are:

| Play screenshot | Source |
| --- | --- |
| `01-discover.png` | `01-discover-light.png` |
| `02-search.png` | `02-discover-search.png` |
| `03-foss-match.png` | `07-match-exact-result.png` |
| `04-stores.png` | `10-stores-overview.png` |
| `05-saved-projects.png` | `13-saved-favourites.png` |
| `06-collections.png` | `15-collections.png` |
| `07-contribution-preview.png` | `21-contribution-preview.png` |
| `08-settings.png` | `22-settings-basic.png` |

The screenshots contain sample data from the headless capture harness. Recapture
them after a UI change, and replace them with real-device screenshots when a
device is available.

## Before uploading

1. Host `privacy-policy.md` at a public HTTPS URL and add that URL in Play
   Console.
2. Confirm the Play build still has no `QUERY_ALL_PACKAGES`, accessibility
   service, or install-packages permission.
3. Confirm no analytics, ads, or crash-reporting SDK was added.
4. Build the Play AAB with the dedicated Play upload key, not the GitHub beta
   key.
5. Complete the [device smoke test](../DEVICE_SMOKE_TEST.md) with the Play build.
6. Re-check the listing text and screenshots against the Play build, not the
   F-Droid build.

See [`../PLAY_INTERNAL_TESTING.md`](../PLAY_INTERNAL_TESTING.md) for the AAB
workflow and signing setup.
