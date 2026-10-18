# Roz (रोज़)

A personal Android app for the things that come round again: **habits**, **chores**, **reminders**
and **birthdays**, all on one Today screen. One person, one phone — no account, no server. Your data
stays on the phone, plus an encrypted backup file that you choose where to keep (Google Drive works).

The full design is in [`SPEC.md`](SPEC.md).

## What it does

| | |
|---|---|
| **Today** | Progress ring, today's habits to tick, overdue chores, chores and reminders due today, birthdays this week. |
| **Habits** | Yes/no, "at least 8" and "at most 2" habits, every day or N times a week. Streak **and** a strength score, so one bad day doesn't wipe out progress. One missed day a week is forgiven. 15-week heatmap. |
| **Chores** | Every Sunday, Mon & Thu, every 2 weeks, the 24th, first Saturday, month end, yearly — or "3 days after I last did it". Missed chores show once as overdue, never stacked. Skip this time, pause, areas. |
| **Reminders** | Exact-time alerts with **Done / In 1 hour / Tomorrow** right on the notification. One-off or repeating. |
| **Birthdays** | Alerts 7 days before, 1 day before and on the day. Age or anniversary number, gift ideas, *Send a wish* on WhatsApp. Import from contacts. |
| **Backup** | Required and encrypted (AES-256-GCM). Opens with your passphrase **or** a recovery code. Nightly while charging on Wi-Fi and after big changes; the phone keeps the last 7 copies plus one a month; a warning if no backup for 3 days. |
| **Also** | Morning summary and evening nudge, undo on everything, Recently deleted for 30 days, CSV and calendar exports, dark mode. |

## Install on your phone

1. Open the repo's **Releases → latest** on your phone and download `roz.apk`.
2. Open it and allow installing from your browser or Files app.
3. Open Roz, allow notifications, and set up your backup from the banner on Today.

Every build is signed with the same key, so a new APK installs over the old one and keeps your data.

## Project layout

- `core/` — plain Kotlin, no Android: repeat rules, chore scheduling, habit streaks and strength,
  birthdays, reminders, the Today plan, the backup format and exports. All the rules live here and
  are unit tested.
- `app/` — the Android app (Kotlin, Jetpack Compose, Room, WorkManager, AlarmManager).

## Building

```bash
./gradlew :core:test                 # rule tests, no Android SDK needed
./gradlew -PcoreOnly=true :core:test # same, without Google's Maven repo (e.g. restricted networks)
./gradlew :app:assembleRelease       # APK, needs the Android SDK (CI does this on every push)
```

### Signing

`app/roz-release.keystore` is the release key. It is useless without its password, which lives only
in the GitHub Actions secret **`ROZ_KEYSTORE_PASSWORD`** (Settings → Secrets and variables → Actions).
CI only publishes a release when that secret is set, so every published APK has the same key.
