# FossScout — Feature Scope and Distribution Design

Status: **Draft — v1 modular foundation implemented; inventory, matching, and store features remain pending (not yet approved)**
Date: 2026-10-22
Owners: product + engineering (to be confirmed)

This is the living design artifact for defining FossScout's requirements and
feature set. It records what has been decided, what is still open, and how the
Play Store and F-Droid builds differ. Update it as the conversation produces
decisions; do not treat proposals as decisions until they are marked **Decided**.

## 1. Intent

FossScout helps privacy-aware Android users understand what is installed on
their phone, discover free and open source equivalents, and choose where to
install from — without forcing them toward or away from any store.

It is an audit and replacement assistant, not only a catalog. The user should
leave with a clearer picture of their installed apps and a practical next step,
not a lecture about software ideology.

The project is open source so others can improve it and add ideas. The original
project retains ownership of its name and branding, and forks are expected to
retain credit and license notices.

## 2. Known context

These are facts about the current repository, not product decisions.

- FossScout is an Android app for discovering free and open source software.
- Current state is a modular foundation (`v0.1.0`): Material 3 Compose UI, a
  bundled catalog, search, category filters, locally persisted favourites and
  notes, runtime feature settings, and an About screen.
- The app has `playstore` and `fdroid` build profiles. The catalog is still
  bundled; store adapters, matching, inventory scanning, and sharing are not
  implemented yet.
- Planned technical baseline: Kotlin, Jetpack Compose, min SDK 24, compile/target
  SDK 35.
- v1 has no accessibility service, no direct APK install, and no store network
  layer. The F-Droid profile carries the broad-package-visibility capability in
  metadata; no matching manifest permission is declared yet.
- The project is MIT licensed.

## 3. Distribution requirement

FossScout must ship as two variants:

| Variant | Distribution | Feature posture |
| --- | --- | --- |
| Play Store build | Google Play | Restricted to capabilities allowed by Play policy. No accessibility-service-dependent features, no direct APK install. |
| F-Droid build | F-Droid (and likely direct APK) | May add restricted capabilities; v1 uses the same no-install feature set as Play plus the broad-visibility capability. |

This is a **stated requirement** implemented as capability-based profiles: the
Play build excludes restricted capabilities, while F-Droid and direct builds may
add them. In v1 neither profile includes direct APK install or an accessibility
service; those are deferred. The profile model is defined in
`2026-10-22-build-profiles-and-optional-features.md`. Earlier questions about the
split, build production, package IDs, and data migration are resolved by D20,
D21, and the profile specification.

### Play policy findings (verified 2026-10-22)

These are constraints, not yet decisions. Sources are linked in section 6.1.

- **`QUERY_ALL_PACKAGES`.** Play treats the installed-app inventory as personal
  and sensitive. The permission is only permitted when broad visibility is core
  to the app's user-facing purpose, with a declaration and a justification that
  a less intrusive method will not work. Permitted examples include device
  search, antivirus, file managers, and browsers. A privacy audit app may be
  eligible, but this cannot be assumed; the Play build needs a narrower fallback.
- **Accessibility API.** Play allows accessibility services for a wide range of
  apps, but only apps whose primary purpose is assisting people with disabilities
  may declare `isAccessibilityTool`. Other apps must provide prominent in-app
  disclosure and complete a declaration. Autonomous actions are restricted;
  deterministic, user-initiated automation is treated differently. Detecting and
  interrupting another store's install flow is unlikely to qualify as an
  accessibility tool. **Assume the install-time prompt is F-Droid-only until
  Play review says otherwise.**
- **`REQUEST_INSTALL_PACKAGES`.** Play permits this only when the core purpose
  includes sending/receiving app packages and enabling user-initiated
  installation. The listed permitted uses do not explicitly cover a discovery
  assistant. **Assume the Play build deep-links to stores instead of installing
  APKs.**
- **No public Play catalog.** Google Play does not publish a machine-readable
  catalog. Scraping Play or relying on an unofficial client API creates
  reliability and terms-of-service risk. Treat general stores as deep-link or
  search-only.

### Store installed vs. no store installed

FossScout must work in both situations. These are separate operating modes, not
one flow with a missing dependency:

