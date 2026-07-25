#!/bin/sh
set -eu

mode="admin"
email="admin.stories.hml@example.invalid"
if [ "${1:-}" = "--fixture-owner" ]; then
  mode="fixture-owner"
elif [ -n "${1:-}" ]; then
  email="$1"
fi

if [ "$mode" = "admin" ]; then
  case "$email" in
    *@example.invalid) ;;
    *) echo "ERRO: use somente e-mail ficticio @example.invalid" >&2; exit 2 ;;
  esac
fi

repo_dir="${TOPSV3_HML_REPO_DIR:-/opt/topsv3/app/current}"
env_file="${TOPSV3_HML_ENV_FILE:-/opt/topsv3/secrets/hml.env}"
compose_file="$repo_dir/deploy/hml/docker-compose.yml"

if [ ! -f "$env_file" ] || [ ! -f "$compose_file" ]; then
  echo "ERRO: contrato HML indisponivel" >&2
  exit 2
fi

if [ -t 0 ]; then
  printf "Senha temporaria de homologacao: " >&2
  stty -echo
  IFS= read -r runtime_input
  stty echo
  printf "\n" >&2
else
  IFS= read -r runtime_input
fi

if [ "${#runtime_input}" -lt 16 ]; then
  runtime_input=""
  echo "ERRO: credencial de runtime muito curta" >&2
  exit 2
fi

cd "$repo_dir"
if [ "$mode" = "fixture-owner" ]; then
  printf '%s\n' "$runtime_input" | docker compose \
    --env-file "$env_file" \
    -f "$compose_file" \
    run --rm -T \
    backend \
    --server.port=0 \
    --app.fixture.owner-credential.enabled=true
else
  printf '%s\n' "$runtime_input" | docker compose \
    --env-file "$env_file" \
    -f "$compose_file" \
    run --rm -T \
    -e FIXTURE_ADMIN_PROVISION_EMAIL="$email" \
    backend \
    --server.port=0 \
    --app.fixture.admin-provision.enabled=true
fi

runtime_input=""
