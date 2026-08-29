#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
helper="${root}/scripts/deploy/executar-backfill-previews-production.sh"
workflow="${root}/.github/workflows/deploy-production.yml"
activator="${root}/scripts/deploy/ativar-release-atomica-production.sh"
atomic_test="${root}/scripts/deploy/testar-release-atomica-production.sh"
service="${root}/backend/src/main/java/br/com/topsdojob/v3/application/operacional/midia/MidiaRestritaRegularizacaoService.java"
repository="${root}/backend/src/main/java/br/com/topsdojob/v3/persistence/repository/PreviewRestritoBackfillJdbcRepository.java"
bootstrap="${root}/backend/src/main/java/br/com/topsdojob/v3/application/operacional/midia/backfill/RestrictedMediaPreviewBackfillBootstrap.java"
configuration="${root}/backend/src/main/java/br/com/topsdojob/v3/application/operacional/midia/backfill/RestrictedMediaPreviewBackfillConfiguration.java"
inventory="${root}/backend/src/main/java/br/com/topsdojob/v3/infrastructure/storage/ObjectStorageInventory.java"
temporary="$(mktemp -d)"

cleanup() {
  rm -rf -- "${temporary}"
}
trap cleanup EXIT

fail() {
  printf 'ERRO: %s\n' "$*" >&2
  exit 1
}

line_of() {
  local file="$1"
  local pattern="$2"
  grep -n -m1 -F "${pattern}" "${file}" | cut -d: -f1
}

assert_before() {
  local first="$1"
  local second="$2"
  [ -n "${first}" ] && [ -n "${second}" ] && [ "${first}" -lt "${second}" ]
}

mock_bin="${temporary}/bin"
release_dir="${temporary}/release"
report_dir="${temporary}/evidence"
env_file="${temporary}/production.env"
mkdir -p "${mock_bin}" "${release_dir}/deploy/production" "${report_dir}"
: > "${release_dir}/deploy/production/docker-compose.yml"
: > "${env_file}"

cat > "${mock_bin}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail

if [ "$1" = inspect ]; then
  exit 1
fi
[ "$1" = compose ] || exit 1
printf '%s\n' "$*" >> "${BACKFILL_FIXTURE_EVENT_LOG}"

mode=UNKNOWN
for argument in "$@"; do
  case "${argument}" in
    --app.restricted-media-preview-reconciliation.mode=*)
      mode="${argument#*=}"
      ;;
  esac
done

if [ "${mode}" = APPLY ] && [ "${BACKFILL_FIXTURE_FAIL_APPLY:-false}" = true ]; then
  printf 'fixture diagnostic_url=https://private.invalid/object\n' >&2
  exit 42
fi

updated=0
unchanged=0
batches=0
db_available=-1
db_unknown=-1
db_pending=-1
db_inconsistent=-1
if [ "${mode}" = APPLY ]; then
  updated="${BACKFILL_FIXTURE_APPLY_UPDATED:-1344}"
  unchanged=$((1344 - updated))
  batches=7
  db_available=1344
  db_unknown=0
  db_pending=0
  db_inconsistent=0
elif [ "${mode}" = VALIDATE ]; then
  unchanged=1344
  db_available=1344
  db_unknown=0
  db_pending=0
  db_inconsistent=0
fi

printf 'RESTRICTED_MEDIA_PREVIEW_RECONCILIATION_RESULT mode=%s links=1344 eligible=1344 r2_available=1344 missing=0 inconsistent=0 unproven=0 r2_pages=2 db_updated=%s db_unchanged=%s batches=%s db_available=%s db_unknown=%s db_pending=%s db_inconsistent=%s\n' \
  "${mode}" "${updated}" "${unchanged}" "${batches}" "${db_available}" \
  "${db_unknown}" "${db_pending}" "${db_inconsistent}"
MOCK
chmod 0700 "${mock_bin}/docker"

cat > "${mock_bin}/install" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
target="${*: -1}"
mkdir -p "${target}"
MOCK
chmod 0700 "${mock_bin}/install"

run_fixture() {
  local name="$1"
  local mode="$2"
  local updated="${3:-1344}"
  local fail_apply="${4:-false}"
  local output="${temporary}/${name}.output"
  local events="${temporary}/${name}.events"
  : > "${events}"
  set +e
  PATH="${mock_bin}:${PATH}" \
    BACKFILL_FIXTURE_EVENT_LOG="${events}" \
    BACKFILL_FIXTURE_APPLY_UPDATED="${updated}" \
    BACKFILL_FIXTURE_FAIL_APPLY="${fail_apply}" \
    bash "${helper}" "${mode}" initial \
      aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
      "${release_dir}" "${env_file}" \
      topsv3-production topsv3-production topsv3-production-net \
      "${report_dir}" > "${output}" 2>&1
  local rc=$?
  set -e
  if [ "${fail_apply}" = true ]; then
    [ "${rc}" -ne 0 ] || fail "${name} deveria falhar"
  else
    [ "${rc}" -eq 0 ] || {
      cat "${output}" >&2
      fail "${name} deveria passar"
    }
  fi
  grep -Fq -- '--app.bootstrap=restricted-media-preview-backfill' "${events}" ||
    fail "${name} nao selecionou o bootstrap dedicado"
}