| Mode | Store app installed? | How it works | Build |
| --- | --- | --- | --- |
| **Companion** | Yes | FossScout hands off: opens the app's store page, or offers to add a repository to the installed store. No install permission needed. | Both |
| **Standalone** | No | FossScout reads the store's index/API, resolves and downloads the APK, verifies it, and invokes Android's installer. | F-Droid build (Play policy risk) |
| **Handoff** | Either | FossScout opens a browser or store listing; the user downloads/installs manually. | Both |

Standalone mode is effectively a small store client. It is useful precisely
because F-Droid-compatible repositories are static file trees, not services that
require the F-Droid app.

## 4. Decisions

| # | Decision | Status | Rationale / notes |
| --- | --- | --- | --- |
| D1 | Ship two distribution variants: Play Store and F-Droid. | **Decided** (user) | Play policy restricts some capabilities; F-Droid can carry the full set. |
| D2 | The F-Droid variant may include an accessibility service and additional options not present in the Play variant. | **Decided** (user) | Stated requirement. Specific capabilities still to be defined. |
| D3 | Maintain a written design artifact that records requirements, definitions, and decisions. | **Decided** (user) | This document. |
| D4 | Primary users are privacy-aware Android users who want to understand and reduce closed-source software on their own device. | **Decided** (user) | The product is an audit and replacement assistant, not an ideological gatekeeper. |
| D5 | FossScout scans installed packages and reports whether a FOSS build or FOSS equivalent is available. | **Decided** (user) | Core job. |
| D6 | When no exact FOSS match exists, FossScout suggests functional alternatives. | **Decided** (user) | The suggestion must be honest about being an alternative, not the same app. |
| D7 | Support both FOSS and general app stores, in both directions. | **Decided** (user) | Do not force proprietary-to-FOSS or FOSS-to-proprietary; the user decides. |
| D8 | Use store database indexes for faster, semi-offline checks. | **Decided** (user) | Exact index sources, caching, and freshness rules are open. |
| D9 | The F-Droid build may observe the user's install flow and suggest a FOSS alternative at that moment. | **Decided** (user) | Intended to use the accessibility service. Play build will not depend on it. |
| D10 | FOSS stores are configurable, not limited to F-Droid. The app ships predefined stores and lets the user add a store that is missing or define their own. | **Decided** (user) | The catalog layer must be store-agnostic. |
| D11 | If a custom store cannot be supported in depth, FossScout still accepts it as a link-only entry and tells the user exactly what is and is not supported. | **Decided** (user) | No silent failure and no false "not available" result. |
| D12 | Flexibility is a product principle: features should be independently configurable or disableable, with sensible defaults rather than forced behavior. | **Decided** (user) | Granularity and which settings are exposed are still open. |
| D13 | The project stays open source; forks must retain credit and license notices, while the original project retains ownership of its name and branding. | **Decided** (user) | The license mechanism that enforces this is still open. |
| D14 | Support both users who have a store client installed and users who do not; no store app is a hard dependency. | **Decided** (user) | Companion, standalone, and handoff modes. |
| D15 | For F-Droid-compatible repositories, FossScout can read the same signed index and download APKs directly; the F-Droid client is optional. | **Decided** (user intent; technically verified) | Direct APK URLs exist, e.g. `https://f-droid.org/repo/<package>_<versionCode>.apk`. |
| D16 | When F-Droid is installed, FossScout should offer to add a compatible repository or open an app page in F-Droid, but must also work without it. | **Proposed** | Uses F-Droid's repo URL/fingerprint, QR, and registered `/fdroid/repo` or `fdroidrepos://` handoff. |
| D17 | Users can keep favourite apps and collections locally, share them with others in multiple ways, and import them on a new phone. | **Decided** (user) | Sharing is user-initiated; no account or login required. |
| D18 | FossScout is anonymous per device: no accounts, no server-side profile, and no personal data leaves the device unless the user explicitly shares or exports it. | **Decided** (user) | The device is the source of truth. Losing the device without an export means losing the local lists. |
| D19 | A favourite is independent of installation state and can carry a private note. | **Decided** (user) | Installed state is derived at read time; favourites can exist for apps that were never installed. |
| D20 | Build variants are capability-based profiles, not two hardcoded builds; optional features are separate modules that can be included or excluded per profile. | **Decided** (user) | Supports Play, F-Droid, direct, and future store-specific profiles. See `2026-10-22-build-profiles-and-optional-features.md`. |
| D21 | Standalone APK download/install (F09) is deferred from v1. | **Decided** (user) | Play restricts it; v1 uses store handoff and repository-add flows. F09 remains a later restricted-build feature. |

