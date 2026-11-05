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
# Coordonnées de la disposition en écran 1800x2560 (voir capture 1)
adb shell input tap 500 420
sleep 1
adb shell input text "http://10.0.2.2:9/lovelace"
adb shell input keyevent KEYCODE_BACK
sleep 1
shot 2-adresse
adb shell input tap 1350 270
sleep 3
shot 3-apres-enregistrer
# Ferme la demande d'envoi en veille
adb shell input keyevent KEYCODE_BACK
sleep 8
shot 4-kiosque
adb shell dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity' > shots/activite-avant.txt
adb shell input swipe 5 1200 900 1200 300
sleep 3
shot 5-apres-glissement
adb shell dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity' > shots/activite-apres.txt
adb logcat -d -s AndroidRuntime:E | tail -40 > shots/crash.txt
