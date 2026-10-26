#!/usr/bin/env bash
# Takes the user guide screenshots on an emulator (run by .github/workflows/screenshots.yml).
# Installs the debug APK, loads the demo data in seed.csv through Clear data + Undo, sets the
# clock to Wed 21 Oct 2026, 2:20 PM, and walks through the screens with adb.
# A step that can't find its button logs a warning and the run carries on, so one change in the
# app doesn't lose every screenshot.

APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
OUT=${OUT:-docs/screenshots}
DEBUG=${DEBUG:-screenshot-debug}
PKG=com.gkgsiva.tracker
HERE=$(dirname "$0")
mkdir -p "$OUT" "$DEBUG"
rm -f "$OUT"/*.png

log() { echo "== $*"; }

dump() {
    # On a slow emulator System UI sometimes stops responding; tap Wait on that dialog and retry.
    for _ in 1 2 3 4 5; do
        adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
        adb shell cat /sdcard/ui.xml > "$DEBUG/ui.xml"
        grep -q "responding" "$DEBUG/ui.xml" || return 0
        echo "WARN: dismissing a not-responding dialog"
        if xy=$(find_node Wait); then adb shell input tap $xy; else adb shell input keyevent KEYCODE_BACK; fi
        sleep 3
    done
}

# Prints "x y" for the node whose text or content-desc is $1 (exact). $2 picks which match:
# first (default), last, top (smallest y) or bottom (largest y). Only nodes in the visible band count.
find_node() {
    python3 - "$DEBUG/ui.xml" "$1" "${2:-first}" "$SCREEN_H" <<'EOF'
import re, sys, xml.etree.ElementTree as ET
path, want, pick, h = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)
hits = []
for n in root.iter("node"):
    if want in (n.get("text"), n.get("content-desc")):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        if 120 < cy < h - 40:
            hits.append((cx, cy))
if not hits:
    sys.exit(1)
x, y = {"first": hits[0], "last": hits[-1], "top": min(hits, key=lambda p: p[1]),
        "bottom": max(hits, key=lambda p: p[1])}[pick]
print(x, y)
EOF
}

tap() {
    # A few tries, since a slow emulator may still be drawing the screen.
    local xy
    for _ in 1 2 3 4; do
        dump
        if xy=$(find_node "$1" "${2:-first}"); then
            adb shell input tap $xy
            sleep "${3:-1.5}"
            return 0
        fi
        sleep 2
    done
    echo "WARN: no '$1' on screen"
    cp "$DEBUG/ui.xml" "$DEBUG/missing-$(echo "$1" | tr -c 'A-Za-z0-9' _).xml"
    return 1
}

swipe_up() { adb shell input swipe 540 $((SCREEN_H * 3 / 4)) 540 $((SCREEN_H / 4)) 400; sleep 1; }
swipe_down() { adb shell input swipe 540 $((SCREEN_H / 4)) 540 $((SCREEN_H * 3 / 4)) 400; sleep 1; }
to_top() { for _ in 1 2 3 4 5; do swipe_down; done; }

# Scrolls down until $1 is in the middle part of the screen.
scroll_to() {
    for _ in $(seq 1 10); do
        dump
        if xy=$(find_node "$1"); then
            local y=${xy#* }
            if [ "$y" -lt $((SCREEN_H * 4 / 5)) ]; then return 0; fi
        fi
        adb shell input swipe 540 $((SCREEN_H * 3 / 4)) 540 $((SCREEN_H / 2)) 400
        sleep 1
    done
    echo "WARN: couldn't scroll to '$1'"
    return 1
}

shot() {
    sleep "${2:-1.5}"
    dump
    adb exec-out screencap -p > "$OUT/$1.png"
    log "shot $1"
}

# MMDDhhmmYYYY.ss in the phone's own time zone.
set_clock() { adb shell 'TZ=$(getprop persist.sys.timezone) date '"$1" >/dev/null; }

restart_app() {
    adb shell am force-stop $PKG
    adb shell am start -n $PKG/.ui.MainActivity >/dev/null
    sleep 4
}

# --- Device setup -------------------------------------------------------------------------
adb root >/dev/null; sleep 3
adb wait-for-device
adb shell setenforce 0
SCREEN_H=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f2)
log "screen height $SCREEN_H"
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
adb shell cmd uimode night no
set_clock 102114202026.00

# Give a freshly booted emulator time to settle before driving it.
sleep 45
dump
adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
restart_app

# --- Demo data: Clear data, swap the kept copy for seed.csv, then Undo ----------------------
sleep 10
tap Settings bottom
scroll_to "Clear all data" && tap "Clear all data"
shot clear-confirm
tap "Delete everything" first 2
shot clear-undo
adb shell "run-as $PKG sh -c 'cat > files/before-clear.csv'" < "$HERE/seed.csv"
tap Undo first 4
restart_app

# --- Today -------------------------------------------------------------------------------
tap Today bottom
to_top
shot today-missed-logout
tap "Add logout" first 2
shot add-entry-dialog
tap Save first 2
scroll_to Changes && shot day-changes
scroll_to "Edit day" && tap "Edit day" && { shot edit-day-dialog; tap Cancel; }
to_top
tap Today top 2         # the "Today" button beside the date
to_top
shot today-inside 3
swipe_up
shot today-stats
scroll_to Entries
shot today-entries
to_top
tap "Start break" first 2
to_top
shot break-started 2
tap "Set 15m limit" first 2
to_top
shot today-on-break 2

adb shell cmd statusbar expand-notifications
shot notification-status 2
adb shell cmd statusbar collapse
# Jump 14 minutes ahead so the 15-minute planned break's heads-up (2 minutes before) fires.
set_clock 102114352026.00
sleep 12
adb shell cmd statusbar expand-notifications
shot notification-break-ending 2
adb shell cmd statusbar collapse
sleep 1

to_top
tap "End break" first 2
tap "Log out" first 1.5
shot logout-warning
tap Cancel
scroll_to "Day type" && tap Change
shot day-type-menu
adb shell input keyevent KEYCODE_BACK; sleep 1

# --- History -----------------------------------------------------------------------------
tap History bottom
to_top
shot history-calendar 2
dump; if xy=$(find_node 27); then adb shell input swipe $xy $xy 900; sleep 1.5; shot calendar-long-press; adb shell input keyevent KEYCODE_BACK; sleep 1; fi
to_top
swipe_up; shot history-daily
tap Weekly && { swipe_up; shot history-weekly; }
tap Monthly && { swipe_up; shot history-monthly; }
tap Year && { swipe_up; shot history-year; }
to_top; swipe_up; tap Daily
scroll_to "Leave & holidays" && shot history-targets
scroll_to "Regularized (1)" && shot history-regularized
to_top
scroll_to Daily
tap "Share report" && { shot share-dialog; tap Cancel; }

# Single days, opened from the calendar.
open_day() { tap History bottom; to_top; tap "$1" first 2; }
open_day 7 && {
    scroll_to "LOP day"; shot day-lop
    tap Regularize && { adb shell input text "Approved%sby%smanager"; adb shell input keyevent KEYCODE_BACK; sleep 1; shot regularize-dialog; tap Cancel; }
}
open_day 13 && { scroll_to Regularized; shot day-regularized; }
open_day 16 && { to_top; shot day-entry-check; }
open_day 9 && { to_top; shot day-wfh; }
open_day 14 && { to_top; shot day-half; }
open_day 6 && { to_top; shot day-short-leave; }
open_day 30 && { to_top; shot day-planned-leave; }

# --- Settings ----------------------------------------------------------------------------
tap Settings bottom
to_top
shot settings-rules
swipe_up; shot settings-part-days
scroll_to Reminders && shot settings-reminders
scroll_to "Clear all data" && shot settings-backup
to_top
tap Dark && { tap Today bottom; tap Today top; to_top; shot today-dark 3; tap Settings bottom; to_top; tap System; }

ls -la "$OUT"