### V1 scope (proposed)

The first integrated release is the **audit, discovery, and personal-library**
release. It does not install APKs itself.

**In v1:** F01 inventory, F02 matching, F03 alternatives, F04 store registry,
F05 index/cache, F06 coverage, F07 replacement guidance, F08 companion handoff,
F11 favourites/notes, F12 collections/sharing, F13 backup/migration, F14
security verification, F16 contribution hooks, F17 feature configuration.

**Deferred:** F09 standalone install (user decision), F10 install-time
accessibility prompt, F15 update tracking.

The Play/F-Droid split still exists in v1: F-Droid may perform a full installed
package scan; Play uses the manual/selected path unless `QUERY_ALL_PACKAGES` is
approved. No v1 feature installs an APK.

### Post-v1 quality candidates (user-selected)

These are not in the first integrated foundation release; they are the next
quality/completeness additions:

- **F18 Unified store search** — search every enabled store from one box, with
  cached-index results and explicit link-only handoffs.
- **F19 Permission and tracker diff** — compare the installed app with an
  alternative by permissions and known tracker signatures, with sources and
  “unknown is not clean” language.
- **F20 Matching correction** — let users override a wrong match locally, with
  undo and backup/export support.
- **F21 Trust badges** — anti-features, reproducible-build status, signer-change
  warnings, stale metadata, and link-only state, each with an explanation and
  source.

## 5. Open questions

The detailed feature files carry their own open questions. These are the
cross-cutting decisions still needed before implementation planning.

1. What definition of FOSS does the catalog use, and how are source-available,
   freemium, or partially open apps labeled?
2. What is the first release's smallest complete promise, and which of the 21
   features are in it?
3. Does the Play build request `QUERY_ALL_PACKAGES`, or ship manual/selected
   checking only until approval is certain?
4. Which predefined stores ship in v1?
5. How should the install-time suggestion behave: overlay, notification, or
   both; and which stores are in scope?
6. How are the profiles identified, versioned, signed, and migrated between?
7. Which license strategy matches "credit required, ownership protected":
   permissive with trademark and NOTICE, or copyleft to keep forks open?
8. Which features get user-facing toggles, and which stay fixed defaults?
9. What are the default index storage budget, sync interval, and cleanup policy?
10. How far should source-repository adapters go in v1?
11. Does a shared collection include store descriptors and notes by default, or
    only app identities and links?
12. Is the full backup encrypted by default?
13. Does the Play build ever download an APK, or always hand off?
14. Are split APKs / app bundles in scope for v1?
15. Is standalone mode a one-off installer or a full update manager?
16. What is explicitly out of scope for the first release?

## 5.1 Feature library

Detailed requirements are split by feature:

- `features/README.md` — index, availability legend, dependency matrix.
- `features/F01-app-inventory.md` through
  `features/F17-feature-configuration.md` — per-feature requirements and
  acceptance criteria.
- `2026-10-22-build-profiles-and-optional-features.md` — profiles, capabilities,
  module boundaries, and how optional features are included or excluded.

## 6. Feature candidates

Status values: **Candidate** (proposed, not agreed), **Decided** (agreed for the
product), **Deferred** (not in the first release).

