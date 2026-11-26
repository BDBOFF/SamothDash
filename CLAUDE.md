# Samoth Dash : carnet de passation

Ce fichier sert à reprendre le projet sans avoir suivi les conversations précédentes.
**Le dépôt est public : n'écris jamais de mot de passe, de jeton ni de clé ici, ni dans un commit.**

## Règle permanente

À la fin de **chaque** modification, sans attendre qu'on le demande :

1. faire un commit, puis un push sur `main` ;
2. surveiller la fabrication sur GitHub Actions jusqu'au bout, puis vérifier que la Release `build-N` est publiée ;
3. mettre à jour ce fichier (`CLAUDE.md`) : décisions, bugs corrigés, ce qui reste à faire.

Si la fabrication échoue, corriger et recommencer avant de dire que c'est fini.

Pour appliquer cette règle, utiliser la skill **`/publier`** (`.claude/skills/publier/`).

**En fin de session où du code a changé** : proposer à l'utilisateur les mises à jour de ce fichier (sous forme de liste ou de diff), en plus de celles déjà faites au point 3. Il valide avant qu'elles soient appliquées.

## Automatisations Claude Code (`.claude/`)

- **Garde-secrets** (`hooks/garde-secrets.py`, avant chaque écriture et chaque `git commit`/`git push`) : bloque un mot de passe de signature en clair, un jeton GitHub, une clé privée ou un jeton Home Assistant. Si le hook bloque, ne pas le contourner : passer par un secret GitHub.
- **Vérification de `kiosk.js`** (`hooks/verif-kiosk.py`, après chaque modification de ce fichier) : syntaxe (`node --check`), pas de `inset:` ni de syntaxe récente (`?.`, `??`, `=>`), à cause des WebView anciennes.
- **`/publier`** : commit, push, suivi de la fabrication, vérification de la Release et de la clé, mise à jour de ce fichier.
- **`/test-emulateur`** : lance le test complet sur émulateur ou la capture des réglages, puis lit les résultats sur la branche `apercu`.

## Préférences de l'utilisateur

- **Toujours en français**, avec des explications simples, sans jargon. Il n'est pas développeur.
- Être **précis sur où cliquer** : nom exact du bouton, emplacement à l'écran, sur quel appareil (tablette ou Home Assistant).
- Il aime le style **Samoth** : noir et rouge (`#D10A20`), tuiles de verre, polices Unbounded et Inter, grand « SAMOTH » en filigrane. Ses autres applis sont dans `BDBOFF/SamothHOME` et `BDBOFF/NovaTv`.
- Il veut que **tout tienne sur un écran**, sans défiler.
- Il a une **autre conversation Claude connectée à son Home Assistant** : tout ce qui se règle dans HA ou Frigate passe par elle. Lui donner un message prêt à copier, qui demande de montrer les changements avant de les appliquer.
- Dire honnêtement ce qui a été testé et ce qui ne l'a pas été.

## Ce que fait l'appli

Appli Android de kiosque pour une tablette fixée au mur, qui affiche un tableau de bord Home Assistant de 4 caméras (Frigate).

- **Plein écran**, sans la barre de gauche ni la barre du haut de Home Assistant, sans avoir besoin de `?kiosk` dans l'adresse.
- **Connexion à HA une seule fois**, comme l'appli officielle : le frontend est chargé en mode `external_auth=1` et l'appli lui fournit les jetons.
- **Batterie envoyée à HA** via l'intégration Mobile App (`sensor.<nom>_batterie`, `binary_sensor.<nom>_en_charge`, température). Elle sert à une automatisation qui allume une prise connectée entre 20 et 80 %.
- **Relance des flux figés** : recharge si une vidéo ne progresse plus pendant une minute. Rechargement périodique en option (désactivé par défaut).
- **Alerte plein écran** : 30 s de la caméra quand `binary_sensor.X_person_occupancy` passe à `on` et que `camera.X` existe. Un appui ferme. Bouton « Tester l'alerte » dans les réglages.
- **Veille et réveil à l'approche** : écran noir et luminosité au minimum sans présence, réveil par la caméra frontale, un toucher ou une alerte. Peut être limitée à une plage horaire (ex. 21:00 à 06:00).
- **Réglages** : glisser du bord gauche de l'écran vers la droite.
- **Mise à jour intégrée** : tuile « Mise à jour N » dans les réglages quand une Release plus récente existe.
- **Diagnostic dans les réglages** : caméras suivies, dernière détection vue, historique **Réseau** (pertes et retours) et **Rechargements** (heure et raison).

## Organisation du code

Kotlin, sans Compose (vues Android classiques), minSdk 26, package `com.samoth.dash`.

