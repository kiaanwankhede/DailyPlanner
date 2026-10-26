# Roz (रोज़) — spec

Roz is a personal Android app that puts four kinds of repeating things in one place:
**habits**, **chores**, **reminders** and **birthdays/anniversaries**. One person, one phone,
no account, no server. Everything stays on the phone except an encrypted backup file you choose
where to keep.

This file is the source of truth for what Roz does. Decisions are recorded here as they are
made; change the spec first, then the code.

## 1. Decisions so far

| Topic | Decision |
|---|---|
| Users | Just you, one phone. No sign-in, no sync, no sharing (maybe much later). |
| Stack | Native Kotlin, Jetpack Compose, Room, WorkManager, AlarmManager — same as Kharcha. |
| Structure | `core/` is plain Kotlin with all the rules and is unit tested; `app/` is the Android app. |
| Name | Roz. Package `app.roz`. Repo: `DailyPlanner`. |
| Look | The six mockup screens (Today, Habits, Chores, Birthdays, Reminders, Add). Each type has its own colour: habits green, chores blue, reminders orange, birthdays yellow. |
| Streaks | Streak **and** a strength score side by side. One free skip a week. |
| Chore window ("every 3–4 days") | Not in v1. |
| Backups | Required and encrypted, with a recovery code. Details in §7. |
| Delivery | GitHub Actions builds the APK on every push and publishes it as the latest release. |

## 2. Screens

1. **Today** — progress ring (done / total for today), a row of today's habits to tick, then
   sections **Overdue**, **Today** (chores due and reminders today), **Birthdays this week**.
   Tap the circle to complete; undo from the snackbar.
2. **Habits** — one card per habit: name, target, streak 🔥, strength %, last 7 days as dots
   (filled = done, striped = skipped/forgiven, empty = missed, dashed = today). Tap a habit for
   its 15-week heatmap and history.
3. **Chores** — grouped **Overdue / Today / This week / Later**, filter chips by area
   (Home, Kitchen, Bathroom, Plants, Car…). Each row shows the repeat rule and the due label.
   Long-press: *Skip this time*, *Pause*, *Edit*, *Delete*.
4. **Birthdays** — a large card for the next one (who, age or years, gift ideas checklist,
   *Send a wish* via WhatsApp/SMS share), then the coming list with day counts.
5. **Reminders** — **Upcoming** and **Done** tabs; grouped Today / Tomorrow / Later. The next
   one shows **Done / In 1 hour / Tomorrow**.
6. **Add / Edit** — one screen with a type switch (Habit, Chore, Reminder, Birthday) and the
   fields for that type.
7. **Settings** — backup (set up, back up now, restore), exports (CSV, calendar), morning
   summary and evening nudge times, recently deleted.

Bottom navigation: Today · Habits · Chores · Birthdays · Reminders. A ＋ button opens Add.

## 3. Habits

- **Kinds**: *Yes/no* ("Meditate"), *at least N* ("8 glasses of water", tick adds one),
  *at most N* ("at most 2 cups of chai"; a day with nothing logged counts as met once it ends).
- **Target**: every day, or **N times a week** (Mon–Sun weeks).
- **Free skip**: one missed day per week is forgiven automatically and does not break the streak.
  You can also mark a day *skipped* on purpose; that uses the same weekly allowance.
  A second miss in the same week breaks the streak.
- **Streak**: daily habits count days; weekly-target habits count weeks that hit the target.
  Today never breaks a streak while it is still in progress.
- **Strength score (0–100%)**: a running average that rises with every done day and falls with
  every miss, with a half-life of about two weeks, so a long run followed by a miss drops a few
  points instead of to zero. Skipped/forgiven days leave it unchanged. Weekly-target habits score
  each day by how much of the target the last 7 days reached.
- Optional reminder time per habit (notification with **Done** / **Snooze**).

## 4. Chores — how repeating works

Every chore has exactly one repeat rule.