| # | Feature | User problem | Distribution | Status |
| --- | --- | --- | --- | --- |
| F1 | Installed-app audit with local package inventory | "What is on my phone, and which of it is closed source?" | Shared; scope may differ by Play policy | Candidate |
| F2 | Exact FOSS build matching with source and license evidence | "Is there a FOSS version of this exact app?" | Shared | Candidate |
| F3 | Functional alternative suggestions when no exact match exists | "If not, what else could do this job?" | Shared | Candidate |
| F4 | Store choice and bidirectional replacement paths | "Let me move between Play and F-Droid without being pushed." | Shared | Candidate |
| F5 | Cached store indexes with freshness indicators | "Make checks fast and work offline or on a bad connection." | Shared | Candidate |
| F6 | Install-time alternative suggestion | "Catch me at the moment I am about to install the closed-source app." | F-Droid build | Candidate |
| F7 | Explainability: why this match, source link, license, last update | "Why should I trust this recommendation?" | Shared | Candidate |
| F8 | Replacement warning: signature mismatch, uninstall, data-loss risk | "What will I lose if I switch?" | Shared | Candidate |
| F9 | Manual "check this app" and share/open-with entry point | "Let me check one app without granting broad access." | Shared; also Play fallback | Candidate |
| F10 | Saved/watch list and rescan reminders | "Tell me when an alternative appears or an app changes." | Shared | Candidate |
| F11 | Configurable store registry: predefined stores plus user-defined stores | "F-Droid is not the only FOSS source, and my store may be missing." | Shared | Candidate |
| F12 | Store adapters with different capabilities (indexed, search-only, deep-link-only) | "Not every store publishes a machine-readable index." | Shared | Candidate |
| F13 | Source repository adapters (GitHub/GitLab/Codeberg/releases/direct APK/HTML) | "Some FOSS apps are distributed from a repository, not a store." | Shared | Candidate |
| F14 | Coverage and confidence dashboard: what was checked, what was not, why | "Don't tell me 'not available' when you only checked half my stores." | Shared | Candidate |
| F15 | Store descriptor import/export and shareable presets | "Let me move my store setup or share it with someone else." | Shared | Candidate |
| F16 | Contribution hooks: report a missing store or app, copy diagnostic details | "I found a gap; let me help fix it." | Shared | Candidate |
| F17 | Companion handoff: add a repository to the installed F-Droid client or open an app page | "I already use F-Droid; put this repo/app there." | Shared | Candidate |
| F18 | Standalone index client with direct APK download and install | "I don't have F-Droid; let me get the APK anyway." | F-Droid build | Candidate |
| F19 | Update tracking for apps installed through FossScout | "If I skip F-Droid, I still want to know about updates." | F-Droid build | Candidate |
| F20 | Security verification panel: repo fingerprint, index signature, APK hash, signer | "Show me why this download is trustworthy." | Shared | Candidate |
| F21 | Local favourites and named collections of apps | "Keep the apps I care about in one place." | Shared | Candidate |
| F22 | Share a collection via the Android share sheet, text/JSON/CSV, QR, file, or deep link | "Send this list to a friend without making them sign up." | Shared | Candidate |
| F23 | Import with preview and merge/replace/skip choices | "Bring a shared list in without duplicating or trusting it blindly." | Shared | Candidate |
| F24 | Full local backup/export and restore for phone migration | "Move my favourites, stores, and settings to a new phone." | Shared | Candidate |

### Store descriptor — proposed fields (not decided)

The user is unsure which URLs stores actually expose. This is a starting point
for the custom-store form, to be validated against real store formats.

| Field | Purpose | Notes |
| --- | --- | --- |
| Display name | Identify the store in the UI | Required. |
| Homepage / store URL | Open the store or a listing in a browser | Required; also the deep-link fallback. |
| Adapter type | Tell FossScout how to read the store | Examples: F-Droid-compatible index, Accrescent gRPC, generic JSON, search-only, deep-link-only, source repository. |
| Index database URL | Machine-readable catalog used for matching and offline cache | Often the only URL needed for an F-Droid-compatible repository. |
| API / query service URL | Live search or metadata lookup when no index exists | May require a query template and optional token. |
| Repository URL | Base location for APK downloads or source releases | Often redundant for indexed stores because entries already point at artifacts; essential for source-repository adapters. |
| Signing key / fingerprint | Verify index or APK authenticity | Optional at first; a trust feature rather than a requirement. |
| Enabled / priority | Control which stores participate and in what order | User-owned; no ideological default. |
| Last synced / freshness | Show how current the cached data is | Required for semi-offline behavior. |

A repository URL is useful, but it is not the same thing as an index URL. The
index answers "what exists?"; the repository answers "where is the artifact?"
For F-Droid-compatible stores, one base URL often supplies both by convention.

### Unsupported custom stores

A custom store may expose nothing FossScout can parse. The required behavior is
graceful degradation, not rejection:

