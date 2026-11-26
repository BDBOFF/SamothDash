#!/usr/bin/env python3
"""Après une modification de kiosk.js : vérifie la syntaxe et les pièges des WebView anciennes."""
import json, re, shutil, subprocess, sys

data = json.load(sys.stdin)
chemin = (data.get("tool_input", {}) or {}).get("file_path", "")
if not chemin.endswith("kiosk.js"):
    sys.exit(0)

problemes = []
if shutil.which("node"):
    r = subprocess.run(["node", "--check", chemin], capture_output=True, text=True)
    if r.returncode != 0:
        problemes.append("Erreur de syntaxe :\n" + r.stderr.strip())
texte = open(chemin, encoding="utf-8").read()
if re.search(r"\binset\s*:", texte):
    problemes.append("`inset:` n'est pas compris par les WebView anciennes : utiliser top/left/right/bottom:0.")
if re.search(r"\?\.|\?\?|=>", texte):
    problemes.append("Syntaxe récente (?. ?? =>) : garder du JavaScript simple pour les WebView anciennes, comme le reste du fichier.")

if problemes:
    print("kiosk.js : " + "\n".join(problemes), file=sys.stderr)
    sys.exit(2)
