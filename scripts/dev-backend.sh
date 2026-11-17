#!/usr/bin/env bash
# Lance le backend en local avec les variables de backend/.env et les profils Spring voulus.
#
# Usage : ./scripts/dev-backend.sh [profils]      (défaut : dev,local → PostgreSQL local)
#         ./scripts/dev-backend.sh dev            (H2 en mémoire, base vide)
#
# backend/.env est aussi lu directement par Spring (spring.config.import) ; ce script l'exporte en
# plus dans l'environnement pour que Maven, Liquibase et les scripts annexes voient les mêmes
# valeurs, et il force la variable JWT_SECRET à être présente avant de démarrer.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILES="${1:-dev,local}"

if [[ ! -f "$ROOT/backend/.env" ]]; then
  echo "backend/.env introuvable : cp backend/.env.example backend/.env puis complétez-le." >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
source "$ROOT/backend/.env"
set +a

if [[ -z "${JWT_SECRET:-}" || "$JWT_SECRET" == remplacer-par-* ]]; then
  echo "JWT_SECRET n'est pas défini dans backend/.env (openssl rand -base64 64)." >&2
  exit 1
fi
if [[ "${PAWAPAY_ENABLED:-false}" == "true" && -z "${PAWAPAY_API_TOKEN:-}" ]]; then
  echo "PAWAPAY_ENABLED=true mais PAWAPAY_API_TOKEN est vide : les dons Mobile Money échoueront (401 PawaPay)." >&2
fi

cd "$ROOT/backend"
exec mvn spring-boot:run -Dspring-boot.run.profiles="$PROFILES"
