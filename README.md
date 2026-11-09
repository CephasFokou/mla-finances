# MLA Finance Management System

Système de gestion financière pour la chorale MLA (ICC Yaoundé) : suivi des contributions, projets/enveloppes, retraits, rapports et médias.

Monorepo :
- `backend/` — API Spring Boot 3 / Java 21 / PostgreSQL / Liquibase (voir `backend/README.md`), sert aussi bien le back-office authentifié que les endpoints publics (`/api/v1/public/**`) consommés par le site de don.
- `frontend/` — back-office authentifié (React / TypeScript).
- `frontend-public/` — site public de don (React / TypeScript), volontairement détaché du back-office : projet, build et déploiement indépendants, pour ne jamais exposer le code de l'application authentifiée sur une page ouverte à tout le monde. Voir `claude/module-front-office-public-spec.md` dans le Projet claude.ai pour le détail de cette séparation.

Références validées : Cahier des Charges v1.1 et Document Technique v1.2 (conservés dans le Projet claude.ai "MLA - FINANCES").

## Démarrer en local sur PostgreSQL avec les vraies données

Le profil `dev` seul tourne sur H2 en mémoire et démarre vide (seule la caisse « Caisse MLA » existe).
Pour travailler sur les données réelles du fichier de suivi (`MLA-ICC-YDE-FINANCE-2026`) :

1. Placer le classeur dans `data/import/` (dossier ignoré par git : il contient des noms et des montants
   réels) — un `.xlsx` téléchargé depuis Google Sheets (nécessite `pip3 install openpyxl`), ou l'export
   texte du classeur, ou un `mla-import.sql` déjà généré.
2. Réinitialiser la base locale (⚠️ efface tout son contenu) :
   ```bash
   ./scripts/reset-local-db.sh data/import/<fichier>
   ```
   Base par défaut : `localhost:5432/mla_finance`, utilisateur système (surchargeable par `PGHOST`,
   `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`). Le script crée la base si besoin, applique les
   migrations, charge les données et affiche les totaux à comparer avec l'onglet Synthèse.
3. Lancer l'application :
   ```bash
   cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev,local
   cd frontend && npm run dev
   ```
   Connexion : `admin@mla-finance.dev` / `admin1234`.

Correspondance fichier ↔ application : voir `scripts/import-excel/import_journal.py` (en-tête) et
`claude/module-grand-livre-spec.md` dans le Projet claude.ai.
