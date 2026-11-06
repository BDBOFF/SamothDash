#!/bin/bash
mkdir -p shots
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell wm size 1280x800
adb shell wm density 240
sleep 3
adb shell am start -n com.samoth.dash/.MainActivity
sleep 10
adb exec-out screencap -p > shots/reglages-1280x800.png
adb shell dumpsys window | grep -m1 mCurrentFocus > shots/info.txt
adb shell wm size >> shots/info.txt
adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb exec-out cat /sdcard/u.xml > shots/ui.xml
adb logcat -d -b crash | tail -30 > shots/crash.txt
