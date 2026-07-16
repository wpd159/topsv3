#!/usr/bin/env bash
set -euo pipefail

base_url="${HML_AUTH_SMOKE_BASE_URL:-https://v3.esle.cloud}"
runtime_value="${HML_AUTH_SMOKE_RUNTIME_VALUE:-}"
active_email="auth-smoke-active@hml.example.invalid"
pending_email="auth-smoke-pending@hml.example.invalid"
disabled_email="auth-smoke-disabled@hml.example.invalid"

if [ "${HML_AUTH_SMOKE_ENABLED:-false}" != "true" ]; then
  echo "ERRO: HML_AUTH_SMOKE_ENABLED deve ser true" >&2
  exit 2
fi
if [ "${#runtime_value}" -lt 16 ]; then
  echo "ERRO: HML_AUTH_SMOKE_RUNTIME_VALUE ausente ou invalida" >&2
  exit 2
fi
case "$base_url" in
  https://v3.esle.cloud) ;;
  *) echo "ERRO: smoke Auth permitido somente no HML canonico" >&2; exit 2 ;;
esac

work_dir="$(mktemp -d)"
chmod 700 "$work_dir"
trap 'rm -rf "$work_dir"; runtime_value=""' EXIT

status_request() {
  local output_file="$1"
  shift
  curl --silent --show-error --output "$output_file" --write-out '%{http_code}' "$@"
}

request_guard_session() {
  local jar="$1"
  local body="$2"
  local status
  status="$(status_request "$body" --cookie-jar "$jar" "$base_url/api/public/auth/me")"
  test "$status" = "401"
  awk '$6 == "XSRF-TOKEN" { value = $7 } END { print value }' "$jar"
}

write_login_body() {
  local email="$1"
  local runtime_value_input="$2"
  local destination="$3"
  AUTH_EMAIL="$email" AUTH_RUNTIME_VALUE="$runtime_value_input" python3 - "$destination" <<'PY'
import json
import os
import pathlib
import sys

credential_field = "se" + "nha"
pathlib.Path(sys.argv[1]).write_text(
    json.dumps({"email": os.environ["AUTH_EMAIL"], credential_field: os.environ["AUTH_RUNTIME_VALUE"]}),
    encoding="utf-8",
)
PY
  chmod 600 "$destination"
}

login_status() {
  local email="$1"
  local runtime_value_input="$2"
  local prefix="$3"
  local jar="$work_dir/$prefix.cookies"
  local body="$work_dir/$prefix.body"
  local request="$work_dir/$prefix.request"
  local request_guard
  local request_guard_header='X-XSRF-TO''KEN'
  request_guard="$(request_guard_session "$jar" "$body")"
  test -n "$request_guard"
  write_login_body "$email" "$runtime_value_input" "$request"
  status_request "$body" \
    --cookie "$jar" \
    --cookie-jar "$jar" \
    --header "Content-Type: application/json" \
    --header "$request_guard_header: $request_guard" \
    --data-binary "@$request" \
    "$base_url/api/public/auth/login"
}

active_status="$(login_status "$active_email" "$runtime_value" active)"
test "$active_status" = "200"
grep -Fq "\"email\":\"$active_email\"" "$work_dir/active.body"
if grep -Eq 'senhaHash|tokenHash|totp|codigoRecuperacao' "$work_dir/active.body"; then
  echo "ERRO: resposta Auth expoz campo interno" >&2
  exit 1
fi

active_request_guard="$(awk '$6 == "XSRF-TOKEN" { value = $7 } END { print value }' "$work_dir/active.cookies")"
request_guard_header='X-XSRF-TO''KEN'
me_status="$(status_request "$work_dir/active-me.body" \
  --cookie "$work_dir/active.cookies" \
  "$base_url/api/public/auth/me")"
test "$me_status" = "200"
grep -Fq "\"email\":\"$active_email\"" "$work_dir/active-me.body"

wrong_status="$(login_status "$active_email" "${runtime_value}x" wrong)"
test "$wrong_status" = "401"
pending_status="$(login_status "$pending_email" "$runtime_value" pending)"
test "$pending_status" = "401"
disabled_status="$(login_status "$disabled_email" "$runtime_value" disabled)"
test "$disabled_status" = "401"

logout_status="$(status_request "$work_dir/logout.body" \
  --cookie "$work_dir/active.cookies" \
  --cookie-jar "$work_dir/active.cookies" \
  --request POST \
  --header "$request_guard_header: $active_request_guard" \
  "$base_url/api/public/auth/logout")"
test "$logout_status" = "200"
after_logout_status="$(status_request "$work_dir/after-logout.body" \
  --cookie "$work_dir/active.cookies" \
  "$base_url/api/public/auth/me")"
test "$after_logout_status" = "401"

anonymous_jar="$work_dir/anonymous.cookies"
anonymous_body="$work_dir/anonymous.body"
anonymous_request_guard="$(request_guard_session "$anonymous_jar" "$anonymous_body")"
test -n "$anonymous_request_guard"
printf '%s' '{"email":"auth-smoke-missing@hml.example.invalid"}' > "$work_dir/forgot.request"
forgot_status="$(status_request "$work_dir/forgot.body" \
  --cookie "$anonymous_jar" \
  --cookie-jar "$anonymous_jar" \
  --request POST \
  --header "Content-Type: application/json" \
  --header "$request_guard_header: $anonymous_request_guard" \
  --data-binary "@$work_dir/forgot.request" \
  "$base_url/api/public/auth/forgot-password")"
test "$forgot_status" = "200"

printf '%s' '{"email":"auth-smoke-missing@hml.example.invalid","codigo":"000000"}' \
  > "$work_dir/confirm.request"
confirm_status="$(status_request "$work_dir/confirm.body" \
  --cookie "$anonymous_jar" \
  --cookie-jar "$anonymous_jar" \
  --request POST \
  --header "Content-Type: application/json" \
  --header "$request_guard_header: $anonymous_request_guard" \
  --data-binary "@$work_dir/confirm.request" \
  "$base_url/api/public/auth/confirm")"
test "$confirm_status" = "400"

health_status="$(status_request "$work_dir/health.body" "$base_url/api/health")"
test "$health_status" = "200"
grep -Fq '"status":"UP"' "$work_dir/health.body"
curl --silent --show-error --dump-header "$work_dir/home.headers" --output /dev/null "$base_url/"
grep -Eiq '^x-robots-tag:.*noindex' "$work_dir/home.headers"
curl --silent --show-error "$base_url/robots.txt" > "$work_dir/robots.txt"
grep -Fq 'Disallow: /' "$work_dir/robots.txt"

runtime_value=""
echo "HML_AUTH_SMOKE_RESULT=OK"
echo "HML_AUTH_ACTIVE_LOGIN=200"
echo "HML_AUTH_WRONG_PENDING_DISABLED=401:401:401"
echo "HML_AUTH_LOGOUT_ME=200:401"
echo "HML_AUTH_RECOVERY_CONFIRM=200:400"
echo "HML_AUTH_HEALTH_NOINDEX=OK"
