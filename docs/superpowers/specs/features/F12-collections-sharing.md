# F12 — Collections and Sharing

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | `SHARE_IMPORT_EXPORT` |
| Depends on | F11 |
| Related | F13 |

## 1. Problem

Users want to share a list of apps with friends without creating an account, and
receive lists from others without trusting them blindly. Sharing must work
through ordinary Android channels and survive a phone change.

## 2. User stories

- As a user, I want to share a collection through my messaging app.
- As a user, I want to send a readable list or a file the app can import.
- As a user, I want to preview an imported list before it changes my data.
- As a user, I do not want a shared file to install anything automatically.

## 3. Share formats

| Format | Use | Notes |
| --- | --- | --- |
| Plain text | Human-readable sharing | App names and links; no notes by default |
| JSON | Lossless import | Versioned schema; supports collections and stores |
| CSV | Spreadsheet-friendly | Optional; may lose nested data |
| File | Email/cloud/chat attachment | `.fossscout` or `.json` |
| QR | Small lists | Payload size limited; compress before encoding |
| Deep link | One-tap import where supported | App-link/URI size limits apply |

## 4. Scope

- Share a collection, a single favourite, or all favourites.
- Include app identity, optional store/source, and optional notes.
- Import from file, clipboard, share intent, QR, or deep link.
- Preview imported items and conflicts.
- Choose merge, replace, or skip per item or as a batch.
- Never auto-install or auto-open arbitrary links.
- Keep the schema versioned and forward-compatible.

## 5. Non-goals

- Hosting shared lists on a FossScout server.
- Requiring a login or account.
- Sharing installed-app inventory implicitly.
- Sharing private notes by default.
- Sharing store credentials or API tokens.

## 6. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F12-R1 | MUST | Sharing requires no account or network service. | A share completes with the app offline. |
| F12-R2 | MUST | Notes are excluded by default and only included with explicit consent. | Default export omits note fields. |
| F12-R3 | MUST | Import shows a preview before modifying local data. | No data changes until confirmation. |
| F12-R4 | MUST | Import supports merge, replace, and skip. | Each mode produces the expected result in tests. |
| F12-R5 | MUST | Imported links and stores are untrusted until confirmed. | No automatic install or store add occurs. |
| F12-R6 | MUST | The schema is versioned and rejects unsupported future versions gracefully. | A v99 file produces a clear error, not a crash. |
| F12-R7 | SHOULD | Plain text output is readable without FossScout. | A recipient can read app names and links. |
| F12-R8 | SHOULD | QR/deep-link payloads have a size limit and a file fallback. | Oversized lists are not silently truncated. |
| F12-R9 | MAY | The user can share only selected items from a collection. | Selection is preserved in the export. |

## 7. Data schema (outline)

```json
{
  "schema": 1,
  "type": "fossscout.collection",
  "name": "Privacy tools",
  "items": [
    {
      "package": "org.example.app",
      "name": "Example",
      "store": "fdroid",
      "note": null
    }
  ]
}
```

Store descriptors may be embedded as references, not as automatically trusted
entries. Imported notes are shown in the preview and remain private until the
user chooses to keep them.

## 8. Edge cases

- Duplicate package IDs.
- Same package from different stores.
- App not present in any enabled store.
- Imported note contains unexpected markup or links.
- Very large collection.
- Malformed or malicious JSON.
- Share target app cannot handle the chosen MIME type.
- Recipient has an older schema version.

## 9. Open questions

1. Should a shared collection include store descriptors by default?
2. Is CSV required for v1?
3. Should QR encode the full list or only a reference? (No server means a full
   payload or a file.)
4. How should notes be handled on import: keep private, drop, or prompt?
