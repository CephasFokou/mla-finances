#!/usr/bin/env python3
"""
Génère le script SQL d'import du fichier de suivi MLA (MLA-ICC-YDE-FINANCE-2026) dans la base de
l'application, à charger APRÈS les migrations Liquibase (voir scripts/reset-local-db.sh).

Sources acceptées :
  - un classeur .xlsx (Google Sheets > Fichier > Télécharger > Microsoft Excel) — nécessite
    openpyxl (pip install openpyxl) ;
  - l'export texte du classeur (sections « ## Sheet name: <onglet> » suivies du CSV de l'onglet).

Onglets lus : Membres, Collectes, Activités, Journal. Les autres (Synthèse, Calendrier...) sont des
calculs : ils servent seulement de contrôle (--check affiche les totaux attendus).

Règles de correspondance (confirmées avec Cephas le 05/11/2026) :
  - « Caisse MLA » -> catégorie caisse principale (main_fund) ; toute autre caisse -> une collecte
    (projet) de l'onglet Collectes ;
  - Entrée -> contributions, Sortie -> retraits déjà décaissés (PROCESSED) à la date du Journal ;
  - « Mois de contribution » -> mois d'attribution (period_month) des contributions mensuelles ;
  - les deux lignes d'un même transfert interne (même date, même montant) sont liées ;
  - « Anonyme » -> don anonyme sans personne ; « Transfert interne » -> aucune personne.

Usage :
  python3 import_journal.py <fichier.xlsx|export.txt> -o mla-import.sql [--check]
Uniquement la bibliothèque standard (sauf openpyxl pour un .xlsx).
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import io
import re
import sys
from collections import defaultdict
from decimal import Decimal, InvalidOperation

MAIN_FUND = "Caisse MLA"
NO_PERSON = {"Transfert interne", ""}
ANONYMOUS = {"Anonyme"}

NATURES = {
    "Contribution mensuelle": "MONTHLY_CONTRIBUTION",
    "Contribution": "CONTRIBUTION",
    "Don": "DONATION",
    "Retour": "RETURN",
    "Avance": "ADVANCE",
    "Dépense": "EXPENSE",
    "Remise": "HANDOVER",
    "Complément de la caisse": "CASH_TOP_UP",
    "Remboursement": "REPAYMENT",
    "Frais": "FEE",
    "Transfert": "TRANSFER",
    "Régularisation": "REGULARIZATION",
}
INFLOW_ONLY = {"MONTHLY_CONTRIBUTION", "CONTRIBUTION", "DONATION", "RETURN", "ADVANCE"}
OUTFLOW_ONLY = {"EXPENSE", "HANDOVER", "CASH_TOP_UP", "REPAYMENT", "FEE"}

CHANNELS = {"OM": "ORANGE_MONEY", "MoMo": "MTN_MOMO", "Cash": "CASH", "Virement": "BANK_TRANSFER",
            "Carte": "CARD", "Autre": "OTHER", "": "OTHER"}
ROLES = {"Responsable": "RESPONSABLE", "Membre": "MEMBRE"}
MEMBER_STATUS = {"Actif": "ACTIVE", "Active": "ACTIVE", "Parti(e)": "LEFT", "Parti": "LEFT", "Partie": "LEFT",
                 "Non intégré": "NOT_INTEGRATED", "Non intégrée": "NOT_INTEGRATED"}
PROJECT_STATUS = {"Clôturé": "COMPLETED", "En cours": "ACTIVE", "À venir": "DRAFT", "A venir": "DRAFT"}
ACTIVITY_STATUS = {"Clôturé": "COMPLETED", "En cours": "PLANNED", "À venir": "PLANNED", "A venir": "PLANNED"}


class ImportError_(Exception):
    pass


# --- lecture des onglets ------------------------------------------------------------------------

def read_sheets(path: str) -> dict[str, list[list]]:
    if path.lower().endswith((".xlsx", ".xlsm")):
        try:
            import openpyxl  # type: ignore
        except ImportError as exc:  # pragma: no cover
            raise ImportError_("Lire un .xlsx nécessite openpyxl : pip3 install openpyxl") from exc
        wb = openpyxl.load_workbook(path, data_only=True, read_only=True)
        return {ws.title: [list(r) for r in ws.iter_rows(values_only=True)] for ws in wb.worksheets}
    text = open(path, encoding="utf-8-sig").read()
    sheets = {}
    for part in re.split(r"^## Sheet name: ", text, flags=re.M)[1:]:
        name, body = part.split("\n", 1)
        sheets[name.strip()] = list(csv.reader(io.StringIO(body)))
    if not sheets:
        raise ImportError_("Aucun onglet trouvé : fichier attendu .xlsx ou export « ## Sheet name: ... ».")
    return sheets


def table(sheets: dict, name: str, first_header: str) -> list[dict]:
    """Lignes d'un tableau dont la ligne d'en-tête commence par first_header, jusqu'à la première ligne vide."""
    if name not in sheets:
        raise ImportError_(f"Onglet « {name} » introuvable (onglets : {', '.join(sheets)}).")
    rows = sheets[name]
    try:
        start = next(i for i, r in enumerate(rows) if r and cell(r[0]) == first_header)
    except StopIteration:
        raise ImportError_(f"En-tête « {first_header} » introuvable dans l'onglet « {name} ».") from None
    header = [cell(h) for h in rows[start]]
    out = []
    for r in rows[start + 1:]:
        values = [cell(v) if not isinstance(v, (int, float, Decimal, dt.date)) else v for v in r]
        if not any(str(v).strip() for v in values if v is not None):
            break
        values += [""] * (len(header) - len(values))
        out.append({h: v for h, v in zip(header, values) if h})
    return out


