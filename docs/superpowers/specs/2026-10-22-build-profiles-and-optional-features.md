# Build Profiles and Optional Features

Status: **Draft**
Date: 2026-10-22
Related: `2026-10-22-fossscout-feature-scope-design.md`

## 1. Why not two hardcoded builds

The Play/F-Droid split is a policy split, not the only possible split. A
store-specific build, a direct-download build, or a future distribution channel
may need a different feature set. Hardcoding `if (isPlayBuild)` throughout the
app would make every new variant a fork.

Instead, every variant is a **profile** with a set of **capabilities**. Features
declare the capabilities they need. A feature is included only when the profile
satisfies it, and the UI explains why a feature is unavailable rather than
silently hiding it.

## 2. Profiles

| Profile | Distribution | Policy posture | Notes |
| --- | --- | --- | --- |
| `playstore` | Google Play | Strict Play compliance | No accessibility install interception, no direct APK installation, broad package visibility only if approved. |
| `fdroid` | F-Droid | F-Droid policy; full FOSS feature set | Accessibility service and standalone install are allowed. |
| `direct` | Project website / sideload | No store policy; same trust model as F-Droid | Useful for testing and for users who do not use a store. |
| `custom` | Any future store | Defined by its capability set | A store-specific flavor must declare capabilities, not copy code. |

A store-specific flavor (for example an IzzyOnDroid-oriented build) should reuse
`fdroid` capabilities unless it genuinely differs. Package ID, signing key, and
store metadata are release concerns, not feature concerns.

## 3. Capabilities

| Capability | Meaning | Usually available in |
| --- | --- | --- |
| `BROAD_PACKAGE_VISIBILITY` | Enumerate all installed packages | `fdroid`, `direct`; `playstore` only with approval |
| `MANUAL_PACKAGE_SELECTION` | User chooses or types an app to check | All profiles |
| `ACCESSIBILITY_INSTALL_PROMPT` | Observe another app's install flow and offer an alternative | `fdroid`, `direct` |
| `DIRECT_APK_INSTALL` | Download and invoke the system installer | `fdroid`, `direct` |
| `BACKGROUND_UPDATE_CHECK` | Periodically compare installed apps with indexes | All profiles, subject to OS limits |
| `STORE_INDEX_NETWORK` | Fetch store indexes or APIs | All profiles |
| `SHARE_IMPORT_EXPORT` | Share, import, and back up local data | All profiles |
| `CUSTOM_STORE_ADAPTERS` | Add user-defined stores and adapters | All profiles |
| `SOURCE_REPOSITORY_ADAPTERS` | Track GitHub/GitLab/Codeberg/direct APK sources | All profiles |
| `ACCESSIBILITY_SERVICE` | Declare an Android AccessibilityService | `fdroid`, `direct` |

## 4. Feature declaration

Every optional feature module exposes a declaration like:

```kotlin
data class FeatureDescriptor(
    val id: String,
    val title: String,
    val availability: Availability,
    val requiredCapabilities: Set<Capability>,
    val defaultEnabled: Boolean,
    val settings: List<FeatureSetting>,
)
```

`Availability` values:

- `GENERAL` — safe for every profile, including Play.
- `RESTRICTED` — requires a restricted capability; excluded from `playstore`.
- `GENERAL_WITH_RESTRICTED_ENHANCEMENT` — a Play-safe baseline plus extra
  behaviour when the capability exists.

The generated `FeatureRegistry` is the single source of truth for navigation,
settings, and “why is this unavailable?” messages.

## 5. Module layout

Optional features are separate Gradle modules with a small contract in `core`:

```text
:core:model
:core:data
:core:ui
:core:store-api
:core:feature-api

:feature:inventory
:feature:matching
:feature:alternatives
:feature:stores
:feature:index-cache
:feature:coverage
:feature:replacement
:feature:companion
:feature:standalone
:feature:install-prompt
:feature:favourites
:feature:sharing
:feature:backup
:feature:security
:feature:updates
:feature:contribution
:feature:settings

:app:playstore
:app:fdroid
:app:direct
```

An app profile includes or excludes a feature module at build time:

```kotlin
// app/fdroid/build.gradle.kts (illustrative)
dependencies {
    implementation(project(":feature:inventory"))
    implementation(project(":feature:standalone"))
    implementation(project(":feature:install-prompt"))
    // ...
}
```

A profile that excludes a module ships no code for it. The UI reads the
`FeatureRegistry` and either hides the entry or shows a short explanation when
the feature is relevant.

## 6. Compile-time modules vs. runtime toggles vs. Android dynamic features

| Mechanism | Use for | Trade-off |
| --- | --- | --- |
| Compile-time module | Features that a distribution must not contain | Smallest and safest; requires a separate build per profile. |
| Runtime setting | Optional behaviour inside an included feature | User flexibility; code is still shipped. |
| Android Dynamic Feature Module | On-demand delivery through Play | Play-only, adds Play Core, not suitable for F-Droid; not planned for v1. |

“Dynamic” in this project means **compile-time optional modules plus runtime
settings**, not Play-only dynamic delivery.

## 7. Store-specific flavors

If a store needs a different feature set:

1. Add a profile with a name and capability set.
2. Include the feature modules it supports.
3. Add store metadata (package ID, signing, listing text) in the release layer.
4. Do not add `if (store == ...)` branches inside shared feature code.

This keeps the number of variants open-ended without turning the codebase into a
matrix of special cases.

## 8. Requirements

| ID | Requirement | Acceptance criteria |
| --- | --- | --- |
| BP-R1 | Each feature declares its availability and required capabilities. | A generated registry lists every feature and its status for each profile. |
| BP-R2 | A profile cannot include a feature whose required capabilities it lacks. | Build fails or the feature is excluded with a test proving the exclusion. |
| BP-R3 | The UI explains unavailable features when the user encounters them. | A Play build shows “not available in this build” with a policy-safe reason instead of a broken button. |
| BP-R4 | Optional features can be removed without changing core code. | Removing a feature module and its profile dependency still builds and passes core tests. |
| BP-R5 | Runtime settings can disable an included feature without removing its data. | Disabling then re-enabling restores the feature and its saved state. |
| BP-R6 | Store-specific profiles reuse shared code. | A new profile adds configuration and release metadata only; no copied feature implementation. |

## 9. Open questions

1. Which profiles are actually shipped in the first release: `playstore`,
   `fdroid`, `direct`, or a subset?
2. Does any store-specific flavor need a separate application ID and signing key?
3. Should `direct` be a release artifact or only an internal/testing profile?
4. Are runtime feature toggles exposed to users for every optional feature, or
   only for privacy/behaviour-sensitive ones?