| Fichier | Rôle |
|---|---|
| `MainActivity.kt` | WebView plein écran, connexion HA, pont `window.externalApp` (jetons), pont `window.samothDash` (alerte, flux figés, diagnostic), glissement vers les réglages, veille, rechargements, suivi du réseau |
| `assets/kiosk.js` | Injecté dans la page HA : masque les barres (CSS dans les shadow DOM), surveille les capteurs de personne et affiche l'alerte, détecte les vidéos figées |
| `HaClient.kt` | Échange de code et renouvellement des jetons, enregistrement Mobile App, envoi des capteurs par webhook |
| `BatteryService.kt` | Service au premier plan : batterie envoyée à chaque changement et chaque minute, alarme toutes les 5 min, suivi du réseau. Contient aussi `WakeReceiver` (démarrage, mise à jour) |
| `MotionDetector.kt` | Caméra frontale en très basse résolution, différence de luminosité sur une grille 16×12. Aucune image gardée ni envoyée |
| `SettingsActivity.kt` | Écran de réglages style Samoth, deux colonnes sur tablette |
| `Prefs.kt` | Réglages et jetons (SharedPreferences), journaux de diagnostic |
| `Updater.kt` | Mise à jour intégrée, reprise de SamothHOME |

## Fabrication et publication de l'APK

- Tout push sur `main` lance `.github/workflows/build.yml` : compilation, puis Release `build-N` avec `SamothDash.apk`.
- **Numéro de version = numéro de lancement du workflow** (`-PBUILD_NUMBER`). La mise à jour intégrée compare ce numéro au tag `build-N`.
- Lien fixe pour installer : `https://github.com/BDBOFF/SamothDash/releases/latest/download/SamothDash.apk`
- Les polices sont téléchargées pendant la fabrication (`app/src/main/assets/fonts/`, ignoré par git).
- L'APK est signé avec `app/samoth-dash.jks`. **Toujours la même clé**, sinon les mises à jour ne s'installent plus par-dessus. Ne pas la changer.
- Le mot de passe de cette clé est dans le **secret GitHub `SAMOTH_KEYSTORE_PASSWORD`** (Settings → Secrets and variables → Actions), lu par `app/build.gradle.kts`. Sans lui, la fabrication s'arrête avec un message clair. Les tests sur émulateur n'en ont pas besoin : ils utilisent la clé de débogage standard.
- La session Claude ne peut pas créer ni lire les secrets GitHub (accès refusé) : c'est l'utilisateur qui les crée dans l'interface.
- Une erreur `HTTP 502` à l'étape « Publier la version » vient de GitHub : relancer suffit.

### Tests sur émulateur (lancement manuel)

- `preview.yml` + `.github/preview.sh` : test complet sur émulateur Android, avec un vrai Home Assistant de test lancé sur le runner (`.github/e2e/`). Il vérifie le dashboard, les barres, la batterie, l'alerte, le bouton de test, la veille, le réveil et le rechargement. Il dure environ 25 minutes.
- `reglages.yml` + `.github/reglages.sh` : capture des réglages en 1280×800.
- Les captures sont publiées sur la branche **`apercu`**, écrasée à chaque lancement. Elle peut être supprimée sans risque.
- Lancer avec `gh api -X POST repos/BDBOFF/SamothDash/actions/workflows/<fichier>.yml/dispatches -f ref=main`.

## Décisions et pourquoi

- **`external_auth=1` plutôt qu'un jeton longue durée** : rien à copier pour l'utilisateur, session qui n'expire pas. Ce paramètre disparaît de l'adresse après chargement : **ne jamais utiliser `web.reload()`**, toujours repasser par `openDashboard()`.
- **Masquage des barres par CSS injecté** et réappliqué chaque seconde, car HA reconstruit ses composants. Il faut gérer l'ancienne barre latérale (`.mdc-drawer`) et la nouvelle (`.sidebar-shell`, `--ha-sidebar-width`).
- **Mobile App plutôt qu'un capteur sur mesure** : la tablette apparaît comme un appareil, sans rien configurer dans HA.
- **Alerte gérée dans la page**, en lisant `hass.states` : aucune configuration dans HA, et le flux passe par `camera_proxy_stream`.
- **Glissement depuis le bord gauche** pour les réglages, à la demande de l'utilisateur (au lieu de 5 appuis). Une zone est exclue des gestes d'Android pour que le geste « retour » ne le vole pas.
- **Veille = écran noir + luminosité minimale**, pas l'écran éteint : la page continue de tourner, donc les alertes peuvent réveiller l'écran.
- **Rechargement périodique désactivé par défaut** : il provoque un écran noir puis l'écran de chargement de HA, ce que l'utilisateur prend pour une coupure.

## Bugs déjà corrigés (et pièges à éviter)

