# F07 — Replacement Guidance

| Field | Value |
| --- | --- |
| Status | Draft (v1 guidance/handoff implemented in S08; direct install remains deferred to F09) |
| Availability | **General** (guidance); direct action depends on F09 |
| Profiles | `playstore` (handoff), `fdroid` (install), `direct` (install), `custom` |
| Required capabilities | `MANUAL_PACKAGE_SELECTION`; `DIRECT_APK_INSTALL` for in-app install |
| Depends on | F02, F09, F14 |
| Related | F11, F15 |

## 1. Problem

Switching an installed app to a FOSS build is not just “download and open”. The
signing certificate may differ, Android may require uninstalling the current app,
and uninstalling can destroy app data. Users need to understand the trade-off
before acting.

## 2. User stories

- As a user, I want to know whether the FOSS build can replace my current app
  without uninstalling it.
- As a user, I want to know what data I might lose.
- As a user, I want a safe handoff or install path, not a surprise.

## 3. Scope

- Compare package ID, signer, version code, and installer source.
- Detect same-package/different-signer conflicts.
- Explain uninstall/reinstall and data-loss risk.
- Offer safe actions: open store page, download, install, or export a reminder.
- Never uninstall automatically.
- Keep a record of what the user chose.

## 4. Non-goals

- Backing up another app's private data.
- Bypassing Android signature checks.
- Guaranteeing data migration between different apps.
- Silently replacing a system app.

## 5. Decision matrix

| Situation | Guidance |
| --- | --- |
| Same signer, newer version | In-place update may be possible; user confirms. |
| Same package, different signer | Uninstall required; warn about data loss. |
| Different package, same job | Different app; no in-place migration. |
| System app | Replacement may be impossible or unsafe; explain. |
| Installed from another store | Show installer source and update-ownership implications. |

## 6. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F07-R1 | MUST | Check signer and package identity before recommending an install. | A different-signer case shows an uninstall warning. |
| F07-R2 | MUST | Warn about possible data loss before any uninstall. | The warning is shown before the action, not after. |
| F07-R3 | MUST | Never uninstall or replace an app without explicit confirmation. | No code path performs a silent uninstall. |
| F07-R4 | MUST | Distinguish “update” from “replace” and “alternative”. | UI labels match the decision matrix. |
| F07-R5 | SHOULD | Show the installed and candidate version codes. | Both versions are visible in the comparison. |
| F07-R6 | SHOULD | Explain that Play and F-Droid builds may use different keys. | The explanation appears for store-crossing replacements. |
| F07-R7 | MAY | Offer a reminder/watchlist item instead of acting now. | User can defer and return later. |

## 7. Data

Installed package state, candidate store/version/signer, decision, timestamp.
No app content or personal data.

## 8. Edge cases

- Signature lineage / key rotation.
- Downgrade attempts.
- Split APKs.
- App installed for another user/work profile.
- Candidate APK is incompatible with the device ABI/SDK.
- User cancels the system installer.

## 9. Open questions

1. Should FossScout ever offer an in-app uninstall flow, or only deep-link to
   system settings?
2. How should it handle signature lineage that Android accepts but the index does
   not describe?
3. Should the user be able to export a “before I switch” checklist?