**On a schedule** — dates stay fixed whether you're early or late:

| Rule | Example |
|---|---|
| Weekly on one or more days, every N weeks | Every Sunday; Mon & Thu; every 2 weeks on Wed |
| Monthly on a date, every N months | 24th of each month; every 3 months on the 10th |
| Monthly on a weekday | First Saturday; last Friday |
| Monthly on the last day | Last day of the month |
| Yearly on a date | 1 November |

**After I do it** — next due counts from when you tick it: every N days, weeks or months.

Rules for the awkward cases:

1. **Missed** — a missed chore shows once as *overdue* with "N days late", never as stacked copies.
2. **Done late** (schedule) — the next one is the next scheduled date after the day you did it.
3. **Done early** (schedule) — ticking before the due date counts for that due date; it doesn't
   come back the next day.
4. **Overdue past the next date** (schedule) — it rolls forward to the most recent scheduled date
   and shows "missed N" (kept in history), still as one item.
5. **29th / 30th / 31st** — falls on the last day of shorter months (31st → 30 Nov, 28/29 Feb).
6. **Skip this time** — moves to the next date without marking done.
7. **Pause** — hidden until resumed; resuming puts the next due date on or after today.
8. **History** — every completion, skip and miss is logged; the chore shows "last done N days ago".
9. Due chores appear in the **morning summary**; nothing rings at an exact time.

## 5. Reminders

- One-off, or repeating with the same schedule rules as chores plus *every day*.
- Ring at an exact time (exact alarm). Notification actions: **Done**, **In 1 hour**,
  **Tomorrow**.
- A repeating reminder, once done, moves to its next date. Undone ones stay on Today.

## 6. Birthdays and anniversaries

- Added by hand (name, relation, date, year optional); optional one-time import from contacts.
- 29 February birthdays are celebrated on 28 February in non-leap years.
- Alerts at the morning summary time **7 days before**, **1 day before** and **on the day**.
- Shows "turns 9" / "35th anniversary" when the year is known.
- Gift ideas checklist per person; *Send a wish* opens WhatsApp (or the share sheet) with a
  ready message.

## 7. Backups

- **Required.** Set up on first run (can be postponed, but Today shows a banner until done).
- **Encrypted file** at a location you pick with Android's file picker (pick Google Drive to keep it
  off the phone). That file always holds the newest backup. File format:
  `"ROZ1" | version | salt | data-key sealed with passphrase key | data-key sealed with recovery key
  | iv | AES-256-GCM(gzip(JSON))`. A random data key encrypts the data; the passphrase and the
  recovery code each unlock that key (PBKDF2-SHA256), so either one restores the backup.
- **Passphrase** typed twice at setup; remembered on the phone, sealed by the Android keystore,
  so nightly backups run unattended.
- **Recovery code**: 20 characters shown once at setup to write down. Monthly reminder to check
  you still know the passphrase.
- **When**: nightly while charging on Wi-Fi, and a few minutes after big changes (deletes,
  restores, bulk edits).
- **Keeps history**: the app also keeps the last 7 backups plus the newest one from each month in
  its own storage on the phone; older copies are deleted. If a new backup would be empty while the
  last one had data, the picked file is left alone, so a wiped phone never overwrites a good copy.
- **Stale warning**: if no backup succeeded for 3 days, Today shows a banner with *Back up now*.
- **Restore** shows a preview ("backup from 12 Oct: 6 habits, 14 chores, 24 birthdays") and asks
  before replacing what's on the phone.
- **Format version** in every file; each app version reads all older versions (tested).
- Android's own Google auto-backup is turned off, so there is one backup system.
- **Exports**: CSV (habit history, chore log) and a calendar file (.ics) of birthdays.

## 8. Undo and deleting

- Every tick, skip and delete shows a snackbar with **Undo**.
- Deleted items go to **Recently deleted** for 30 days, then are removed.

