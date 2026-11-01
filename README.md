# Mahood Family

A family app for Android: shared calendar, shared shopping lists and a family notice board.
**There is no server.** Data lives on each phone and syncs, encrypted, through a Google Drive
folder shared between family members. The calendar uses a normal shared Google Calendar.

## Install

1. On each phone, open the latest release on GitHub and download the `.apk`.
2. Tap it and allow "install unknown apps" for your browser when asked.
3. Open the app, enter your name, sign in with Google.
4. The first person taps **Create family**. Everyone else gets added under
   **Family › Invite** and then scans the QR code or pastes the invite.

The app checks GitHub for new versions on the Family screen.

## How it works

| Layer | What |
| --- | --- |
| `modules/*` | Features: `calendar`, `lists`, `board`. Each implements `FamilyModule`. |
| `core` | Local store (SQLite, last-writer-wins CRDT), sync engine, Google sign-in, Drive client, encryption, notifications, shared UI. |
| `app` | Setup flow, navigation, Home and Family screens, module registration. |

**Sync.** Every change is stored locally first, so the app works offline. Changes are
queued and uploaded as small encrypted files (`ops-<device>-<time>.bin`) to the family
Drive folder. Each phone downloads files it hasn't seen and merges them field by field:
the newest change wins, so all phones converge whatever order files arrive in.
Sync runs every 20 seconds while the app is open and about every 15 minutes in the
background.

**Privacy.** Files are encrypted with AES-256-GCM using a family key that is created on
the first phone and passed on only inside the invite (QR code or link). Google stores
only ciphertext. Anyone with the invite *and* access to the Drive folder can join, so
share invites privately.

## Adding a feature

1. Copy `modules/board` to `modules/<name>` and change the package and namespace.
2. Implement `FamilyModule`: an `id`, a `HomeCard`, a `Screen`, and (optionally)
   `notificationLines`. Store data with `ctx.put(collection, id, fields)` and read it
   with `ctx.records(collection)`. Sync and encryption come for free.
3. Add `include(":modules:<name>")` to `settings.gradle.kts`, the dependency to
   `app/build.gradle.kts`, and the module to the list in `FamilyApplication`.

## Building

GitHub Actions builds a signed APK on every push to `main` and publishes it as a release.
The signing key comes from repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`). Locally: `./gradlew :app:assembleRelease` (falls back to the
debug key when no keystore is configured).

One-off Google Cloud setup for sign-in: see [docs/GOOGLE_SETUP.md](docs/GOOGLE_SETUP.md).