- **Barre de gauche toujours visible** sur HA récents (2026.11) : la structure avait changé. Toujours tester sur la **dernière version** de HA, pas une ancienne.
- **Alerte invisible** sur les WebView anciennes : `inset:0` n'est pas compris, il faut `top/left/right/bottom:0`.
- **Le Wi-Fi de la tablette se coupait** pendant que l'appli tournait : un `WifiLock` en mode haute performance était soupçonné. Il a été retiré (version 17). **Ne pas le remettre.**
- **Écran noir puis écran de chargement de HA** plusieurs fois par jour : l'appli rechargeait le dashboard à chaque micro-coupure du Wi-Fi, en plus du rechargement horaire. Elle ne recharge plus que si la page est en erreur ou après plus d'une minute sans réseau (version 22).
- **Alerte qui ne se déclenchait jamais** : ce n'était pas l'appli. Les capteurs Frigate étaient indisponibles dans HA, car Frigate se connectait à MQTT sur `127.0.0.1` au lieu de `core-mosquitto`. Corrigé dans HA par l'autre conversation.
- **Alerte en retard** (la personne est déjà partie) : ce n'est pas l'appli. Le processeur qui fait tourner Frigate n'analyse que 0,2 à 0,5 image par seconde par caméra. Les solutions sont côté Frigate : masques, moins de sensibilité la nuit, puis accélération matérielle (OpenVINO ou clé Coral).
- **Le test sur émulateur** : il faut `numpy` et `ffmpeg` pour la caméra de démonstration de HA, sinon elle n'apparaît pas et l'alerte ne peut pas être testée.

## Installation chez l'utilisateur

- Tablette Android chinoise, fixée au mur, 4 caméras sur le dashboard `http://192.168.1.122:8123/dashboard-cameras/camera`. Nom dans HA : « Tablette camera ».
- Caméras Frigate : `camera_entree`, `camera_jardin`, `camera_jardin_2`, `camera_voiture` (capteurs `binary_sensor.<nom>_person_occupancy`).
- Une seconde tablette (Redmi, Fully Kiosk) affiche un autre dashboard et n'a pas de souci de Wi-Fi.
- Surveillance réseau dans HA : `binary_sensor.tablette_cameras_ping` (adresse de la tablette `192.168.1.154`), historique dans **Historique** ou **Activité**.
- Réglages actuels : veille activée de 21:00 à 06:00, mise en veille après 1 min, rechargement périodique sur « Jamais ».

## Sécurité du dépôt

- Version 24 : le mot de passe de la clé n'est plus dans le code. **Il reste visible dans l'historique git**, avec la clé : la clé est donc à considérer comme exposée. L'utilisateur a choisi en connaissance de cause de **garder cette clé** (changer de clé oblige à désinstaller l'appli une fois). Ne pas le relancer là-dessus.
- Vérifier que l'APK garde la même clé : exporter le certificat de `samoth-dash.jks` et vérifier qu'il figure dans l'APK publié (signature v2 seulement, pas de fichier `META-INF/*.RSA`).

## Ce qui reste à faire

- **Surveiller les coupures du Wi-Fi** sur plusieurs jours. Du 9 au 10 novembre, aucune coupure ni avec HA kiosk ni avec Samoth Dash, sauf au changement d'appli. Les longues coupures du 9 au matin restent inexpliquées. Piste : réserver l'adresse de la tablette dans la box SFR (à confirmer si c'est fait).
- **Réglage du réveil à l'approche** : seuils estimés (`CELL_DIFF`, `MIN_CELLS` dans `MotionDetector.kt`). Ça fonctionne chez l'utilisateur, à ajuster s'il se plaint.
- **Idée proposée, pas faite** : démarrer l'alerte sur le dernier instantané Frigate de la personne, puis passer au direct, pour compenser le retard de détection.
- **Capteurs en double dans HA** : `sensor.tablette_camera_batterie` et `sensor.tablette_cameras_batterie` existent tous les deux. À vérifier avec l'autre conversation lequel garder dans l'automatisation de la prise.
- Le flux vidéo figé n'a jamais été provoqué en test réel.
- La branche `secret-signature` (déjà fusionnée dans `main`) et la branche `apercu` peuvent être supprimées depuis GitHub : la session Claude n'a pas le droit de supprimer une branche distante.
- Les flux MJPEG ne sont pas détectés comme figés : seul le rechargement périodique les couvre.


## Graphify : explorer le code sans gaspiller de tokens
Avant grep, glob ou la lecture de fichiers entiers, utilise les outils Graphify :
- où est défini un symbole -> graphify_find, puis graphify_node
- comment marche une fonctionnalité -> query_graph
- qui appelle X -> graphify_callers
- impact d'une modification -> graphify_impact (include_risk=true)
- tests liés -> graphify_tests_for
- dépendances d'un fichier -> graphify_file_neighbors
Compare le commitSha renvoyé avec `git rev-parse HEAD` : identique = fais confiance au résultat, sinon relis seulement les fichiers modifiés.
Garde grep pour du texte précis, la config et les fichiers non indexés.
Ne lis que les lignes utiles d'un fichier, jamais un gros fichier en entier.
