# Office Tracker

A small Android app for tracking office hours under these rules:

- Log in before **9:00 AM**, leave after **5:00 PM**
- **8 hours** inside the office every day
- About **1 hour** of total break time (a longer break is fine as long as the rules above are met)

Breaking any one of the first three rules makes the day **LOP** (loss of pay), even with more than 8 hours
inside: a late login, leaving before 5 PM, or less than 8 hours inside. A late login shows as LOP right away;
the other two once the day is over.

## How it works

Tap **Log in** when you arrive. After that you get **Start break** / **End break** for each break and
**Log out** when you leave for the day (it warns you first if logging out now would make the day LOP).
The Today screen updates every second, with an animated progress ring and rolling digits, and shows:

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

Everything is stored locally on the phone in a Room (SQLite) database. **Clear all data** at the bottom of
History deletes it after a confirmation.

The rules live in `app/src/main/java/com/gkgsiva/tracker/domain/Rules.kt` if they ever change.

## Getting the APK

Every push builds a debug APK in GitHub Actions. Open the latest **Android build** run, download the
`office-tracker-debug-apk` artifact, unzip it and install `app-debug.apk` on your phone
(you'll need to allow installs from unknown sources).

## Building locally

Open the project in Android Studio, or with the Android SDK installed run:

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # time-calculation tests
```

Stack: Kotlin, Jetpack Compose (Material 3), Room, minSdk 26.
