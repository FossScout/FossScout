# Flavors - FossScout

FossScout supports two build flavors with different distribution channels.
The GitHub beta is the only published release path; the F-Droid source tag
`v0.1.0-vanilla` is prepared, while Play and F-Droid publication remain
intentionally disabled.

## Flavors
| Flavor | Application ID | Purpose | Notes |
|---|---|---|---|
| `playstore` | `com.fossscout.app` | Google Play Store | Play-safe feature set |
| `vanilla` | `com.fossscout.app.vanilla` | F-Droid and direct distribution | Full-feature set; broad package visibility and full installed-app scan; version suffixed `-vanilla` |

## Beta Variant
- `playstoreBeta` variant uses application ID `com.fossscout.app.beta` with `-beta` version suffix
- Allows installation alongside production
- The GitHub release workflow runs only for tags matching `v*-beta*`; the variant can still be built locally

## Build Commands
```bash
# PlayStore
./gradlew assemblePlaystoreDebug
./gradlew assemblePlaystoreRelease

# Vanilla
./gradlew assembleVanillaDebug
./gradlew assembleVanillaRelease

# Beta (PlayStore beta)
./gradlew assemblePlaystoreBeta
```

## Versioning
Version code/name can be overridden via CI environment variables (`CI_VERSION_CODE`, `CI_VERSION_NAME`).

## Signing
Release signing is configured conditionally via environment variables/secrets. Debug builds use default debug signing.
