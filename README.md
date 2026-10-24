# Office Tracker

A small Android app for tracking office hours under these rules:

- Core hours: log in by **9:00 AM**, leave after **5:00 PM**
- **8 hours** inside the office every day (hours don't carry over between days)
- Official hours **8:30 AM to 5:30 PM** set the break: login to 5:30 PM minus 8h, so an 8:30 login gives
  **1 hour**, an earlier login more and a later one less (a longer break is fine; you just stay later)

Breaking any one of the first three rules makes the day **LOP** (loss of pay), even with more than 8 hours
inside: a late login, leaving before 5 PM, or less than 8 hours inside. A late login shows as LOP right away;
the other two once the day is over. If your manager regularizes an LOP, tap **Regularize** on that day
(with an optional note): it then shows as Regularized, keeps the original reason, and isn't counted as LOP.

## How it works

Tap **Log in** when you arrive. After that you get **Start break** / **End break** for each break and
**Log out** when you leave for the day (it warns you first if logging out now would make the day LOP).
The Today screen updates every second, with an animated clock dial that fills toward 8h and rolling digits, and shows:

- time spent inside the office, with progress toward 8h
- total break time (including the break you're on right now) and how much allowance is left
- how much more time you need to clock in
- the earliest time you can leave (the later of "8h reached" and 5 PM), and when to be back from a break

Entries can be added, edited or deleted for any day, in case you forget to tap.
The History screen shows daily (last 14 days), weekly (last 8 weeks) and monthly (last 6 months) charts
with inside time, break time and the 8h target, plus LOP days (highlighted in red on the daily chart) and counts of late logins and days under 8h.

Each day has a type: **Office** (default), **WFH** (only the first login and last logout count, no breaks,
same LOP rules), **Holiday** or **On leave** (no login needed, never LOP). History and reports count WFH,
holiday and leave days separately. The share icon sends a text report for a day, this week or this month through any app
(WhatsApp, email, …).

Tap the date on Today to jump to any day, including future dates, so you can plan leave and holidays
ahead. History lists what's planned and how much leave you've taken this year.

**Settings** lets you change the rules (core login-by and leave-after, hours inside, official start and end), pick weekly
off days (Saturday and Sunday by default), turn reminders on or off (login reminder 15 minutes before the
deadline, "you can leave now", and "break allowance used"), export or import a CSV backup, and clear all data.

While you're logged in, an ongoing notification counts down to the required hours (or up through your break)
and has Start break / End break / Log out buttons that work from the lock screen. A quick settings tile
("Office break") starts and ends breaks with one tap.

**Gate auto-login** (optional, off until you set it up in Settings): stand at the office gate and save it as
the gate, with a radius of 50–300 m (100 m by default). Walking in offers "Log in" (or "End break"), and
walking out offers "Start break" or "Log out", each timed to when you crossed the gate. Nothing is recorded
until you tap the notification. It needs precise location "Allow all the time"; Android watches the geofence
itself, so the app still does no background work.

There's also a home-screen widget (long-press the home screen → Widgets → Office Tracker) with a live counter
and one-tap Log in / Start break / End break / Log out.

Everything is stored locally on the phone in a Room (SQLite) database. Nothing runs in the background: the
live counter only ticks while the app is open, reminders are a few one-shot alarms, and the gate is a
system geofence.

Default rules live in `app/src/main/java/com/gkgsiva/tracker/domain/Rules.kt`.

## Getting the APK

Each version is on the [Releases](https://github.com/gkgsiva/tracker/releases) page: open it on your phone and
tap `office-tracker-<version>.apk` to download and install it (allow installs from unknown sources once).
From 1.2 on every build is signed with the same key (`app/debug.keystore`), so a new APK installs over the
old one and keeps your data.

Every push also builds a test APK in GitHub Actions: open the **Android build** run and download the
`office-tracker-debug-apk` artifact (a zip with `app-debug.apk` inside).

## Versions

Each set of additions bumps the minor version (`versionName` in `app/build.gradle.kts`): 1.0, 1.1, 1.2, …
When it merges into `main`, the *Release* workflow tags the commit `v<version>` and publishes a release
with the APK, so any earlier version can be installed again or checked out and rebuilt. The installed version is shown at the bottom of Settings.

## Building locally

Open the project in Android Studio, or with the Android SDK installed run:

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # time-calculation tests
```

Stack: Kotlin, Jetpack Compose (Material 3), Room, minSdk 26.
