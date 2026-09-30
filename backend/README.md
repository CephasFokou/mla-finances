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

## Build

```bash
mvn clean verify
```

## Profils

- `dev` : H2 en mémoire, secret JWT fixe versionné, compte SUPER_ADMIN pré-créé (voir ci-dessus). L'envoi d'email (invitations, réinitialisation de mot de passe) est un no-op journalisé tant que `RESEND_API_KEY` n'est pas exporté localement.
- `prod` : PostgreSQL via `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD`. Variables d'environnement **obligatoires** pour l'authentification et l'email :
  - `JWT_SECRET` — secret de signature HMAC256 (≥ 32 caractères aléatoires).
  - `RESEND_API_KEY` — clé API Resend (même fournisseur que le projet PÉNIEL de Cephas).
  - `EMAIL_FROM` — adresse d'expédition (ex. `MLA Finance <no-reply@votre-domaine.org>`).
  - `FRONTEND_BASE_URL` — URL publique du frontend, utilisée dans les liens d'activation/réinitialisation envoyés par email.
  - `FRONTEND_ORIGIN` — origine autorisée en CORS (doit correspondre au domaine du frontend déployé).

## Modèle de données (fondations)

Premier lot d'entités, aligné sur le Document Technique validé : `Role`/`Permission`, `User` (invitation par email, mot de passe choisi par l'invité à l'activation — voir `service.AuthService`), `Donor`, `PaymentChannel` (canaux dynamiques), `DonationCategory`, `Project` (solde d'ouverture, séries récurrentes), `Contribution` (rattachement Project **XOR** DonationCategory, contrainte appliquée aussi en base ; `periodMonth` distinct de `transactionDate`).

Les modules suivants (transferts, moyens de paiement PawaPay, notifications) seront ajoutés au fil des prochaines itérations, module par module, en s'appuyant sur les règles de gestion RG-01 à RG-20 du Cahier des Charges.
