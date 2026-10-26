# Office Tracker

A small Android app for tracking office hours under these rules (the [user guide](docs/user-guide.md)
explains every screen; each release also carries it as a PDF):

- Core hours: log in by **9:00 AM**, log out after **5:00 PM**
- **8 hours of work** every day, not counting breaks
- **9 hours in the office** every day, from login to logout, counting breaks
- Hours don't carry over between days

Breaking any one of these makes the day **LOP** (loss of pay), and the app says which rule was missed.
You can leave at the latest of login + 9h, reaching 8h of work, and 5 PM, so 9h minus 8h gives 1h of
break; a longer break isn't LOP, you just stay later. A late login shows as LOP right away; the others
once the day is over. If your manager regularizes an LOP, tap **Regularize** on that day
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
same LOP rules including the 9h), **Holiday** or **On leave** (no login needed, never LOP), **Half day** or **Short leave** (see below). History and reports count WFH,
holiday and leave days separately. The share icon sends a text report for a day, this week or this month through any app
(WhatsApp, email, …).

Tap the date on Today to jump to any day, including future dates, so you can plan leave and holidays
ahead. History lists what's planned and how much leave you've taken this year.

**History** opens with a month calendar: each day is colored by how it went (8h met, LOP, regularized,
holiday or leave), and tapping a day opens it. Entries that don't fit together (a check-out before the
first login, two logins in a row, a past day that ends on a break) are flagged on the day with a fix, and
if a recent day has no logout, Today (and the morning login reminder) asks for the time you left.
Long-press a day on the calendar to set its type (plan leave, holidays, half days or short leave ahead).

**Edit day** on any day opens login, every break and logout in one form, with an optional reason. Every
change made by hand (there or entry by entry) is written to the day's **Changes** list, e.g. "login 9:05 AM
→ 8:55 AM · forgot to swipe". History lists every **regularized** day with its reason, note and the date it
was regularized, and can share that list.

History's **Year** tab shows each month of a year on one screen: days that met the hours, LOP, regularized,
leave, holidays, and half days or short leave, with totals. The share button there can also make a one-page
**PDF month sheet** (this month or last) with every day's login, logout, breaks, hours and status.

The home-screen widget shows only timers the system draws and fixed times, so it stays current between
updates (on a break it counts the day's break time and shows when you're due back). The status
notification now uses a channel that shows on the lock screen even on phones that hide silent
notifications there, so you can see the time left without unlocking.

**Settings** lets you change the rules (core login-by and leave-after, hours inside, official start and end), choose light, dark or system theme, pick weekly
off days (Saturday and Sunday by default), turn reminders on or off (login reminder 15 minutes before the
deadline, "you can leave now", and "break allowance used"), export or import a CSV backup, turn on a weekly automatic backup to a folder you pick (a dated CSV each
week), and clear all data (a copy is kept so you can undo it). On the 1st a notification sums up the month
that just ended: days that met 8h, LOP, regularized days and total hours.

While you're logged in, an ongoing notification counts down to the required hours (or up through your break)
and has Start break / End break / Log out buttons that work from the lock screen. A quick settings tile
("Office break") starts and ends breaks with one tap.

**Breaks**: Start break starts at once. Each break has a label (Breakfast, Tea, Lunch or Other, guessed
from the time and changed with one tap) and shows on one line in Entries with its start, end and length.
During a break, **Set limit** gives it the limit from Settings (15 minutes by default) and **Extend** adds
more (5 minutes by default). A heads-up notification with End break and Extend buttons arrives a couple of
minutes before the limit (adjustable in Settings) and again when it's up.

**Half day** and **Short leave** are day types too. A half day needs 4 hours inside at any time, with no
core hours. Short leave (2 hours by default) needs that much less inside and eases core hours by the same
amount at both ends (log in by 11:00, leave after 15:00). Both amounts are in Settings.

There's also a home-screen widget (long-press the home screen → Widgets → Office Tracker) with a live counter
and one-tap Log in / Start break / End break / Log out.

Everything is stored locally on the phone in a Room (SQLite) database. Nothing runs in the background: the
live counter only ticks while the app is open, and reminders are a few one-shot alarms. The app asks for no
location access (the gate auto-login from 1.1 was removed in 1.8).

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
