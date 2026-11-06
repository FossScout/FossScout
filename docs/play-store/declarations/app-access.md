# App access instructions

FossScout has no account, login, subscription, or restricted content. Reviewers
can access every feature without credentials.

## Suggested reviewer steps

1. Launch FossScout. The Discover catalog opens.
2. Search for `notes` or select a category to test filtering.
3. Open **Installed** and use **Check app** with a package such as
   `org.thoughtcrime.securesms`.
4. Open **Stores** to inspect the F-Droid and IzzyOnDroid entries. Index sync
   requires network access; cached data may be used offline.
5. Open **Saved** and **Collections** to inspect local favourites and lists.
6. Open **Settings** to inspect feature toggles and the backup entry point.
7. Open **About** for version, license, and project links.

## Notes for the reviewer

- The Play build supports manual package checks and limited package visibility.
  Apps with a launcher activity are visible to those checks; a full
  installed-app scan is available only in the F-Droid build.
- Some handoff actions require a compatible store client; a browser fallback is
  available.
- No login, test account, or special configuration is required.
- The app does not install or uninstall APKs.
