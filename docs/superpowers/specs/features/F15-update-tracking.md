# F15 — Update Tracking

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General + restricted enhancement** |
| Profiles | `playstore` (notify + handoff), `fdroid` (notify + install), `direct` (notify + install) |
| Required capabilities | `BACKGROUND_UPDATE_CHECK`; `DIRECT_APK_INSTALL` for in-app install |
| Depends on | F01, F05, F09 |
| Related | F07, F11 |

## 1. Problem

Direct APK downloads do not get store update notifications. Users who use
FossScout standalone, or who track apps they have not installed, need a way to
learn about new versions without constantly checking manually.

## 2. User stories

- As a user, I want to know when a favourited app gets a new version.
- As a user, I want to know when an app I installed through FossScout has an
  update.
- As a Play Store user, I want a notification that opens the store page.
- As a user, I do not want silent background installs.

## 3. Scope

- Compare installed/favourite version codes with the latest known index version.
- Notify only when the user opts in.
- Respect Android background execution and battery settings.
- Play profile: notify and hand off to the store; never install.
- F-Droid/direct profile: notify and offer install through F09.
- Show update source, version, and coverage/freshness.
- Let the user snooze or ignore a specific app.

## 4. Non-goals

- Silent auto-update.
- Updating apps installed by another store without the user's involvement.
- Checking stores that are disabled or unsupported.
- Guaranteeing an update exists when coverage is incomplete.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F15-R1 | MUST | Update checks are off by default. | A fresh install performs no background checks. |
| F15-R2 | MUST | The user can enable/disable checks per app and globally. | Settings are honoured across restarts. |
| F15-R3 | MUST | Notifications state the source and version. | Notification includes app, store, and version. |
| F15-R4 | MUST | Play profile never installs an update directly. | Action opens the store/browser instead. |
| F15-R5 | MUST | Stale or incomplete coverage does not produce a false “up to date” claim. | UI shows “unknown/incomplete” when data is stale. |
| F15-R6 | SHOULD | Checks respect metered/charging preferences. | No check runs on metered network when disallowed. |
| F15-R7 | SHOULD | The user can snooze an app or a version. | Snooze prevents repeat notification for that version. |
| F15-R8 | MAY | The app can show a changelog when the store provides one. | Changelog is displayed without leaving the app. |

## 6. Data

Watchlist entry: app key, source store, installed version, latest known version,
last check, snooze state, notification preference. No personal data.

## 7. Edge cases

- App installed from a different store than the tracked source.
- Version code not monotonic.
- Index stale.
- App removed from the store.
- User uninstalls the app but keeps the favourite.
- Android restricts background work.
- Multiple stores offer different versions.

## 8. Open questions

1. What is the default check interval if the user enables it?
2. Should updates be checked for all favourites or only installed apps by default?
3. Should the app compare version names as well as version codes?
4. How should conflicting versions from multiple stores be presented?