- Save the entry anyway as **link-only**.
- Show a **Limited support** badge and a plain-language reason: no supported
  index or API found at the supplied URL.
- Let the user open the store, search it manually, or edit the entry later.
- Exclude it from automated matching, but show the gap in coverage.
- Offer a prefilled "request support" link with the store URL and adapter
  details, so the open-source community can add an adapter.
- Never report an app as "no FOSS alternative" if an enabled store was not
  actually checked.

## 6.1 Store capability matrix (researched 2026-10-22)

| Store / source | Adapter | What it exposes | Integration notes |
| --- | --- | --- | --- |
| **F-Droid main** | F-Droid index v2, fallback v1 | Full signed catalog, package metadata, versions, licenses, source links, anti-features; search API; per-app API; signer index | `https://f-droid.org/repo`; `index-v2.json`; `entry.json` + diffs; `index-v1.jar`. Full v2 index was ~66.5 MB uncompressed and listed 4,766 packages on this date. Use `entry.json`/diffs for updates, not a full download every time. |
| **F-Droid Archive** | F-Droid index v2/v1 | Older versions removed from the main repo | Same format as F-Droid main; optional predefined store. |
| **IzzyOnDroid** | F-Droid index v2 | F-Droid-compatible catalog of developer-built APKs, licenses, source links, reproducible-build markers | `https://apt.izzysoft.de/fdroid/repo`; fingerprint `3BF0D6ABFEAE2F401707B6D966BE743BF0EEE49C2561B9BA39073711F628937A`; index-v2 was ~14.4 MB and listed 1,401 apps on this date. |
| **Guardian Project** | F-Droid index | F-Droid-compatible repository | Candidate predefined entry; verify current URL, index, and fingerprint before shipping. |
| **Accrescent** | gRPC API (not an F-Droid index) | List apps, localized listings, package info, download info, update info, device compatibility | Public API at `appstore-api.accrescent.app`; API definitions are Apache-2.0. Requires protobuf/gRPC and a separate adapter; not a drop-in F-Droid repo. Verify third-party client terms. |
| **Google Play** | Deep link / search only | Public listing pages and `market://` intents; no public catalog index | `market://details?id=<package>` and `https://play.google.com/store/apps/details?id=<package>`; search URL template. No scraping. |
| **Other general stores** (Samsung, Huawei, Amazon, APKPure, Aptoide, Uptodown, etc.) | Deep link / search URL template | Store listing pages, sometimes a search endpoint | Treat as search-only or deep-link-only until a stable public API is verified. Templates are per-store and can break. |
| **Source repositories** (GitHub, GitLab, Codeberg, SourceHut, direct APK, HTML) | Source adapter | Releases, assets, version tags, APK links | Per-app, not catalog-wide. Similar to Obtainium's source model; expect rate limits and HTML scraping fragility. Do not copy GPL code without checking compatibility. |

**Key distinctions:**

- An **index** is a catalog snapshot: good for offline matching, but large and
  needs freshness metadata.
- An **API** is queried live: smaller and fresher, but needs network and may be
  rate-limited or unstable.
- A **repository URL** is where artifacts live: useful for downloads, not a
  substitute for an index.
- A **deep link** is only a handoff: no matching, no offline coverage.

### Favourites, sharing, and device migration

The local model should separate three things:

| Object | Contents | Shared? |
| --- | --- | --- |
| **Favourite** | App identity (package name), optional store/source, optional note | Only when the user shares it |
| **Collection** | Name, description, ordered favourites, schema version | Yes, explicitly |
| **Local profile** | Favourites, collections, custom stores, settings, cache state | Only through a full backup/export |

Design rules:

- No account, no server-side list, no device identifier. The device is the
  source of truth.
- A **shared collection** should not contain installed-app state, device
  identifiers, settings, API tokens, or private store credentials.
- Sharing options can include the Android share sheet (plain text/JSON), a file
  export, a QR code for small lists, and a deep link where the payload fits.
- Import is a preview flow, never an automatic action. The user chooses merge,
  replace, or skip, and confirms before any store is added or install starts.
- Imported content is untrusted: no automatic installs, no automatic opening of
  arbitrary links, and no silent store additions.
