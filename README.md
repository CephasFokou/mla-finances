# MLA Finance Management System

Système de gestion financière pour la chorale MLA (ICC Yaoundé) : suivi des contributions, projets/enveloppes, retraits, rapports et médias.

Monorepo :
- `backend/` — API Spring Boot 3 / Java 21 / PostgreSQL / Liquibase (voir `backend/README.md`), sert aussi bien le back-office authentifié que les endpoints publics (`/api/v1/public/**`) consommés par le site de don.
- `frontend/` — back-office authentifié (React / TypeScript).
- `frontend-public/` — site public de don (React / TypeScript), volontairement détaché du back-office : projet, build et déploiement indépendants, pour ne jamais exposer le code de l'application authentifiée sur une page ouverte à tout le monde. Voir `claude/module-front-office-public-spec.md` dans le Projet claude.ai pour le détail de cette séparation.

Références validées : Cahier des Charges v1.1 et Document Technique v1.2 (conservés dans le Projet claude.ai "MLA - FINANCES").
