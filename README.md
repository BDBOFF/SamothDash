# Samoth Dash — kiosque Home Assistant pour tablette

Affiche un tableau de bord Home Assistant en plein écran, sans barre de gauche ni barre du haut, et envoie la batterie de la tablette à HA.

## Télécharger

**https://github.com/BDBOFF/SamothDash/releases/latest/download/SamothDash.apk**

## Installer

1. Ouvrir le lien sur la tablette, installer l'APK (autoriser les applis inconnues si demandé).
2. Saisir l'adresse du tableau de bord, par exemple `http://192.168.1.10:8123/lovelace/0`.
3. Se connecter à HA (une seule fois) et accepter l'envoi en veille.

**Réglages** : glisser du bord gauche de l'écran vers la droite.

**Démarrage automatique** : Réglages → *Choisir comme écran d'accueil*.

## Dans Home Assistant

La tablette apparaît dans *Paramètres → Appareils → Mobile App* (nom « Tablette » par défaut) :

- `sensor.tablette_batterie` : niveau en %
- `binary_sensor.tablette_en_charge` : alimentée ou non
- `sensor.tablette_temperature_batterie` : température

Envoi à chaque changement et au moins chaque minute. Rien à configurer côté HA tant que `default_config` ou `mobile_app` est actif.

## Surveillance

Chaque fonction a son interrupteur dans les réglages :

- **Flux figés** : recharge si une vidéo ne progresse plus pendant une minute, et à intervalle régulier au choix (utile pour les flux qu'on ne peut pas analyser).
- **Alerte plein écran** : 30 s en plein écran quand `binary_sensor.X_person_occupancy` passe à `on` et que `camera.X` existe (noms de l'intégration Frigate). Un appui ferme.
- **Veille et réveil à l'approche** : écran noir et rétroéclairage au minimum sans présence ; la caméra frontale, un toucher ou une alerte le rallument. Il faut un peu de lumière pour détecter un mouvement. La veille peut être limitée à une plage horaire (ex. 21:00 à 06:00) : en dehors, l'écran reste allumé et la caméra frontale est coupée.

## Technique

Kotlin, sans Compose · minSdk 26 (Android 8). Frontend HA en mode `external_auth`, comme l'appli officielle.
APK signé avec `app/samoth-dash.jks` (toujours la même clé, pour que les mises à jour s'installent par-dessus).
