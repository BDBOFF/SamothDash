#!/bin/bash
# Installe l'APK, photographie les réglages, puis vérifie le glissement depuis le bord gauche.
mkdir -p shots
shot() { adb exec-out screencap -p > "shots/$1.png"; }
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell settings put system user_rotation 1
adb shell settings put system accelerometer_rotation 0
adb shell am start -n com.samoth.dash/.MainActivity
sleep 8
shot 1-reglages
adb shell dumpsys window displays | grep -m1 -E 'cur=' > shots/ecran.txt || true
# Saisit une adresse injoignable : le kiosque affiche son message de nouvel essai
adb shell input keyevent KEYCODE_TAB
W=$(adb shell wm size | tail -1 | sed 's/.*: //' | cut -dx -f1); H=$(adb shell wm size | tail -1 | sed 's/.*: //' | cut -dx -f2)
echo "taille $W x $H" >> shots/ecran.txt
L=$(( W > H ? W : H )); S=$(( W > H ? H : W ))
adb shell input tap $(( L / 4 )) $(( S * 24 / 100 ))
sleep 1
adb shell input text "http://10.0.2.2:9/lovelace"
adb shell input keyevent KEYCODE_BACK
sleep 1
shot 2-adresse
adb shell input tap $(( L * 3 / 4 )) $(( S * 22 / 100 ))
sleep 3
# Refuse la demande de veille si elle apparaît
shot 3-apres-enregistrer
adb shell input keyevent KEYCODE_BACK
sleep 6
shot 4-kiosque
adb shell input swipe 5 $(( S / 2 )) $(( L / 3 )) $(( S / 2 )) 300
sleep 3
shot 5-apres-glissement
adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -2 > shots/activite.txt
adb logcat -d -s AndroidRuntime:E | tail -40 > shots/crash.txt
