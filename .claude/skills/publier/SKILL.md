---
name: publier
description: Publie une modification de Samoth Dash de bout en bout - commit, push sur main, suivi de la fabrication GitHub, vérification de la Release et de la clé de signature, mise à jour de CLAUDE.md. À utiliser à la fin de chaque modification (règle permanente du projet).
---

# Publier une modification

À faire à la fin de **chaque** modification, sans attendre que l'utilisateur le demande.

## 1. Commit et push

```bash
git pull -q origin main
git add -A
git commit -m "<message court en français>"   # finir par les lignes d'attribution demandées
git push origin main
# Vérifier que le push est bien arrivé (le proxy coupe parfois sans erreur visible)
[ "$(git ls-remote origin main | cut -c1-7)" = "$(git rev-parse --short=7 HEAD)" ] || git push origin main
```

Ne jamais pousser un changement qui dépend d'un secret GitHub que l'utilisateur n'a pas encore créé : le mettre sur une branche à part en attendant.

## 2. Suivre la fabrication jusqu'au bout

```bash
sleep 25
until [ "$(gh api repos/BDBOFF/SamothDash/actions/runs --jq '[.workflow_runs[]|select(.name=="Compiler Samoth Dash" and .status!="completed")]|length')" = 0 ]; do sleep 10; done
gh api repos/BDBOFF/SamothDash/actions/runs --jq '[.workflow_runs[]|select(.name=="Compiler Samoth Dash")][0]|[.conclusion,.run_number,.head_sha[0:7]]|@tsv'
gh api repos/BDBOFF/SamothDash/releases/latest --jq .tag_name   # doit valoir build-<run_number>
```

- `success` et le bon `build-N` : continuer.
- Échec : lire l'erreur (`gh run view <id> -R BDBOFF/SamothDash --log-failed | tail -40`), corriger, recommencer. Une erreur `HTTP 502` à « Publier la version » vient de GitHub : relancer suffit.
- « Secret SAMOTH_KEYSTORE_PASSWORD manquant » : c'est l'utilisateur qui doit le créer (Settings → Secrets and variables → Actions).

## 3. Si la signature a pu changer

Seulement si `app/build.gradle.kts`, la clé ou `build.yml` ont été touchés : vérifier que l'APK publié porte toujours le certificat de `app/samoth-dash.jks` (signature v2, pas de `META-INF/*.RSA`). Exporter le certificat de la clé (`keytool -exportcert`) et vérifier que ses octets figurent dans l'APK. Le mot de passe n'est pas dans le dépôt : ne jamais l'écrire dans un fichier ni un commit.

## 4. Mettre à jour CLAUDE.md

Ajouter ce qui a changé : décision et pourquoi, bug corrigé, ce qui reste à faire, numéro de version. Puis commit et push de CLAUDE.md (une Release est publiée aussi, c'est normal).

## 5. Dire à l'utilisateur

En français simple : le numéro de version, comment l'installer (tuile **Mise à jour** dans les réglages de la tablette), ce qui a été testé et ce qui ne l'a pas été.