## 9. Notifications

- **Morning summary** (default 8:00): what's due today, overdue chores, birthday alerts.
- **Evening nudge** (default 20:30): only if habits are still open today.
- **Exact alerts**: reminders, and habits that have their own reminder time.
- All of these survive a reboot.

## 10. Roadmap

| Version | Scope |
|---|---|
| **v1** | Everything above, plus top priority (§11) and the weekly add prompt (§12): Today, habits (streak + strength + free skip), chores (all repeat rules), reminders, birthdays, notifications, encrypted backups with recovery code, exports, undo, recently deleted, dark mode. |
| v2 | Home-screen widget; "how dirty" bar on rolling chores (Tody-style); day timeline view; notes on a day; birthday extras (milestones, gift price/link, what you gave last year); habit insights; Health Connect auto-tick for step habits (as in Sehat); reminders every N hours. |
| Later | Indian festivals and lunar dates; linking with Kharcha (a bill payment ticks its reminder); family sharing. |
| Not planned | Game mechanics, leaderboards, AI coaching. |

## 11. Top priority

Any one habit, chore, reminder, birthday or anniversary can be marked as **the** top priority. It
shows first on Today and stays pinned on the lock screen until you finish or remove it.

- **One at a time.** Marking another item replaces the current one (the form says so).
- **How to set it.** The *Top priority* switch at the end of every Add/Edit form (also for a new
  item), or long-press a chore or reminder and choose *Make top priority*.
- **On Today.** A dark card above the habits with *TOP PRIORITY*, the item, how late or when it is
  due, and **Done** and **Remove**. Birthdays and anniversaries only have **Remove**.
- **On the lock screen.** A quiet, pinned notification (channel *Top priority*, no sound) with
  **Done** and **Remove** buttons. Android phones have no real lock-screen widgets, so a pinned
  notification is the way. It follows the phone's own lock-screen notification setting.
- **Privacy.** *Settings → Hide top priority on lock screen* shows only "Top priority · Unlock to
  see it" until the phone is unlocked.
- **When it ends.** Finishing a chore or reminder ends its time on top. A yes/no habit ends when
  done today; a counting habit ("8 glasses") stays, updating "5 of 8 today", until it reaches the
  target. A birthday stays until its day is over. Deleting or pausing the item also ends it.
- **Not allowed.** "At most" habits never finish until the day ends, so they can't be the top
  priority.
- **Kept up to date.** The pinned card is refreshed after every change, at the morning summary,
  after a reboot or update, and after a restore.
- **Backups.** The pick and the lock-screen setting are part of the backup.

## 12. Weekly "add one recurring item" prompt

A nudge to keep building up what Roz tracks: aim for **one new recurring item a week**, counted from
**1 January**, and keep prompting in the task list until you've caught up.

- **The count.** One item is due on the start day (1 January) and one more every 7 days after, so
  1–7 Jan is week 1, 8–14 Jan week 2, and so on. On 26 Oct that is 43.
- **What counts as added.** Everything you have now: habits, chores, birthdays and anniversaries,
  and repeating reminders (one-off reminders don't count). Deleted items don't count.
- **The gap.** *to go* = items due so far − items you have, never below zero. Add one and it drops
  by one; a new week raises it by one; at zero the prompt disappears until the next week.
- **In the task list.** A row in the **Today** section with a dashed blue edge, "Add 1 recurring
  item", "33 to go · 1 a week since 1 Jan" and the number on the right. It is not ticked off: tap it
  to open the Add form, preselected to the kind you have the fewest of (birthday, habit or chore).
- **Weekly notification.** On the first day of each 7-day week, at the morning-summary time, if
  you're behind: "New week: add 1 recurring item".
- **Settings.** A switch to turn it off, and the day the count starts from (default 1 January of
  the year Roz was first opened; it does not reset by itself on the next 1 January).
- **Backups.** The switch and the start day are part of the backup.

