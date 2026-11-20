#!/usr/bin/env python3
"""
Contacts des membres (email, téléphone WhatsApp) à partir de la « Fiche des membres MLA-SE » (réponses au
formulaire Google « Vos coordonnées », export .xlsx). Le classeur financier importé par import_journal.py n'a
pas de contacts : ce script les complète.

Usage :
    python3 scripts/import-excel/import_contacts.py data/import/fiche-membres.xlsx data/import/membres.csv \
        > data/import/mla-contacts.sql

membres.csv : export des membres de la base (une ligne « display_name|first_name|last_name » par membre), ex. :
    psql -d mla_finance -Atc "select display_name, coalesce(first_name,''), coalesce(last_name,'') \
        from donors where member_role is not null" > data/import/membres.csv

Rapprochement par les mots du prénom et du nom (accents et casse ignorés) : une fiche est rattachée au membre
qui partage le plus de mots avec elle, seulement si ce choix est sans ambiguïté. Le SQL produit ne remplit que les
champs encore vides (jamais d'écrasement) et cible chaque membre par son nom usuel (unique), donc il s'applique
aussi bien en local qu'en production. Le compte rendu (stderr) masque les contacts.

Téléphone : 237 + 9 chiffres (même forme que le payeur PawaPay). Une fiche avec deux numéros garde le premier ; un
numéro illisible est ignoré et signalé.
"""
import re
import sys
import unicodedata

import openpyxl

EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[A-Za-z]{2,}$")


def norm(text):
    text = unicodedata.normalize("NFKD", str(text or "")).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z]+", " ", text).strip()


def words(*parts):
    stop = {"epse", "epouse", "de", "la", "le"}
    return {w for p in parts for w in norm(p).split() if len(w) >= 3 and w not in stop}


def phone(raw):
    if raw is None:
        return None, None
    if isinstance(raw, float) and raw.is_integer():
        raw = int(raw)  # cellule numérique : « 2.37697E+11 » n'est qu'un affichage, la valeur est entière
    text = str(raw)
    if re.search(r"e\+", text, re.I):
        return None, "numéro abîmé par le tableur"
    first = re.split(r"/|\bet\b|,", text)[0]
    digits = re.sub(r"\D", "", first)
    if digits.startswith("237"):
        digits = digits[3:]
    if len(digits) == 9 and digits.startswith("6"):
        return "237" + digits, None
    return None, "numéro illisible"


def mask_email(e):
    return e[0] + "•••" + e[e.index("@"):]


def sql(value):
    return "'" + value.replace("'", "''") + "'"


def main(sheet_path, members_path):
    members = []
    for line in open(members_path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line:
            continue
        display, first, last = (line.split("|") + ["", ""])[:3]
        members.append((display, words(display, first, last), words(first, last)))

    ws = openpyxl.load_workbook(sheet_path, read_only=True, data_only=True).worksheets[0]
    rows = list(ws.iter_rows(values_only=True))
    header = [norm(h) for h in rows[0]]
    col = {name: header.index(name) for name in ("adresse e mail", "prenom", "nom", "numero de telephone")}

    out, report, used = [], [], set()
    print("-- Contacts des membres (généré par scripts/import-excel/import_contacts.py) : champs vides seulement.")
    print("begin;")
    for row in rows[1:]:
        row = list(row) + [None] * (len(header) - len(row))
        first, last = row[col["prenom"]], row[col["nom"]]
        if not (first or last):
            continue
        fiche = words(first, last)
        scored = sorted(((len(fiche & m[2]) or len(fiche & m[1]), m[0]) for m in members), reverse=True)
        best, second = scored[0], scored[1] if len(scored) > 1 else (0, None)
        label = f"{str(first).strip()} {str(last).strip()}"
        if best[0] == 0 or best[0] == second[0]:
            report.append(f"NON RATTACHÉE : {label}")
            continue
        display = best[1]
        if display in used:
            report.append(f"DOUBLON ignoré : {label} → {display}")
            continue
        used.add(display)
        email = str(row[col["adresse e mail"]] or "").strip().lower()
        email = email if EMAIL.match(email) else None
        tel, tel_issue = phone(row[col["numero de telephone"]])
        sets = []
        if email:
            sets.append(f"email = coalesce(nullif(email, ''), {sql(email)})")
        if tel:
            sets.append(f"phone = coalesce(nullif(phone, ''), {sql(tel)})")
        notes = []
        if not email:
            notes.append("email invalide ou absent")
        if tel_issue or not tel:
            notes.append(tel_issue or "téléphone absent")
        report.append(f"{display:<16} ← {label:<40} email {mask_email(email) if email else '—':<28} "
                      f"tél {tel[:5] + '•••' + tel[-3:] if tel else '—'}" + (f"   ({', '.join(notes)})" if notes else ""))
        if sets:
            print(f"update donors set {', '.join(sets)} where display_name = {sql(display)};")
    print("commit;")
    missing = [m[0] for m in members if m[0] not in used]
    report.append(f"\n{len(used)} membre(s) rattaché(s) ; sans fiche : {', '.join(missing) or 'aucun'}")
    print("\n".join(report), file=sys.stderr)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
