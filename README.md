# MLA Finance Management System

Système de gestion financière pour la chorale MLA (ICC Yaoundé) : suivi des contributions, projets/enveloppes, retraits, rapports et médias.

L'argent des dons en ligne est sur le portefeuille Mobile Money partagé, séparé par projet par le service
`wallet-ledger` : les dons y entrent (dépôts) et un retrait validé peut en sortir par Mobile Money depuis le
back-office (voir « Décaissement des retraits par Mobile Money » dans `backend/README.md`). Chaque retrait
décaissé a un bon de décaissement PDF téléchargeable, et un retrait est validé par une autre personne que
celle qui l'a saisi (sauf super administrateur, auto-validation tracée). Les frais Mobile Money sont enregistrés
automatiquement chaque matin, et la page Retraits signale tout écart avec le solde Mobile Money réel. Les membres
qui n'ont pas versé leur contribution du mois peuvent être relancés par email et WhatsApp depuis l'écran Relances,
et leurs réponses (« Bien reçu », date promise, « C'est déjà fait ») sont suivies automatiquement.

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
3. Variables locales : `cp backend/.env.example backend/.env` et compléter (secret JWT, clé Resend, jeton
   PawaPay sandbox…) ; `cp frontend-public/.env.example frontend-public/.env.local` (numéro de paiement MLA
   facultatif). Détail dans `backend/README.md`.
4. Lancer l'application :
   ```bash
   ./scripts/dev-backend.sh                 # backend, profils dev,local, variables de backend/.env
   cd frontend && npm run dev               # back-office : http://localhost:5173
   cd frontend-public && npm run dev        # site de don : http://localhost:5180
   ```
   Connexion : `admin@mla-finance.dev` / `admin1234`. Les ports 5173 et 5180 sont stricts (ils
   correspondent aux origines CORS de `backend/.env`) : libérer le port plutôt que laisser Vite en choisir un autre.

Correspondance fichier ↔ application : voir `scripts/import-excel/import_journal.py` (en-tête) et
`claude/module-grand-livre-spec.md` dans le Projet claude.ai.
