# Office Tracker

A small Android app for tracking office hours under these rules:

- Log in before **9:00 AM**, leave after **5:00 PM**
- **8 hours** inside the office every day
- Up to **1 hour** of total break time

Breaking any one of the first three rules makes the day **LOP** (loss of pay), even with more than 8 hours
inside: a late login, leaving before 5 PM, or less than 8 hours inside. A late login shows as LOP right away;
the other two once the day is over.

## How it works

Each day starts with your first login. After that, every **Check out** / **Check in** pair is a break.
The Today screen updates every second and shows:

- time spent inside the office, with progress toward 8h
- total break time (including the break you're on right now) and how much allowance is left
- how much more time you need to clock in
- the earliest time you can leave (the later of "8h reached" and 5 PM), and when to be back from a break

Entries can be added, edited or deleted for any day, in case you forget to tap.
The History screen shows daily (last 14 days), weekly (last 8 weeks) and monthly (last 6 months) charts
with inside time, break time and the 8h target, plus LOP days (highlighted in red on the daily chart) and counts of late logins and days under 8h.

Everything is stored locally on the phone in a Room (SQLite) database.

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
