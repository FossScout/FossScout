# Data safety draft

Status: ready to copy into Play Console after the app entry exists.

## Recommended answers

| Play Console question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Data types collected | **None** |
| Data types shared | **None** |
| Is all collected data encrypted in transit? | **Not applicable**; no user data is sent to the developer. Store-index requests use HTTPS. |
| Do you provide a way for users to request data deletion? | **No server-side data.** Local data can be deleted by clearing app data or uninstalling. Exported backups are controlled by the user. |
| Ads or analytics | **None** |
| Crash reporting | **None** |
| Account creation | **None** |

## Why the answer is "No"

- Favourites, notes, collections, settings, match corrections, cached indexes,
  and verification history are stored locally on the device.
- The app does not send installed-app inventory, notes, collections, device
  identifiers, or account data to the developer.
- The Play build does not request `QUERY_ALL_PACKAGES`, install packages, or use
  an accessibility service.
- Store-index sync contacts public F-Droid and IzzyOnDroid endpoints. Those
  third-party servers may receive standard request metadata such as an IP
  address, request time, and user agent, as with any HTTPS request. FossScout
  does not attach user data to those requests.
- Contribution reports are never uploaded automatically. The user reviews a
  redacted report and chooses whether to copy it, share it, or open a prefilled
  GitHub issue URL. If the user submits an issue, GitHub's privacy policy
  applies.
- Opening a store link or browser fallback hands the request to another app or
  website, whose privacy policy applies.

## Before submission

- Confirm no analytics, crash-reporting, advertising, or attribution SDK was
  added after this draft.
- Confirm the privacy policy is hosted at a public URL.
- Re-check the Play build manifest for `QUERY_ALL_PACKAGES` and other sensitive
  permissions.
- If the app changes to collect or share data, update this form before release.
