# mla-finance-backend

API backend du système de gestion financière de la chorale MLA (ICC Yaoundé). Spring Boot 3 / Java 21, PostgreSQL en production (H2 en développement), migrations Liquibase, sécurité JWT (OAuth2 Resource Server).

## Prérequis

- Java 21
- Maven 3.9+ (`brew install maven`, ou l'installation bundlée de votre IDE — pas de wrapper `mvnw` fourni pour l'instant)

## Démarrer en local

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

L'API démarre sur `http://localhost:8080`, avec une base H2 en mémoire (auto-migrée par Liquibase) et la sécurité désactivée pour faciliter le développement (voir `DevSecurityConfig` — jamais utilisé en production).

- Statut : `GET /api/v1/status`
- Santé : `GET /actuator/health`
- Documentation API : `http://localhost:8080/swagger-ui.html`
- Console H2 (dev uniquement) : `http://localhost:8080/h2-console` (JDBC URL : `jdbc:h2:mem:mla_finance`)

## Build

```bash
mvn clean verify
```

## Profils

- `dev` : H2 en mémoire, sécurité ouverte (aucun fournisseur d'identité requis).
- `prod` : PostgreSQL via `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD`, JWT obligatoire via `OAUTH2_ISSUER_URI`.

## Modèle de données (fondations)

Premier lot d'entités, aligné sur le Document Technique validé : `Role`/`Permission`, `User` (invitation par email, pas de mot de passe saisi côté admin), `Donor`, `PaymentChannel` (canaux dynamiques), `DonationCategory`, `Project` (solde d'ouverture, séries récurrentes), `Contribution` (rattachement Project **XOR** DonationCategory, contrainte appliquée aussi en base ; `periodMonth` distinct de `transactionDate`).

Les modules suivants (retraits, transferts, moyens de paiement PawaPay, médias, audit, notifications) seront ajoutés au fil des prochaines itérations, module par module, en s'appuyant sur les règles de gestion RG-01 à RG-20 du Cahier des Charges.