- A **full backup** is separate from a shared collection. It can include custom
  stores and settings for phone migration, optionally protected by a
  user-chosen passphrase.
- Android auto-backup may help, but it cannot be the only migration path because
  it is OS- and vendor-dependent.

### How F-Droid repository adding works

An F-Droid repository is a **base URL plus a signing-key fingerprint**. The
client downloads a signed index (`entry.json`/`index-v2.json`, or the older
`index-v1.jar`) and verifies it against the pinned key. Adding a repository
simply stores that URL and fingerprint and fetches the index.

F-Droid supports several handoff paths:

- The standard **Manage Repositories** screen, where the user pastes the URL and
  optional fingerprint.
- A **QR code** or link in the form
  `https://<repo-host>/fdroid/repo?fingerprint=<SHA-256>`; the F-Droid client
  registers for well-formed `/fdroid/repo` URLs and offers to add the repo.
- The `fdroidrepos://` scheme, which the F-Droid client also handles.
- `market://details?id=<package>`, which the F-Droid client handles for app
  pages.

FossScout can support all of these: show a QR code, copy the URL/fingerprint,
launch the registered URL/`fdroidrepos://` intent if F-Droid is installed, and
otherwise show plain instructions or use its own standalone adapter. The same
repository descriptor powers both paths, so a custom F-Droid-compatible repo
works in FossScout even when the F-Droid client is absent.

### Direct APK download without a store app

F-Droid-compatible repositories expose static APK files. The website already
offers direct downloads, for example:

`https://f-droid.org/repo/org.fdroid.fdroid_2000052.apk`

The signed index contains the package version and artifact metadata, so
FossScout can resolve the appropriate APK without the F-Droid client. The
trade-off is that direct downloads do not get F-Droid's update notifications;
FossScout can compensate by comparing the installed version code with the
index's suggested version.

The same is possible for any indexed store that exposes artifact URLs. For
Accrescent, the gRPC API provides download info. For many general stores, no
stable direct URL or API exists, so only a browser/store handoff is honest.

### Standalone security gates

If FossScout downloads and installs APKs, it takes on store-client
responsibilities:

1. Pin the repository fingerprint and refuse a changed key without explicit
   user approval.
2. Verify the signed index before trusting package metadata.
3. Verify the APK's SHA-256 against the signed index.
4. Show the APK signer and check continuity with the installed app.
5. Warn that a signature mismatch requires uninstall and can destroy app data.
6. Never install silently; every install is user-confirmed.
7. Keep standalone installation out of the Play build unless policy review says
   otherwise.

## 7. Draft requirements

These are early and will be refined. Each is tied to a build variant where it
matters.

| # | Requirement | Build | Status |
| --- | --- | --- | --- |
| R1 | Package inventory is computed locally; the inventory is never uploaded by default. | Shared | Draft |
| R2 | Each installed app is classified as FOSS, proprietary, unknown, or unable to determine, with evidence. | Shared | Draft |
| R3 | Match results distinguish exact build, fork, and functional alternative, and show confidence. | Shared | Draft |
| R4 | Stores can be added, edited, disabled, reordered, imported, and exported. | Shared | Draft |
| R5 | An unsupported store is saved as link-only with an explicit reason and coverage gap. | Shared | Draft |
| R6 | Index sync uses conditional requests/diffs, shows last-updated time, and can be cancelled or rolled back. | Shared | Draft |
| R7 | The Play build must remain useful without accessibility, broad package visibility, or install permission. | Play | Draft |
| R8 | The install-time prompt is opt-in, scoped, and has a manual fallback; it is absent from the Play build. | F-Droid | Draft |
| R9 | Replacement guidance warns about signature mismatch, uninstall, and possible data loss before acting. | Shared | Draft |
| R10 | No automatic uninstall or silent installation; every destructive step is user-confirmed. | Shared | Draft |
| R11 | Major behaviors have a user-facing setting or safe default, per the flexibility principle. | Shared | Draft |
| R12 | License, copyright, attribution, and source links are visible in the app and in the repository. | Shared | Draft |
| R13 | Companion mode opens the installed store's app page or repository-add flow without requiring install permission. | Both | Draft |
| R14 | Standalone mode resolves APK URLs from a signed index, downloads, verifies, and invokes the system installer with explicit user confirmation. | F-Droid | Draft |
| R15 | A user without any store app can complete a download and install for a supported indexed store. | F-Droid | Draft |
| R16 | Standalone installs can be checked against index versions for updates, with notifications opt-in. | F-Droid | Draft |
| R17 | Security failures (bad index signature, hash mismatch, signer change, fingerprint change) block installation and explain why. | Shared | Draft |
| R18 | Favourites and collections are stored locally and survive app restarts and normal upgrades. | Shared | Draft |
| R19 | A collection can be shared through at least one human-readable format and one machine-readable format, without an account. | Shared | Draft |
| R20 | Import shows a preview and requires confirmation before adding apps, stores, or links. | Shared | Draft |
| R21 | A full export can restore favourites, custom stores, and settings on a new device. | Shared | Draft |
| R22 | No account, device identifier, or analytics identifier is required or generated for favourites or sharing. | Shared | Draft |

