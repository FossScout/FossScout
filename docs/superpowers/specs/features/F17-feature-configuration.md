# F17 — Feature Configuration

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | None |
| Depends on | Build profiles |
| Related | All features |

## 1. Problem

The user wants flexibility on every feature, while the app still needs sensible
defaults. Too many switches create a settings maze; too few force behaviour.
Optional features must also be easy to include or exclude per distribution.

## 2. User stories

- As a user, I want to turn off a feature I do not use.
- As a user, I want advanced options available without seeing them on first run.
- As a user, I want to reset a feature to its default.
- As a maintainer, I want to exclude an optional feature from a build without
  editing shared code.

## 3. Scope

- A generated settings surface from feature declarations.
- Basic and advanced sections.
- Per-feature enable/disable where meaningful.
- Safe defaults, with a visible “reset to default” action.
- An “unavailable in this build” explanation for excluded features.
- Import/export of settings through F13.
- Internal feature flags for development, not user-facing by default.

## 4. Non-goals

- A setting for every internal constant.
- Hiding core privacy protections behind an off switch.
- Letting a runtime toggle bypass a build-profile restriction.
- Play-only dynamic delivery.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F17-R1 | MUST | Every optional feature declares its settings and defaults. | The settings screen is generated from descriptors. |
| F17-R2 | MUST | Disabling a feature does not delete its data. | Re-enabling restores prior state. |
| F17-R3 | MUST | A feature excluded by the build profile cannot be enabled at runtime. | No hidden toggle can turn on a restricted capability. |
| F17-R4 | MUST | The UI explains why a feature is unavailable. | Play build shows a policy-safe reason, not a crash. |
| F17-R5 | MUST | Settings can be reset individually and globally. | Reset returns to declared defaults. |
| F17-R6 | SHOULD | Advanced settings are separated from everyday ones. | First-run screen shows only basic settings. |
| F17-R7 | SHOULD | Settings are included in the full backup. | Restore reproduces the configuration. |
| F17-R8 | MAY | A development build can override feature availability. | Overrides are visibly marked and absent from release builds. |

## 6. Data

Feature ID, enabled state, setting values, schema version, last changed. No
personal data beyond user preferences.

## 7. Edge cases

- Feature removed in a later version.
- Setting value no longer valid.
- Imported settings from a newer version.
- Profile change between installs.
- User disables a feature that another feature depends on.

## 8. Open questions

1. Which features get a user-facing toggle versus a fixed default?
2. Should there be a “minimal mode” that disables all optional features at once?
3. How are dependency conflicts between features resolved?
4. Should settings be searchable?