run_fixture plan PLAN
grep -Fq 'mode=PLAN status=OK eligible=1344' "${temporary}/plan.output"
grep -Fq -- '--app.restricted-media-preview-reconciliation.apply-confirmed=false' "${temporary}/plan.events"

run_fixture apply APPLY 1344
grep -Fq 'mode=APPLY updated=1344 unchanged=0 batches=7 available=1344 unknown=0 pending=0 inconsistent=0' "${temporary}/apply.output"
grep -Fq -- '--app.restricted-media-preview-reconciliation.apply-confirmed=true' "${temporary}/apply.events"

run_fixture apply_idempotent APPLY 0
grep -Fq 'mode=APPLY updated=0 unchanged=1344 batches=7 available=1344 unknown=0 pending=0 inconsistent=0' "${temporary}/apply_idempotent.output"

run_fixture validate VALIDATE
grep -Fq 'mode=VALIDATE updated=0 unchanged=1344 batches=0 available=1344 unknown=0 pending=0 inconsistent=0' "${temporary}/validate.output"

run_fixture apply_failure APPLY 1344 true
grep -Fq 'mode=APPLY status=FAIL exit_code=42' "${temporary}/apply_failure.output"
! grep -Fq 'private.invalid' "${temporary}/apply_failure.output"

grep -Fq 'StoredObjectPage page = storage.list(' "${service}"
grep -Fq 'ObjectProvider<ObjectStorageInventory>' "${service}"
if grep -Eq 'storage\.(exists|get|put|copy|delete)\(' "${service}"; then
  fail "backfill usa operacao individual ou mutavel no ObjectStorage"
fi
grep -Fq 'public interface ObjectStorageInventory' "${inventory}"
grep -Fq 'StoredObjectPage list(' "${inventory}"
if grep -Eq '(exists|get|put|copy|delete)\(' "${inventory}"; then
  fail "inventario do backfill expoe operacao individual ou mutavel"
fi
grep -Fq 'WebApplicationType.NONE' "${bootstrap}"
grep -Fq 'RestrictedMediaPreviewBackfillConfiguration.class' "${bootstrap}"
grep -Fq '@ImportAutoConfiguration' "${configuration}"
grep -Fq '@ConditionalOnProperty(' "${configuration}"
grep -Fq 'havingValue = "restricted-media-preview-backfill"' "${configuration}"
grep -Fq 'ExcludedPreviewBackfillRepositoryFilter.class' "${configuration}"
grep -Fq 'MidiaRestritaRegularizacaoService.class' "${configuration}"
grep -Fq 'R2PreviewInventoryConfiguration.class' "${configuration}"
if grep -Eq 'SecurityConfig|AuthenticationManager|HealthController|EnableScheduling' "${configuration}"; then
  fail "contexto do backfill importa pilha web, seguranca ou scheduler"
fi
grep -Fq 'UPDATE arquivo_midia' "${repository}"
grep -Fq "preview_restrito_status = 'DISPONIVEL'" "${repository}"
! grep -Eq 'docker (cp|save)|r2 (put|copy|delete)|aws s3 (cp|mv|rm)' "${helper}"
! grep -Fq 'docker rm -f' "${helper}"

backup_line="$(line_of "${workflow}" 'bash "${backup_producer}"')"
flyway_line="$(line_of "${workflow}" '"${compose[@]}" run --rm -T --no-deps flyway migrate')"
initial_line="$(line_of "${workflow}" 'for preview_backfill_mode in PLAN APPLY VALIDATE; do')"
candidate_line="$(line_of "${workflow}" 'bash "${atomic_activator}"')"
assert_before "${backup_line}" "${flyway_line}"
assert_before "${flyway_line}" "${initial_line}"
assert_before "${initial_line}" "${candidate_line}"

delta_apply_line="$(line_of "${activator}" 'stage_or_abort run_preview_backfill APPLY delta')"
delta_validate_line="$(line_of "${activator}" 'stage_or_abort run_preview_backfill VALIDATE delta')"
candidate_gate_last_line="$(grep -nF 'stage_or_abort verify_candidate' "${activator}" | tail -1 | cut -d: -f1)"
switch_line="$(line_of "${activator}" 'stage_or_abort switch_gateway')"
drain_line="$(line_of "${activator}" 'stage_or_abort drain_window')"
final_apply_line="$(line_of "${activator}" 'stage_or_abort run_preview_backfill APPLY final')"
final_validate_line="$(line_of "${activator}" 'stage_or_abort run_preview_backfill VALIDATE final')"
assert_before "${delta_apply_line}" "${delta_validate_line}"
assert_before "${candidate_gate_last_line}" "${delta_apply_line}"
assert_before "${delta_validate_line}" "${switch_line}"
assert_before "${switch_line}" "${drain_line}"
assert_before "${drain_line}" "${final_apply_line}"
assert_before "${final_apply_line}" "${final_validate_line}"

grep -Fq 'run_scenario backfill-failure' "${atomic_test}"
grep -Fq 'PREVIEW_BACKFILL_DELTA_VALIDATE' "${atomic_test}"
grep -Fq 'PREVIEW_BACKFILL_FINAL_VALIDATE' "${atomic_test}"

printf 'PREVIEW_BACKFILL_DEPLOY_TESTS=PASS\n'