## 8. Build and release definition

See `2026-10-22-build-profiles-and-optional-features.md` for the profile and
module model. Still to define:

- application ID strategy per profile;
- signing and release channels;
- F-Droid metadata and reproducible-build expectations;
- Play data-safety and accessibility disclosures;
- store-specific release metadata and package IDs;
- migration path between profiles.

## 9. Open-source, ownership, and credit strategy

Current license: **MIT**. That already requires retaining the copyright and
license notice in copies or substantial portions, so a fork cannot strip credit
entirely. It does **not** require a fork to stay open source, and it does not
grant trademark rights.

Options to discuss:

| Strategy | Forks must credit | Forks must stay open | Notes |
| --- | --- | --- | --- |
| MIT + `NOTICE` + trademark policy | Yes | No | Least friction; a company could ship a closed fork with attribution. |
| Apache-2.0 + `NOTICE` + trademark policy | Yes | No | Adds an express patent grant and NOTICE propagation; still permissive. |
| GPL-3.0 | Yes | Yes, for distributed derivatives | Keeps improvements in the commons; incompatible with proprietary forks. |
| AGPL-3.0 | Yes | Yes, including network use | Usually overkill for an Android client; strongest copyleft. |

Ownership tools that work with any of the above:

- a `NOTICE` file and `CONTRIBUTORS.md`;
- SPDX headers and a visible in-app "License and credits" screen;
- a trademark policy for the FossScout name and logo, so modified forks use a
  different identity;
- a DCO for contributions, or a CLA if dual licensing/relicensing is desired;
- an explicit statement that the license does not grant trademark rights.

Not legal advice; the final choice should be checked by someone qualified.

## 10. Non-goals and anti-goals

_To be completed._ Include things that would make the product a failure even if
technically functional.

## 11. Decision log

Append-only history of changes to this document.

| Date | Change | By |
| --- | --- | --- |
| 2026-10-22 | Created draft; recorded distribution requirement and artifact decision. | brainstorming session |
| 2026-10-22 | Recorded the audit-and-replacement intent, bidirectional store stance, index caching, and F-Droid install-time suggestion. Added feature candidates and policy constraints. | brainstorming session |
| 2026-10-22 | Recorded configurable FOSS stores with predefined entries and user-defined stores; added store-registry and store-adapter candidates. | brainstorming session |
| 2026-10-22 | Added research findings: F-Droid/IzzyOnDroid index formats, Accrescent gRPC, Play policy limits, and deep-link-only general stores. Recorded open-source/credit requirement, unsupported-store fallback, and flexibility principle. | brainstorming session |
| 2026-10-22 | Clarified companion/standalone/handoff modes, F-Droid repository-add mechanics, direct APK downloads without the F-Droid client, and standalone security gates. | brainstorming session |
| 2026-10-22 | Recorded local favourites/collections, account-free sharing, phone-migration export/import, and anonymous-per-device storage. Added sharing/import requirements and open questions. | brainstorming session |
| 2026-10-22 | Added favourite independent of installation state, per-favourite notes, capability-based build profiles, optional feature modules, and the per-feature requirements library. | brainstorming session |
| 2026-10-22 | Deferred standalone install and accessibility for v1; added F18 unified search, F19 permission/tracker diff, F20 matching correction, and F21 trust badges as post-v1 quality candidates. | brainstorming session |
