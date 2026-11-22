# mla-finance-backend

API backend du système de gestion financière de la chorale MLA (ICC Yaoundé). Spring Boot 3 / Java 21, PostgreSQL en production (H2 en développement), migrations Liquibase, authentification JWT auto-émise (RG-18 — voir `security.SecurityConfig` et `service.JwtService`, pas de fournisseur d'identité externe).

## Prérequis

- Java 21
- Maven 3.9+ (`brew install maven`, ou l'installation bundlée de votre IDE — pas de wrapper `mvnw` fourni pour l'instant)

## Variables locales : `backend/.env`

```bash
cp backend/.env.example backend/.env   # puis compléter (voir les commentaires du fichier)
```

Spring Boot lit `backend/.env` au démarrage (`spring.config.import` dans `application.yml`, aucune
dépendance) ; les vraies variables d'environnement gardent la priorité. Contenu : `FRONTEND_ORIGIN` /
`PUBLIC_FRONTEND_ORIGIN` (CORS, ports 5173 et 5180 fixés en `strictPort` côté Vite), `FRONTEND_BASE_URL`,
`JWT_SECRET` (`openssl rand -base64 64`), `RESEND_API_KEY` / `EMAIL_FROM`, `DATABASE_*` (profil `local`),
`PAWAPAY_*` (voir plus bas) et `RATE_LIMIT_TRUST_PROXY`. Les profils Spring ne peuvent pas y être fixés :
utiliser `./scripts/dev-backend.sh [profils]` (défaut `dev,local`), qui exporte aussi le fichier dans
l'environnement et vérifie que `JWT_SECRET` est renseigné.

## Démarrer en local

```bash
../scripts/dev-backend.sh            # dev,local : PostgreSQL local + variables de backend/.env
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # H2 en mémoire, base vide
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

## Paiement Mobile Money : PawaPay (`PAWAPAY_*`)

Intégration de l'API PawaPay v2 (dépôts Orange Money / MTN MoMo), désactivée par défaut. Avec
`PAWAPAY_ENABLED=true` et `PAWAPAY_API_TOKEN` (dashboard sandbox → Developers → API tokens), les
canaux `ORANGE_MONEY` et `MTN_MOMO` du site de don passent « en ligne » : le donateur saisit son numéro,
reçoit une demande de paiement sur son téléphone, et le don est confirmé **automatiquement** (Contribution
créée) dès que PawaPay rapporte `COMPLETED`. Les autres canaux (carte, espèces, virement) restent en mode
manuel. Détail des choix et des mesures de sécurité : `claude/module-paiement-pawapay-spec.md` (Projet
claude.ai).

- Sandbox : `PAWAPAY_BASE_URL=https://api.sandbox.pawapay.io` ; numéros de test qui aboutissent :
  `237653456789` (MTN), `237693456789` (Orange) ; échecs simulés : `...019` plafond, `...029` compte
  inconnu, `...039` refus du client, `...049` solde insuffisant (Orange), `...069` erreur.
- Callback : URL à déclarer dans le dashboard PawaPay :
  `<URL publique de l'API>/api/v1/webhooks/pawapay/<PAWAPAY_CALLBACK_SECRET>`. Le contenu du callback n'est
  jamais cru : il déclenche une relecture du statut chez PawaPay. En local (poste injoignable), le job de
  réconciliation relit les dons en cours toutes les 15 s — le flux complet fonctionne donc sans tunnel.
- Bornes et anti-abus : `PAWAPAY_MIN_AMOUNT` / `PAWAPAY_MAX_AMOUNT` (100 / 500 000 FCFA, entiers),
  3 demandes maximum par numéro et par 10 minutes, limitation de débit par IP (`app.rate-limit.*` :
  60 req/min sur `/api/v1/public/**`, 20 sur `/api/v1/auth/**`, 120 sur `/api/v1/webhooks/**` ;
  `RATE_LIMIT_TRUST_PROXY=true` derrière Railway pour lire `X-Forwarded-For`).

### Décaissement des retraits par Mobile Money (payouts wallet-ledger)

Avec `PAWAPAY_ENABLED=true`, un retrait **validé** peut être décaissé depuis sa fiche (« Décaisser par
Mobile Money ») vers un numéro MLA de la liste fermée `app.payment.payout-recipients` (`application.yml` :
libellé, MSISDN `237…`, opérateur `ORANGE_CMR` / `MTN_MOMO_CMR`), jamais vers un numéro saisi. Cette
liste est le miroir de la liste blanche du projet MLA dans wallet-ledger, qui reste l'autorité.

- Envoi : `POST /api/v1/withdrawals/{id}/payout` `{"recipientKey"}` (`MANAGE_WITHDRAWALS`) → `POST /v2/payouts`
  (montant du retrait, XAF, `clientReferenceId = MLA-WD-<référence ou id>`). Le `payoutId` est enregistré
  avant l'appel ; un seul payout actif par retrait.
- Statut final : webhook wallet-ledger sur la même URL que les dons (`X-Ledger-Event: payout`, seul le
  `payoutId` est lu) + job de polling + bouton « Vérifier le statut » ; tous relisent `GET /v2/payouts/{id}`.
  `COMPLETED` ⇒ retrait **PROCESSED** automatiquement (canal Orange Money / MTN MoMo) ; `FAILED` ⇒ le retrait
  reste validé, nouvelle tentative possible.
- Au-dessus du seuil wallet-ledger (50 000 FCFA) : `PENDING_APPROVAL` jusqu'à validation dans la Trésorerie.
- Pendant un payout en cours, annulation et « Marquer comme décaissé » sont refusés (409).
- Solde affiché sur la page Retraits : `GET /api/v1/withdrawals/mobile-money-balance` → wallet-ledger
  `GET /api/v1/balance` (route wallet-ledger, pas un chemin PawaPay).
- Détail : `docs/module-retraits-spec.md`.

### Séparation des rôles et bon de décaissement

- Personne ne valide ni ne rejette un retrait qu'il a saisi, sauf `SUPER_ADMIN` (403 sinon) ; l'auto-validation
  d'un super administrateur est tracée (`WITHDRAWAL_SELF_APPROVED`), signalée par un badge « Auto-validé » et
  filtrable (`GET /api/v1/withdrawals?selfApproved=true`).
- Bon de décaissement PDF d'un retrait décaissé : `GET /api/v1/withdrawals/{id}/voucher.pdf`, produit à la
  demande (openhtmltopdf, polices embarquées, rien sur disque), avec la référence opérateur pour un payout
  Mobile Money.

### Frais Mobile Money (automatiques)

Chaque jour à 07:00 UTC, les frais PawaPay du projet MLA (lignes `FEE` de `GET /api/v1/movements` chez
wallet-ledger) sont enregistrés comme dépenses « Frais Mobile Money » sur l'enveloppe de l'opération qui les a
causés (Caisse MLA à défaut), une par jour et par enveloppe, sans doublon. Variables : `MLA_FEES_SYNC_ENABLED`
(vrai par défaut avec PawaPay), `MLA_FEES_SYNC_CRON`, `MLA_LEDGER_START` (début du contrôle d'écart si
wallet-ledger ne renvoie pas de date d'ouverture). L'encart de solde de la page Retraits affiche l'écart.

## Relances des contributions (lot 1 : email)

Écran « Relances » (permission `SEND_REMINDERS`, par défaut SUPER_ADMIN et TRESORIER) : membres actifs du mois,
contribution mensuelle réglée ou non, relance par email (Resend) individuelle avec aperçu, ou groupée. Règles :
mois en cours seulement, à partir du 10, 3 jours d'attente sans réponse, 3 relances maximum par mois, envois
entre 8 h et 20 h (heure de Douala ; hors plage, la relance est programmée pour 8 h). Une contribution mensuelle
enregistrée clôt le dossier en « Réglé ». Un envoi dont aucun canal n'aboutit (ex. `RESEND_API_KEY` vide) est
tracé mais ne compte pas. Réglages dans Administration → Relances. Variables : `PUBLIC_SITE_URL` (lien
« Contribuer maintenant », défaut `PUBLIC_FRONTEND_ORIGIN` puis `https://don-mla.itc-services.xyz`),
`REMINDER_QUEUE_POLL_MS`. WhatsApp (Kapso), réponses des membres, collectes relançables, suivi par référence et
espace membre : lots suivants.

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
  - `PAWAPAY_ENABLED`, `PAWAPAY_BASE_URL` (`https://api.pawapay.io` en production, jeton différent du
    sandbox), `PAWAPAY_API_TOKEN`, `PAWAPAY_CALLBACK_SECRET` — voir la section PawaPay.
  - `RATE_LIMIT_TRUST_PROXY=true` — l'API est derrière le proxy Railway.

## Modèle de données (fondations)

Premier lot d'entités, aligné sur le Document Technique validé : `Role`/`Permission`, `User` (invitation par email, mot de passe choisi par l'invité à l'activation — voir `service.AuthService`), `Donor`, `PaymentChannel` (canaux dynamiques), `DonationCategory`, `Project` (solde d'ouverture, séries récurrentes), `Contribution` (rattachement Project **XOR** DonationCategory, contrainte appliquée aussi en base ; `periodMonth` distinct de `transactionDate`).

Paiement en ligne : `PaymentTransaction` (intention de don, jamais une Contribution avant réception des fonds), `payment/PaymentProvider` avec `ManualPaymentProvider` et `payment/pawapay/PawaPayProvider`, réconciliation par callback + polling (RG-20).
