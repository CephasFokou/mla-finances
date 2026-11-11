# mla-finance-backend

API backend du système de gestion financière de la chorale MLA (ICC Yaoundé). Spring Boot 3 / Java 21, PostgreSQL en production (H2 en développement), migrations Liquibase, authentification JWT auto-émise (RG-18 — voir `security.SecurityConfig` et `service.JwtService`, pas de fournisseur d'identité externe).

## Prérequis

- Java 21
- Maven 3.9+ (`brew install maven`, ou l'installation bundlée de votre IDE — pas de wrapper `mvnw` fourni pour l'instant)

## Démarrer en local

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

L'API démarre sur `http://localhost:8080`, avec une base H2 en mémoire (auto-migrée par Liquibase). L'authentification réelle est active même en profil `dev` (Cephas a choisi de ne pas garder de mode "tout ouvert" local, voir `claude/module-authentification-spec.md`) : un compte SUPER_ADMIN est créé automatiquement au démarrage (`security.DevAdminSeeder`) pour pouvoir se connecter tout de suite —

- Email : `admin@mla-finance.dev`
- Mot de passe : `admin1234`

- Statut : `GET /api/v1/status`
- Santé : `GET /actuator/health`
- Documentation API : `http://localhost:8080/swagger-ui.html`
- Console H2 (dev uniquement) : `http://localhost:8080/h2-console` (JDBC URL : `jdbc:h2:mem:mla_finance`)

### Logs

En profil `dev`, les logs applicatifs, Spring Security et CORS sont en `DEBUG` par défaut (utile
pour diagnostiquer un rejet CORS, un 401, un jeton invalide...). Paramétrable sans recompiler, dans
n'importe quel profil :

```bash
LOG_LEVEL_APP=DEBUG LOG_LEVEL_SECURITY=DEBUG LOG_LEVEL_CORS=DEBUG mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### CORS en dev

`app.cors.allowed-origins` accepte des **patterns** (pas seulement des origines exactes) — la
valeur par défaut en dev est `http://localhost:*`, pour ne pas casser le CORS quand Vite choisit un
port différent de 5173 (déjà occupé -> 5174, etc.).

### PostgreSQL local (profil `local`)

À combiner avec `dev` : `mvn spring-boot:run -Dspring-boot.run.profiles=dev,local`. Base par défaut
`jdbc:postgresql://localhost:5432/mla_finance`, utilisateur système, surchargeable par `DATABASE_URL`
ou `PGHOST`/`PGPORT`/`PGDATABASE`/`PGUSER`/`PGPASSWORD`. Pour (ré)initialiser cette base avec les
données du fichier de suivi : `../scripts/reset-local-db.sh` (voir le README racine).

`--app.migrate-only=true` applique les migrations puis arrête l'application (utilisé par ce script).

## Build

```bash
mvn clean verify
```

## Profils

- `dev` : H2 en mémoire, secret JWT fixe versionné, compte SUPER_ADMIN pré-créé (voir ci-dessus). L'envoi d'email (invitations, réinitialisation de mot de passe) est un no-op journalisé tant que `RESEND_API_KEY` n'est pas exporté localement.
- `prod` : PostgreSQL via `DATABASE_URL` (format JDBC `jdbc:postgresql://hôte:port/base`) / `DATABASE_USERNAME` / `DATABASE_PASSWORD`. Variables d'environnement :
  - **Obligatoires** : `JWT_SECRET` (secret HMAC256, ≥ 32 caractères aléatoires), `FRONTEND_BASE_URL` (URL du back-office, pour les liens d'activation/réinitialisation), `FRONTEND_ORIGIN` (origine CORS du back-office).
  - `PUBLIC_FRONTEND_ORIGIN` — origine CORS du site public de don (`frontend-public/`), si déployé.
  - `RESEND_API_KEY` / `EMAIL_FROM` — facultatives : sans clé, aucun email n'est envoyé (avertissement journalisé). Resend n'envoie qu'à l'adresse du compte tant qu'aucun domaine expéditeur n'est vérifié.
  - `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` (≥ 12 caractères) / `BOOTSTRAP_ADMIN_NAME` — amorçage du premier SUPER_ADMIN (`security.BootstrapAdminRunner`) : n'agit que si la table `users` est vide. **À retirer après la première connexion**, puis changer le mot de passe.
  - `MEDIA_STORAGE_DIR` (défaut `/app/data/media-storage`) — répertoire des médias ; sur Railway, y monter un volume persistant (et `RAILWAY_RUN_UID=0` pour que le conteneur puisse y écrire), sinon les fichiers sont perdus à chaque redéploiement. `MEDIA_STORAGE=local` est la seule valeur gérée pour l'instant (un stockage objet R2/S3 pourra être ajouté derrière `MediaStorageService`).
  - `LOG_LEVEL_APP` / `LOG_LEVEL_SECURITY` / `LOG_LEVEL_CORS` — niveaux de log (voir plus haut).

## Modèle de données (fondations)

Premier lot d'entités, aligné sur le Document Technique validé : `Role`/`Permission`, `User` (invitation par email, mot de passe choisi par l'invité à l'activation — voir `service.AuthService`), `Donor`, `PaymentChannel` (canaux dynamiques), `DonationCategory`, `Project` (solde d'ouverture, séries récurrentes), `Contribution` (rattachement Project **XOR** DonationCategory, contrainte appliquée aussi en base ; `periodMonth` distinct de `transactionDate`).

Les modules suivants (transferts, moyens de paiement PawaPay, notifications) seront ajoutés au fil des prochaines itérations, module par module, en s'appuyant sur les règles de gestion RG-01 à RG-20 du Cahier des Charges.