def cell(v) -> str:
    if v is None:
        return ""
    if isinstance(v, (dt.datetime, dt.date)):
        return v.strftime("%d/%m/%Y")
    return str(v).strip()


def amount(v) -> Decimal | None:
    if v is None or v == "":
        return None
    if isinstance(v, (int, float, Decimal)):
        return Decimal(str(v)).quantize(Decimal("0.01"))
    s = str(v).replace(" ", "").replace("\xa0", "").replace(" ", "").strip().rstrip(",")
    if not s or s == "-":
        return None
    try:
        return Decimal(s.replace(",", ".")).quantize(Decimal("0.01"))
    except InvalidOperation:
        raise ImportError_(f"Montant illisible : {v!r}") from None


def date(v) -> dt.date:
    if isinstance(v, dt.datetime):
        return v.date()
    if isinstance(v, dt.date):
        return v
    s = str(v).strip()
    for fmt in ("%d/%m/%Y", "%Y-%m-%d"):
        try:
            return dt.datetime.strptime(s, fmt).date()
        except ValueError:
            pass
    raise ImportError_(f"Date illisible : {v!r}")


def month(v) -> dt.date | None:
    if v is None or v == "":
        return None
    if isinstance(v, (dt.date, dt.datetime)):
        return dt.date(v.year, v.month, 1)
    m = re.fullmatch(r"(\d{4})-(\d{1,2})", str(v).strip())
    if not m:
        raise ImportError_(f"Mois de contribution illisible (attendu AAAA-MM) : {v!r}")
    return dt.date(int(m.group(1)), int(m.group(2)), 1)


# --- SQL ----------------------------------------------------------------------------------------

def q(v) -> str:
    if v is None or v == "":
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, Decimal):
        return format(v, "f")
    if isinstance(v, dt.date):
        return f"DATE '{v.isoformat()}'"
    return "'" + str(v).replace("'", "''") + "'"


def ts(d: dt.date) -> str:
    # Midi heure locale (Africa/Douala, UTC+1) = 11:00 UTC : le jour reste juste quel que soit le fuseau.
    return f"TIMESTAMP '{d.isoformat()} 11:00:00'"


def donor_id(name: str) -> str:
    return f"(SELECT id FROM donors WHERE display_name = {q(name)})"


def project_id(name: str) -> str:
    return f"(SELECT id FROM projects WHERE name = {q(name)})"


def activity_id(name: str) -> str:
    return "NULL" if not name else f"(SELECT id FROM activities WHERE name = {q(name)})"


