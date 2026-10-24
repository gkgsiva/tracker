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

log() { echo "== $*"; }

dump() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml > "$DEBUG/ui.xml"
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
    dump
    local xy
    if xy=$(find_node "$1" "${2:-first}"); then
        adb shell input tap $xy
        sleep "${3:-1.5}"
    else
        echo "WARN: no '$1' on screen"
        cp "$DEBUG/ui.xml" "$DEBUG/missing-$(echo "$1" | tr -c 'A-Za-z0-9' _).xml"
        return 1
    fi
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
    adb exec-out screencap -p > "$OUT/$1.png"
    log "shot $1"
}

set_clock() { adb shell "date $1" >/dev/null; } # MMDDhhmmYYYY.ss

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
adb shell setprop persist.sys.timezone Asia/Kolkata
adb shell cmd uimode night no
set_clock 102114202026.00
# Tidy status bar.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 82 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null

adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
restart_app

# --- Demo data: Clear data, swap the kept copy for seed.csv, then Undo ----------------------
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
tap Today top 2         # the "Today" button beside the date
to_top
shot today-inside 3
swipe_up
shot today-stats
scroll_to Entries
shot today-entries
to_top
tap "Start break" first 3
shot today-on-break 2

adb shell cmd statusbar expand-notifications
shot notification-status 2
adb shell cmd statusbar collapse
adb shell cmd statusbar add-tile $PKG/.system.BreakTileService
sleep 2
adb shell cmd statusbar expand-settings
shot quick-settings-tile 3
adb shell cmd statusbar collapse
sleep 1

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
swipe_up
shot history-daily
tap Weekly && { to_top; scroll_to "Average per working day"; shot history-weekly; }
to_top
tap Monthly && { scroll_to "Average per working day"; shot history-monthly; }
scroll_to "Leave & holidays"
shot history-targets
to_top
tap "Share report" && { shot share-dialog; tap Cancel; }

# A late login day (LOP) and its Regularize dialog.
to_top
tap 7 first 2 && {
    scroll_to "LOP day"; shot day-lop
    tap Regularize && { adb shell input text "Approved%sby%smanager"; adb shell input keyevent KEYCODE_BACK; sleep 1; shot regularize-dialog; tap Cancel; }
}
tap History bottom; to_top
tap 13 first 2 && { scroll_to Regularized; shot day-regularized; }
tap History bottom; to_top
tap 16 first 2 && { to_top; shot day-entry-check; }
tap History bottom; to_top
tap 9 first 2 && { to_top; shot day-wfh; }
tap History bottom; to_top
tap 30 first 2 && { to_top; shot day-planned-leave; }

# --- Settings ----------------------------------------------------------------------------
tap Settings bottom
to_top
shot settings-rules
scroll_to Reminders && shot settings-reminders
scroll_to "Clear all data" && shot settings-backup
tap Dark && { tap Today bottom; tap Today top; to_top; shot today-dark 3; tap Settings bottom; to_top; tap System; }

# --- Month summary on the 1st ------------------------------------------------------------
adb shell am force-stop $PKG
adb shell am start -n $PKG/.ui.MainActivity >/dev/null; sleep 3
tap Today bottom
tap "Log out" first 1.5; tap "Log out" first 1.5   # confirm, so the status notification goes
adb shell input keyevent KEYCODE_HOME
set_clock 110109592026.30
sleep 50
adb shell cmd statusbar expand-notifications
shot notification-month-summary 3
adb shell cmd statusbar collapse

ls -la "$OUT"
