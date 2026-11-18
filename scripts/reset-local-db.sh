#!/usr/bin/env bash
# Réinitialise une base PostgreSQL de MLA Finance (locale ou en ligne) et la recharge avec les
# données du fichier de suivi (MLA-ICC-YDE-FINANCE-2026). ATTENTION : toutes les données de la base
# sont effacées, comptes utilisateurs compris.
#
# Le script ne crée AUCUN compte : en mode app.migrate-only, ni DevAdminSeeder (admin1234) ni
# BootstrapAdminRunner (prod) ne tournent, et il échoue si la table users n'est pas vide à la fin.
#   - en local : le premier démarrage en profil dev crée le compte admin@mla-finance.dev ;
#   - en ligne : redémarrer le service, BootstrapAdminRunner crée le SUPER_ADMIN à partir de
#     BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD (variables Railway).
#
# Usage :
#   ./scripts/reset-local-db.sh [source] [--yes]
#
#   source : un .sql déjà généré, un classeur .xlsx (téléchargé depuis Google Sheets) ou l'export
#            texte du classeur. Par défaut : data/import/mla-import.sql s'il existe.
#   --yes  : ne pas demander de confirmation.
#
# Connexion (variables d'environnement, valeurs par défaut entre parenthèses) :
#   PGHOST (localhost)  PGPORT (5432)  PGDATABASE (mla_finance)  PGUSER (utilisateur courant)  PGPASSWORD
#
# La base visée par les migrations Java est forcée sur PG* (arguments en ligne de commande) : un
# DATABASE_URL ou un .env ne peut pas les envoyer ailleurs que psql.
#
# Étapes : base créée si besoin, schéma vidé, migrations Liquibase appliquées par le backend
# (app.migrate-only), données chargées en une transaction, puis contrôle des totaux.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
unset DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_PROFILES_ACTIVE
export PGHOST="${PGHOST:-localhost}" PGPORT="${PGPORT:-5432}" PGDATABASE="${PGDATABASE:-mla_finance}" PGUSER="${PGUSER:-$USER}"

SOURCE=""
ASSUME_YES=0
for arg in "$@"; do
  case "$arg" in
    --yes|-y) ASSUME_YES=1 ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) SOURCE="$arg" ;;
  esac
done
if [[ -z "$SOURCE" && -f "$ROOT/data/import/mla-import.sql" ]]; then
  SOURCE="$ROOT/data/import/mla-import.sql"
fi
[[ -n "$SOURCE" && -f "$SOURCE" ]] || { echo "Source introuvable : indiquez un .sql, .xlsx ou l'export du fichier de suivi." >&2; exit 1; }

say() { printf '\n\033[1m▸ %s\033[0m\n' "$*"; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "Commande requise introuvable : $1 ($2)" >&2; exit 1; }; }
need psql "client PostgreSQL : brew install libpq && brew link --force libpq"
need java "Java 21 : brew install openjdk@21"
need mvn "Maven : brew install maven"

case "$PGHOST" in localhost|127.0.0.1|::1) REMOTE=0 ;; *) REMOTE=1 ;; esac
echo "Base cible : postgresql://$PGUSER@$PGHOST:$PGPORT/$PGDATABASE$([[ $REMOTE -eq 1 ]] && echo '   (EN LIGNE)')"
echo "Source     : $SOURCE"
if [[ $ASSUME_YES -ne 1 ]]; then
  read -r -p "Toutes les données de cette base vont être effacées. Continuer ? [o/N] " answer
  [[ "$answer" =~ ^[oOyY]$ ]] || { echo "Annulé."; exit 1; }
fi

mkdir -p "$ROOT/data/import"

say "1/5 Préparation de la base"
if ! psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$PGDATABASE'" | grep -q 1; then
  createdb "$PGDATABASE"
  echo "Base $PGDATABASE créée."
fi
psql -q -v ON_ERROR_STOP=1 -c "SET client_min_messages = warning; DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;" >/dev/null
echo "Schéma public vidé."