def channel_id(code: str) -> str:
    return f"(SELECT id FROM payment_channels WHERE code = {q(code)})"


MAIN_FUND_ID = "(SELECT id FROM donation_categories WHERE main_fund = TRUE)"


def build(sheets: dict, source_label: str) -> tuple[str, dict]:
    members = table(sheets, "Membres", "Nom usuel")
    collections = table(sheets, "Collectes", "Collecte")
    activities = table(sheets, "Activités", "Activité")
    journal = table(sheets, "Journal", "Date")

    member_names = {m["Nom usuel"] for m in members}
    collection_names = {c["Collecte"] for c in collections}
    activity_names = {a["Activité"] for a in activities}

    # Normalisation et contrôles ligne par ligne (numéro = ligne du tableau Journal, 1 = première).
    moves = []
    errors = []
    for i, r in enumerate(journal, start=1):
        try:
            fund = r["Caisse / Collecte"]
            nature = NATURES.get(r["Nature"])
            if nature is None:
                raise ImportError_(f"nature inconnue « {r['Nature']} »")
            e, s = amount(r.get("Entrée (FCFA)")), amount(r.get("Sortie (FCFA)"))
            if (e is None) == (s is None):
                raise ImportError_("il faut une entrée OU une sortie")
            inflow = e is not None
            if inflow and nature in OUTFLOW_ONLY or not inflow and nature in INFLOW_ONLY:
                raise ImportError_(f"« {r['Nature']} » incompatible avec une {'entrée' if inflow else 'sortie'}")
            if fund != MAIN_FUND and fund not in collection_names:
                raise ImportError_(f"caisse/collecte « {fund} » absente de l'onglet Collectes")
            act = r.get("Activité (dépenses de la caisse)", "")
            if act and act not in activity_names:
                raise ImportError_(f"activité « {act} » absente de l'onglet Activités")
            if act and fund != MAIN_FUND:
                raise ImportError_("une activité ne concerne que la Caisse MLA")
            channel = CHANNELS.get(r.get("Canal", ""))
            if channel is None:
                raise ImportError_(f"canal inconnu « {r.get('Canal')} »")
            pm = month(r.get("Mois de contribution"))
            if nature == "MONTHLY_CONTRIBUTION":
                if fund != MAIN_FUND:
                    raise ImportError_("une contribution mensuelle va dans la Caisse MLA")
                if pm is None:
                    raise ImportError_("mois de contribution manquant")
            d = date(r["Date"])
            moves.append(dict(line=i, date=d, person=r["Membre / Tiers"], fund=fund, activity=act, nature=nature,
                              period=pm or dt.date(d.year, d.month, 1), amount=e if inflow else s, inflow=inflow,
                              channel=channel, description=r.get("Description", ""),
                              remark=r.get("Remarque (justification)", ""), group=None))
        except ImportError_ as exc:
            errors.append(f"Journal ligne {i} ({r.get('Date')}, {r.get('Membre / Tiers')}) : {exc}")
    if errors:
        raise ImportError_("\n".join(errors))

    # Transferts internes : appariement entrée/sortie de même date et même montant.
    pending = [m for m in moves if m["nature"] == "TRANSFER"]
    n = 0
    for m in pending:
        if m["group"] or not m["inflow"]:
            continue
        twin = next((o for o in pending if not o["inflow"] and not o["group"] and o["date"] == m["date"]
                     and o["amount"] == m["amount"] and o["fund"] != m["fund"]), None)
        if twin:
            n += 1
            m["group"] = twin["group"] = f"TRF-IMPORT-{n:02d}"

    third_parties = sorted({m["person"] for m in moves}
                           - member_names - NO_PERSON - ANONYMOUS)

    sql = [
        f"-- Import généré par scripts/import-excel/import_journal.py le {dt.datetime.now():%d/%m/%Y %H:%M}",
        f"-- Source : {source_label} — {len(moves)} mouvements, {len(members)} membres, "
        f"{len(third_parties)} tiers, {len(collections)} collectes, {len(activities)} activités.",
        "-- À charger sur une base fraîchement migrée (Liquibase), en une seule transaction.",
        "BEGIN;",
        "DO $$ BEGIN",
        "  IF EXISTS (SELECT 1 FROM contributions) OR EXISTS (SELECT 1 FROM withdrawals) OR EXISTS (SELECT 1 FROM projects) THEN",
        "    RAISE EXCEPTION 'La base contient déjà des mouvements ou des collectes : utilisez scripts/reset-local-db.sh.';",
        "  END IF;",
        "  IF NOT EXISTS (SELECT 1 FROM donation_categories WHERE main_fund) THEN",
        "    RAISE EXCEPTION 'Caisse principale absente : la migration 0012 n''a pas été appliquée.';",
        "  END IF;",
        "END $$;",
        "",
        "-- Membres (onglet Membres) puis tiers (noms du Journal absents de l'onglet Membres)",
    ]
    for m in members:
        role = ROLES.get(m.get("Rôle", ""), "MEMBRE")
        status = MEMBER_STATUS.get(m.get("Statut", ""), "ACTIVE")
        first, last = m.get("Prénom(s)", ""), m.get("Nom", "")
        full = " ".join(x for x in (first, last) if x) or m["Nom usuel"]
        sql.append("INSERT INTO donors (display_name, first_name, last_name, full_name, member_role, member_status, remark) "
                   f"VALUES ({q(m['Nom usuel'])}, {q(first)}, {q(last)}, {q(full)}, {q(role)}, {q(status)}, {q(m.get('Remarque', ''))});")
    for t in third_parties:
        sql.append(f"INSERT INTO donors (display_name, full_name, remark) VALUES ({q(t)}, {q(t)}, 'Tiers (non membre), importé du Journal');")

    sql.append("")
    sql.append("-- Collectes dédiées (onglet Collectes), hors caisse du MLA")
    dates_by_fund = defaultdict(list)
    for m in moves:
        dates_by_fund[m["fund"]].append(m["date"])
    for c in collections:
        ds = sorted(dates_by_fund.get(c["Collecte"], []))
        status = PROJECT_STATUS.get(c.get("Statut", ""), "ACTIVE")
        sql.append("INSERT INTO projects (name, description, status, opening_balance, start_date, end_date) VALUES ("
                   f"{q(c['Collecte'])}, {q(c.get('Commentaire', ''))}, {q(status)}, 0, "
                   f"{q(ds[0]) if ds else 'NULL'}, {q(ds[-1]) if ds and status == 'COMPLETED' else 'NULL'});")

    sql.append("")
    sql.append("-- Activités payées par la caisse (onglet Activités)")
    dates_by_activity = defaultdict(list)
    for m in moves:
        if m["activity"]:
            dates_by_activity[m["activity"]].append(m["date"])
    for a in activities:
        label = a.get("Date", "")
        try:
            event = date(label) if re.fullmatch(r"\d{1,2}/\d{1,2}/\d{4}", label) else None
        except ImportError_:
            event = None
        if event is None and dates_by_activity.get(a["Activité"]):
            event = min(dates_by_activity[a["Activité"]])
        status = ACTIVITY_STATUS.get(a.get("Statut", ""), "PLANNED")
        sql.append("INSERT INTO activities (name, period_label, event_date, status, comment) VALUES ("
                   f"{q(a['Activité'])}, {q(label)}, {q(event) if event else 'NULL'}, {q(status)}, {q(a.get('Commentaire', ''))});")

    sql.append("")
    sql.append("-- Journal : entrées -> contributions, sorties -> retraits décaissés")
    for m in moves:
        envelope = ("NULL", MAIN_FUND_ID) if m["fund"] == MAIN_FUND else (project_id(m["fund"]), "NULL")
        anonymous = m["person"] in ANONYMOUS
        person = "NULL" if anonymous or m["person"] in NO_PERSON else donor_id(m["person"])
        description = m["description"] or NATURE_LABELS[m["nature"]]
        comment = f"-- Journal ligne {m['line']}"
        if m["inflow"]:
            movement_type = "TRANSFER" if m["nature"] == "TRANSFER" else "STANDARD"
            sql.append(f"{comment}\nINSERT INTO contributions (project_id, category_id, donor_id, payment_channel_id, amount, "
                       "period_month, transaction_date, movement_type, nature, activity_id, anonymous, notes, remark, "
                       "transfer_group, created_at) VALUES ("
                       f"{envelope[0]}, {envelope[1]}, {person}, {channel_id(m['channel'])}, {q(m['amount'])}, "
                       f"{q(m['period'])}, {q(m['date'])}, {q(movement_type)}, {q(m['nature'])}, {activity_id(m['activity'])}, "
                       f"{q(anonymous)}, {q(description[:500])}, {q(m['remark'][:500])}, {q(m['group'])}, {ts(m['date'])});")
        else:
            sql.append(f"{comment}\nINSERT INTO withdrawals (project_id, category_id, amount, reason, status, nature, person_id, "
                       "payment_channel_id, activity_id, operation_date, remark, transfer_group, requested_at, decided_at, "
                       "processed_at, created_at) VALUES ("
                       f"{envelope[0]}, {envelope[1]}, {q(m['amount'])}, {q(description[:500])}, 'PROCESSED', {q(m['nature'])}, "
                       f"{person}, {channel_id(m['channel'])}, {activity_id(m['activity'])}, {q(m['date'])}, "
                       f"{q(m['remark'][:500])}, {q(m['group'])}, {ts(m['date'])}, {ts(m['date'])}, {ts(m['date'])}, {ts(m['date'])});")

    sql.append("")
    summary = (f"Import du fichier de suivi ({source_label}) : {len(moves)} mouvements, "
               f"{len(members)} membres, {len(collections)} collectes, {len(activities)} activités")
    sql.append(f"INSERT INTO audit_logs (entity_type, entity_id, action, summary) VALUES ('IMPORT', {len(moves)}, 'IMPORT', {q(summary[:500])});")
    sql.append("COMMIT;")

    stats = expected_totals(moves)
    stats.update(moves=len(moves), members=len(members), third_parties=len(third_parties),
                 collections=len(collections), activities=len(activities),
                 transfers=n)
    return "\n".join(sql) + "\n", stats


