#!/usr/bin/env python3
"""Bloque l'écriture ou le commit d'un mot de passe, jeton ou clé : le dépôt est public."""
import json, re, subprocess, sys

MOTIFS = [
    (r'(store|key)Password\s*=\s*"[^"]+"', "mot de passe de signature écrit en clair"),
    (r'\bghp_[A-Za-z0-9]{20,}|\bgithub_pat_[A-Za-z0-9_]{20,}', "jeton GitHub"),
    (r'-----BEGIN [A-Z ]*PRIVATE KEY-----', "clé privée"),
    (r'\beyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{10,}', "jeton (JWT), par exemple un jeton Home Assistant"),
    (r'(?i)(api[_-]?key|secret|token)\s*[:=]\s*["\'][A-Za-z0-9_\-]{24,}["\']', "clé ou jeton écrit en clair"),
]

def cherche(texte):
    for motif, nom in MOTIFS:
        if re.search(motif, texte or ""):
            return nom
    return None

data = json.load(sys.stdin)
outil = data.get("tool_name", "")
entree = data.get("tool_input", {}) or {}

if outil in ("Write", "Edit", "MultiEdit"):
    textes = [entree.get("content"), entree.get("new_string")]
    textes += [e.get("new_string") for e in entree.get("edits", []) or []]
    trouve = cherche("\n".join(t for t in textes if t))
elif outil == "Bash" and re.search(r"\bgit\s+(commit|push)\b", entree.get("command", "")):
    diff = subprocess.run(["git", "diff", "--cached", "-U0"], capture_output=True, text=True).stdout
    if re.search(r"\bgit\s+push\b", entree.get("command", "")):
        diff += subprocess.run(["git", "log", "-p", "@{u}..HEAD"], capture_output=True, text=True).stdout
    ajouts = "\n".join(l for l in diff.splitlines() if l.startswith("+"))
    trouve = cherche(ajouts)
else:
    trouve = None

if trouve:
    print(f"Bloqué : {trouve} détecté. Le dépôt est public. Utilise un secret GitHub "
          f"(ex. SAMOTH_KEYSTORE_PASSWORD) ou une variable d'environnement.", file=sys.stderr)
    sys.exit(2)
