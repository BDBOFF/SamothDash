#!/bin/bash
# Test de bout en bout : vraie WebView Android connectée à un vrai Home Assistant lancé sur le runner.
mkdir -p shots
R=shots/resultats.txt
PKG=com.samoth.dash
shot() { adb exec-out screencap -p > "shots/$1.png"; }
say() { echo "== $*" >> $R; }
logs() { adb logcat -d -s SamothDash:I | grep -v beginning >> $R; adb logcat -c; }
prefs() { # $1 = motionWake
  TOKEN=$(python3 .github/e2e/ha.py token)
  cat > /tmp/dash.xml <<X
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
<string name="url">http://10.0.2.2:8123/tab-test/0</string>
<string name="refresh">$TOKEN</string>
<string name="deviceName">Tablette</string>
<boolean name="askedExempt" value="true" />
<boolean name="motionWake" value="$1" />
<int name="sleepMinutes" value="1" />
<int name="reloadMinutes" value="$2" />
<boolean name="sleepSchedule" value="${3:-false}" />
<int name="sleepFrom" value="${4:-1260}" />
<int name="sleepTo" value="${5:-360}" />
</map>
X
  B=$(base64 -w0 /tmp/dash.xml)
  adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs; echo $B | base64 -d > shared_prefs/dash.xml'"
}
top() { adb shell dumpsys window | grep -m1 mCurrentFocus >> $R; }
brightness() { adb shell dumpsys window windows | grep -m1 -oE 'sbrt=[-0-9.]+|screenBrightness=[-0-9.]+' >> $R; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system user_rotation 1
adb shell settings put system accelerometer_rotation 0
adb shell pm grant $PKG android.permission.CAMERA
adb logcat -c

nowmin() { D=$(adb shell date +%H:%M | tr -d '\r'); echo $(( 10#${D%:*} * 60 + 10#${D#*:} )); }

say "1. Dashboard, barres masquées, batterie, détection caméra (faux flux en mouvement : doit rester éveillé)"
prefs true 0
adb shell am start -n $PKG/.MainActivity
sleep 45
shot 1-dashboard
python3 .github/e2e/ha.py states >> $R
sleep 45
top; shot 1b-reste-eveille; logs

say "2. Alerte : personne détectée"
python3 .github/e2e/ha.py on
sleep 6
shot 2-alerte
python3 .github/e2e/ha.py off
sleep 32
shot 2b-fin-alerte; logs

say "2c. Bouton Tester l'alerte"
adb shell am force-stop $PKG
prefs false 0
adb shell "run-as $PKG sh -c 'sed -i s,\</map\>,\<boolean\ name=\"testAlert\"\ value=\"true\"\ /\>\</map\>, shared_prefs/dash.xml'"
adb shell am start -n $PKG/.MainActivity
sleep 25
shot 2c-test-alerte; logs
sleep 35
adb shell input swipe 5 1200 900 1200 300
sleep 3
shot 2d-diagnostic-reglages
adb shell input keyevent KEYCODE_BACK
sleep 2

say "3. Veille sans caméra, puis réveil par alerte et par toucher"
adb shell am force-stop $PKG
adb shell pm revoke $PKG android.permission.CAMERA
prefs true 0
adb shell am start -n $PKG/.MainActivity
sleep 85
shot 3-veille; logs
python3 .github/e2e/ha.py on
sleep 6
shot 3b-reveil-alerte
python3 .github/e2e/ha.py off
sleep 32
logs
sleep 70
shot 3c-veille-encore
adb shell input tap 900 1200
sleep 2
shot 3d-reveil-toucher; logs

say "4. Rechargement périodique (1 min)"
adb shell am force-stop $PKG
prefs false 1
adb shell am start -n $PKG/.MainActivity
sleep 150
shot 4-apres-rechargement; logs

say "5. Réglages"
adb shell input swipe 5 1200 900 1200 300
sleep 3
shot 5-reglages; top
adb logcat -d -b crash | tail -40 >> $R