say "2/5 Compilation du backend"
(cd "$ROOT/backend" && mvn -q -DskipTests package)
JAR="$(ls "$ROOT"/backend/target/mla-finance-backend-*.jar | head -1)"

say "3/5 Migrations Liquibase (structure + données de référence, aucun compte)"
# app.migrate-only désactive DevAdminSeeder (aucun compte admin1234) ; BootstrapAdminRunner n'existe
# qu'en profil prod. Le mot de passe passe par l'environnement, jamais en argument visible.
SPRING_DATASOURCE_PASSWORD="${PGPASSWORD:-}" \
java -jar "$JAR" --spring.profiles.active=dev,local --app.migrate-only=true \
  --spring.datasource.url="jdbc:postgresql://$PGHOST:$PGPORT/$PGDATABASE" \
  --spring.datasource.username="$PGUSER" \
  --spring.main.web-application-type=none --logging.level.root=WARN \
  --logging.level.org.mla.finance=INFO > "$ROOT/data/import/migrate.log" 2>&1 \
  || { echo "Échec des migrations, voir data/import/migrate.log" >&2; tail -20 "$ROOT/data/import/migrate.log" >&2; exit 1; }
echo "Migrations appliquées."

say "4/5 Import des données du fichier de suivi"
SQL="$SOURCE"
if [[ "$SOURCE" != *.sql ]]; then
  need python3 "Python 3 (inclus dans les outils de ligne de commande Xcode)"
  SQL="$ROOT/data/import/mla-import.sql"
  python3 "$ROOT/scripts/import-excel/import_journal.py" "$SOURCE" -o "$SQL"
fi
psql -q -v ON_ERROR_STOP=1 -f "$SQL" >/dev/null
echo "Données chargées."
USERS="$(psql -tAc "SELECT count(*) FROM users")"
[[ "$USERS" == 0 ]] || { echo "ANOMALIE : $USERS compte(s) utilisateur présent(s) après l'import, alors qu'aucun ne doit être créé." >&2; exit 1; }
echo "Aucun compte utilisateur créé (users = 0)."

say "5/5 Contrôle (à comparer avec l'onglet Synthèse du fichier)"
psql -q -P footer=off <<'SQL'
WITH caisse AS (SELECT id FROM donation_categories WHERE main_fund)
SELECT
  (SELECT count(*) FROM contributions) + (SELECT count(*) FROM withdrawals)        AS "Mouvements",
  (SELECT count(*) FROM donors WHERE member_role IS NOT NULL)                     AS "Membres",
  (SELECT coalesce(sum(amount),0) FROM contributions WHERE category_id = (SELECT id FROM caisse))
  - (SELECT coalesce(sum(amount),0) FROM withdrawals WHERE status = 'PROCESSED' AND category_id = (SELECT id FROM caisse))
                                                                                   AS "Solde caisse MLA",
  (SELECT coalesce(sum(c.amount),0) FROM contributions c JOIN projects p ON p.id = c.project_id WHERE p.status = 'ACTIVE')
  - (SELECT coalesce(sum(w.amount),0) FROM withdrawals w JOIN projects p ON p.id = w.project_id WHERE p.status = 'ACTIVE' AND w.status = 'PROCESSED')
                                                                                   AS "Collectes en cours",
  (SELECT coalesce(sum(amount),0) FROM contributions
     WHERE nature = 'MONTHLY_CONTRIBUTION' AND extract(year FROM period_month) = extract(year FROM current_date))
                                                                                   AS "Contrib. mensuelles (année)";
SQL

if [[ $REMOTE -eq 1 ]]; then cat <<EOF

Base en ligne prête, sans aucun compte. Redémarrer le service applicatif (Railway) : au démarrage,
BootstrapAdminRunner crée le SUPER_ADMIN défini par BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD.
EOF
else cat <<EOF

Base prête. Pour lancer l'application sur cette base :
  cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev,local
  cd frontend && npm run dev
Au premier démarrage en profil dev : admin@mla-finance.dev / admin1234 (compte de développement).
EOF
fi