NATURE_LABELS = {v: k for k, v in NATURES.items()}


def expected_totals(moves: list[dict]) -> dict:
    balances = defaultdict(Decimal)
    for m in moves:
        balances[m["fund"]] += m["amount"] if m["inflow"] else -m["amount"]
    monthly = sum((m["amount"] for m in moves if m["nature"] == "MONTHLY_CONTRIBUTION"), Decimal(0))
    return dict(main_fund_balance=balances.pop(MAIN_FUND, Decimal(0)),
                collection_balances={k: v for k, v in balances.items()},
                monthly_contributions=monthly)


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("source", help="classeur .xlsx ou export texte du fichier de suivi")
    p.add_argument("-o", "--output", required=True, help="fichier SQL à écrire")
    p.add_argument("--label", help="libellé de la source dans le journal d'audit (défaut : nom du fichier)")
    args = p.parse_args(argv)
    import os
    try:
        sql, stats = build(read_sheets(args.source), args.label or os.path.basename(args.source))
    except ImportError_ as exc:
        print(f"Import impossible :\n{exc}", file=sys.stderr)
        return 1
    with open(args.output, "w", encoding="utf-8") as f:
        f.write(sql)
    fmt = lambda d: f"{d:,.0f}".replace(",", " ")
    print(f"{args.output} : {stats['moves']} mouvements, {stats['members']} membres, {stats['third_parties']} tiers, "
          f"{stats['collections']} collectes, {stats['activities']} activités, {stats['transfers']} transferts appariés.")
    print(f"Totaux attendus après import : caisse MLA {fmt(stats['main_fund_balance'])} FCFA, "
          f"contributions mensuelles {fmt(stats['monthly_contributions'])} FCFA.")
    for name, bal in stats["collection_balances"].items():
        if bal:
            print(f"  collecte « {name} » : reste {fmt(bal)} FCFA")
    return 0


if __name__ == "__main__":
    sys.exit(main())
