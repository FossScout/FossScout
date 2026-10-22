# F10 — Install-Time Suggestion

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **Restricted** |
| Profiles | `fdroid`, `direct`, `custom` (never `playstore`) |
| Required capabilities | `ACCESSIBILITY_INSTALL_PROMPT`, `ACCESSIBILITY_SERVICE`, `MANUAL_PACKAGE_SELECTION` |
| Depends on | F01, F02, F08 |
| Related | F06, F07 |

## 1. Problem

The best moment to offer a FOSS alternative is when the user is already in a
store, about to install the proprietary app. Detecting that moment generally
requires an AccessibilityService, which Google Play restricts. This feature is
therefore a restricted-build capability, not a general one.

## 2. User stories

- As a user, I want FossScout to offer a FOSS alternative when I am about to
  install a proprietary app.
- As a user, I want the prompt to be quiet, optional, and easy to dismiss.
- As a user, I want a manual “check this app” path when detection fails.

## 3. Scope

- Opt-in accessibility service, disabled by default.
- Observe only supported store/install screens, not all app content.
- Identify the package being viewed/installed where the UI exposes it.
- Show a small, dismissible suggestion when a verified FOSS match exists.
- Offer “view alternative”, “not now”, and “don’t suggest this app again”.
- Provide a manual fallback.

## 4. Non-goals

- Reading passwords, messages, or unrelated screen content.
- Automatically clicking install, uninstall, or purchase buttons.
- Hiding or blocking the original install button.
- Play Store distribution.
- Working when the accessibility service is disabled.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F10-R1 | MUST | The service is opt-in, with a prominent in-app disclosure before enabling. | No service is enabled without explicit consent. |
| F10-R2 | MUST | The service is absent from the `playstore` profile. | A Play build contains no accessibility service declaration. |
| F10-R3 | MUST | Only package identity and visible store context are processed; no unrelated screen text is stored. | A review of the data model shows no free-form screen capture storage. |
| F10-R4 | MUST | The prompt never performs an install or navigates automatically. | All actions require a user tap. |
| F10-R5 | MUST | The user can dismiss, disable, or suppress prompts per app. | Suppression survives restart. |
| F10-R6 | SHOULD | Suggestions appear only for verified matches above a confidence threshold. | Low-confidence matches do not trigger a prompt. |
| F10-R7 | SHOULD | A manual “check this app” fallback is always available. | The feature works without accessibility when invoked manually. |
| F10-R8 | MAY | The user can choose overlay, notification, or both. | The chosen presentation is honoured. |

## 6. Data and privacy

Processed data stays on-device: package name, store package, match result,
prompt decision. No screen text, keystrokes, or personal content is retained.
Diagnostics are opt-in and exclude screen content.

## 7. Edge cases

- OEM installer UI differs.
- Play Store UI changes or uses WebView.
- Multiple windows / split screen.
- Accessibility service killed by the system.
- Package not visible in the index or coverage incomplete.
- User is updating an already-installed app rather than installing a new one.
- Work profile.

## 8. Open questions

1. Which stores' install flows are in scope for v1?
2. Overlay, notification, or both by default?
3. Should the prompt appear for updates as well as fresh installs?
4. How is the “verified match” threshold defined?
5. What is the Play-safe equivalent, if any: a share target or a manual check?
