# F22 — Support and Donations

| Field | Value |
| --- | --- |
| Status | Partial (About entry point implemented; verified Liberapay URL pending) |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | None |
| Depends on | None |
| Related | F16, F17 |

## 1. Problem

FossScout is free software with no ads, accounts, or paid feature tiers. Users
who find it useful should be able to support development without installing a
proprietary payment SDK or creating an account in the app.

## 2. User stories

- As a user, I want an optional way to support FossScout.
- As a privacy-conscious user, I want the app to open the donation page in my
  browser instead of processing payment inside the app.
- As a maintainer, I want one verified link that is easy to update.

## 3. Scope

- Show a “Support FossScout” section on the About screen when a verified
  Liberapay URL is configured.
- Open the URL in the browser with an explicit user action.
- Document the same support path in the README.
- Keep the section hidden when the URL is blank or invalid.

## 4. Non-goals

- In-app payment processing or Google Play Billing.
- Accounts, donation history, receipts, or tax handling.
- Donation-gated features, badges, or other rewards.
- Automatic prompts, reminders, tracking parameters, or analytics.
- Adding a new permission, dependency, or feature module.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F22-R1 | MUST | The support entry point is hidden until a verified URL is configured. | A blank constant renders no support section. |
| F22-R2 | MUST | The URL is an HTTPS Liberapay profile URL. | Other schemes, hosts, credentials, ports, queries, and fragments are rejected. |
| F22-R3 | MUST | The link opens only after an explicit tap. | No automatic browser launch or background request. |
| F22-R4 | MUST | Donations do not unlock app features. | No entitlement, badge, or feature-gating path exists. |
| F22-R5 | SHOULD | The README documents the same support path. | README support text names Liberapay and optional donations. |

## 6. Data and privacy

The app stores no donation data. It passes only the configured URL to the
platform browser. There is no payment SDK, account, analytics event, or network
request inside the app.

## 7. Edge cases

- The Liberapay account is not configured yet: hide the section.
- The URL is changed or removed: the section disappears.
- No browser can open the link: the existing website-open path fails silently.
- The project later moves away from Liberapay: update the single constant and the
  README together.

## 8. Open questions

1. What is the canonical Liberapay profile URL?
2. Should the README publish a second one-time donation method later?

## 9. Implementation decision (2026-10-27)

- **Entry point:** About only. The existing six bottom-navigation destinations
  are unchanged, and Settings does not add another support entry.
- **Platform:** Liberapay. The link is external and uses the browser; no payment
  SDK is added.
- **Hidden placeholder:** `SupportLinks.LIBERAPAY_URL` is blank until the
  project account exists. `SupportLinks.validatedLiberapayUrl` accepts only an
  HTTPS `liberapay.com` profile URL with no credentials, explicit port, query, or
  fragment.
- **README:** the support section explains that donations are optional, do not
  unlock features, and the verified link will be published when configured.
- **Deferred:** in-app billing, donation prompts, one-time payment methods, and
  store-specific donation links.
