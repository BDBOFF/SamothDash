---
name: test-emulateur
description: Lance le test complet de Samoth Dash sur un émulateur Android avec un vrai Home Assistant (alerte, veille, réveil, rechargements), ou la capture des réglages en 1280x800, puis lit les résultats. À utiliser après une modification de l'alerte, de la veille, du réseau, du masquage des barres ou de l'écran de réglages.
---

# Tester sur émulateur

Deux tests, lancés à la main sur GitHub Actions. Les captures arrivent sur la branche `apercu` (écrasée à chaque fois).

| Test | Workflow | Durée | Quand |
|---|---|---|---|
| Complet | `preview.yml` (`.github/preview.sh`) | ~25 min | alerte, veille, réveil, rechargements, réseau, barres |
| Réglages | `reglages.yml` (`.github/reglages.sh`) | ~8 min | changement de l'écran de réglages |

Les tests compilent la version debug, signée avec la clé de débogage : pas besoin du secret.

## Lancer et attendre

```bash
W=preview.yml   # ou reglages.yml
# Attendre qu'aucun autre test ne tourne, sinon les captures se mélangent sur apercu
gh api -X POST repos/BDBOFF/SamothDash/actions/workflows/$W/dispatches -f ref=main
sleep 30
until [ "$(gh api repos/BDBOFF/SamothDash/actions/runs --jq '[.workflow_runs[]|select(.status!="completed")]|length')" = 0 ]; do sleep 15; done
gh api repos/BDBOFF/SamothDash/actions/runs --jq '.workflow_runs[0]|[.name,.conclusion,.run_number]|@tsv'
```

Lancer la commande d'attente en arrière-plan : elle dépasse souvent 10 minutes.

## Lire les résultats

```bash
D=/tmp/claude-0/shots; rm -rf $D; git clone -q --depth 1 -b apercu https://github.com/BDBOFF/samothdash $D
grep -v mouvement $D/resultats.txt   # journal SamothDash : alerte, veille, réveil, rechargements
ls $D/*.png                         # regarder les captures avec Read
```

## Pièges connus

- Si `camera.demo_camera` manque dans les états, le Home Assistant de test n'a pas chargé la démo (numpy, ffmpeg) : l'alerte ne peut pas être testée. Ce n'est pas l'appli.
- Un échec à « Publier les captures » avec `cd: shots` veut dire que le Home Assistant de test n'a pas démarré : relancer une fois.
- Coordonnées des appuis (`adb shell input tap`) prévues pour l'écran 1800x2560 du profil `pixel_c` : à revoir si la disposition des réglages change.
- Une coupure réseau simulée ne reproduit pas forcément les vraies coupures de la tablette.
- Le test ne remplace pas la tablette de l'utilisateur : le dire quand on lui présente le résultat.
