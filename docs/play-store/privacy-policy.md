# FossScout Privacy Policy

**Effective date:** 2026-11-01

This policy applies to the FossScout Android app (`com.fossscout.app`) and the
FossScout project. FossScout is free software maintained by FossScout
contributors.

## Summary

FossScout is local-first. It does not require an account and does not include
advertising, analytics, crash reporting, or tracking SDKs. The developer does
not collect, sell, or share personal data from the app.

## Information stored on your device

FossScout stores the following locally on your device:

- App settings and feature toggles.
- Favourites, private notes, tags, collections, and their order.
- Custom store entries, pinned fingerprints, and trust notes.
- Match corrections and preferred store clients.
- Cached store index data, verification history, and sync metadata.
- Backup files you choose to export.

This information stays on your device unless you explicitly export it, share
it, or include it in an Android system backup.

## Installed-app information

FossScout can read package metadata for apps visible to it in order to check
matches and show local inventory. This information is processed on the device
and is not sent to the developer.

The Play build uses manual package checks and limited package visibility. It
does not request `QUERY_ALL_PACKAGES`, install packages, or use an accessibility
service. The F-Droid build adds a full installed-app scan for users who want it.

## Network requests

FossScout uses network access to:

- Download public store indexes from F-Droid and IzzyOnDroid.
- Open links you choose, such as store pages, project pages, or the issue
  tracker.

When the app contacts a third-party server, that server may receive standard
request metadata such as your IP address, request time, and user agent, as with
any HTTPS request. FossScout does not attach your installed-app inventory,
notes, favourites, collections, or other local profile data to store-index
requests.

If you open a link or submit a prefilled GitHub issue, the destination service's
privacy policy applies.

## Contribution reports

Contribution reports are generated locally from a redacted preview. Nothing is
uploaded automatically. You choose whether to copy the report, share it through
the system share sheet, or open a prefilled GitHub issue URL. If you submit an
issue, GitHub processes the content you submit under GitHub's privacy policy.

## Backups and sharing

You can export a FossScout backup containing your local profile data. You can
optionally protect it with a passphrase. You control where the backup is saved
and who receives it. If you share a file or report through Android's share
sheet, the receiving app's privacy policy applies.

If Android system backup is enabled on your device, Android may include
FossScout's local app data in a device backup. That backup is handled by Android
and your device provider; you can disable system backup in Android settings.

## Permissions

FossScout requests:

- **Internet** for store-index sync and links you open.
- **Package queries** for F-Droid, Droid-ify, and Neo Store so companion-store
  detection works without broad package visibility.

FossScout does not request location, contacts, camera, microphone, SMS, call
log, broad storage, accessibility, or install-packages permissions.

## Children

FossScout is not directed to children under 13. The app does not knowingly
collect personal information from children.

## Data deletion

FossScout does not maintain server-side user data. To delete local data, clear
the app's storage in Android settings or uninstall the app. Exported backup
files remain under your control and must be deleted separately if you no longer
want them.

## Changes

If this policy changes, the updated version will be published in the FossScout
repository with a new effective date.

## Contact

For privacy questions, open an issue at
<https://github.com/FossScout/FossScout/issues>.
