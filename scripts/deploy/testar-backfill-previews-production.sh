#!/usr/bin/env bash
set -euo pipefail

# Shell orchestration proof only. Docker and the Python observer are synthetic;
# the helper, canonical tree, _op_lock, journals checks and cleanup flow are real.
# Run in an isolated Linux process with /opt (or /opt/topsv3) on a dedicated
# tmpfs, source read-only and no Docker socket. Never override the production
# helper's canonical-root predicate. No real Docker/Spring/DB is exercised.
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# CI prepares the image explicitly; local runs may select an already present
# image by physical ID. The child has no daemon/socket or external network.
fixture_is_isolated() {
  [[ "$(uname -s)" == Linux && "$root" == /source ]] || return 1
  [[ ! -e /var/run/docker.sock && ! -e /run/docker.sock ]] || return 1
  [[ "$(ls /sys/class/net)" == lo ]] || return 1
  awk '
    $5 == "/" && $6 ~ /(^|,)ro(,|$)/ { readonly_root=1 }
    $5 == "/source" && $6 ~ /(^|,)ro(,|$)/ { readonly_source=1 }
    $5 == "/tmp" || $5 == "/opt/topsv3" {
      for (i=6;i<=NF;i++) if ($i == "-" && $(i+1) == "tmpfs") {
        if ($5 == "/tmp" && $6 !~ /(^|,)noexec(,|$)/) executable_temp=1
        if ($5 == "/opt/topsv3" && $6 ~ /(^|,)noexec(,|$)/) isolated_opt=1
      }
    }
    END { exit !(readonly_root && readonly_source && executable_temp && isolated_opt) }
  ' /proc/self/mountinfo
}
launch_isolated_fixture() (
  set -euo pipefail
  fixture_image="${TOPSV3_BACKFILL_FIXTURE_IMAGE_ID:-}"
  [[ "$fixture_image" =~ ^sha256:[a-f0-9]{64}$ ]] || {
    printf 'ERRO: prepare uma imagem local e informe TOPSV3_BACKFILL_FIXTURE_IMAGE_ID fisico\n' >&2; exit 1;
  }
  command -v docker >/dev/null
  command -v timeout >/dev/null
  launcher_docker() {
    local duration="$1"; shift
    MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' timeout --signal=TERM --kill-after=5s "$duration" docker "$@"
  }
  [[ "$(launcher_docker 30s image inspect --format '{{.Id}}' "$fixture_image")" == "$fixture_image" ]]
  launcher_owner="$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"
  [[ "$launcher_owner" =~ ^[a-f0-9]{32}$ ]]
  launcher_name="tops-preview-shell-${launcher_owner}"
  launcher_label="topsv3.preview.fixture=${launcher_owner}"
  launcher_id= launcher_uncertain=0
  launcher_evidence="$(mktemp -d)"
  launcher_source="$root"
  case "$(uname -s)" in MINGW*|MSYS*) launcher_source="$(cygpath -m "$root")" ;; esac
  launcher_cleanup() {
    local original="$1" cleanup_rc=0 inventory owned inspected actual_id actual_owner running rc
    trap - EXIT
    set +e
    inventory="$(launcher_docker 30s ps --all --quiet --no-trunc)"; rc=$?
    if [[ "$rc" -ne 0 ]]; then cleanup_rc=1
    else
      owned="$(launcher_docker 30s ps --all --quiet --no-trunc --filter "label=$launcher_label")"; rc=$?
      if [[ "$rc" -ne 0 || ( -n "$owned" && ! "$owned" =~ ^[a-f0-9]{64}$ ) ]]; then cleanup_rc=1
      elif [[ -n "$launcher_id" && ( ( -n "$owned" && "$owned" != "$launcher_id" ) || ( "$inventory" == *"$launcher_id"* && "$owned" != "$launcher_id" ) ) ]]; then cleanup_rc=1
      elif [[ -n "$owned" ]]; then
        inspected="$(launcher_docker 30s inspect "$owned" --format '{{.Id}} {{index .Config.Labels "topsv3.preview.fixture"}} {{.State.Running}}')"; rc=$?
        read -r actual_id actual_owner running <<<"$inspected"
        if [[ "$rc" -ne 0 || "$actual_id" != "$owned" || "$actual_owner" != "$launcher_owner" || ! "$running" =~ ^(true|false)$ ]]; then cleanup_rc=1
        else
          if [[ "$running" == true ]]; then launcher_docker 30s stop --time 10 "$actual_id" >"$launcher_evidence/stop.log" 2>&1 || cleanup_rc=1; fi
          if [[ "$cleanup_rc" -eq 0 ]]; then launcher_docker 30s rm "$actual_id" >"$launcher_evidence/remove.log" 2>&1 || cleanup_rc=1; fi
          inventory="$(launcher_docker 30s ps --all --quiet --no-trunc)"; rc=$?
          [[ "$rc" -eq 0 && "$inventory" != *"$actual_id"* ]] || cleanup_rc=1
        fi
      fi
    fi
    [[ "$launcher_uncertain" -eq 0 ]] || cleanup_rc=1
    printf 'BACKFILL_FIXTURE_LAUNCHER original_exit=%s cleanup_exit=%s ambiguous=%s evidence=%s\n' "$original" "$cleanup_rc" "$launcher_uncertain" "$launcher_evidence"
    [[ "$original" -ne 0 || "$cleanup_rc" -eq 0 ]] || original=1
    exit "$original"
  }
  trap 'launcher_cleanup "$?"' EXIT
  set +e
  launcher_id="$(launcher_docker 30s create --pull=never --name "$launcher_name" --label "$launcher_label" \
    --cap-drop ALL --security-opt no-new-privileges:true \
    --network none --read-only --tmpfs /tmp:rw,exec,nosuid,nodev --tmpfs /opt/topsv3:rw,noexec,nosuid,nodev \
    --mount "type=bind,source=$launcher_source,target=/source,readonly" \
    --env TOPSV3_BACKFILL_FIXTURE_ISOLATED=true --entrypoint bash "$fixture_image" \
    /source/scripts/deploy/testar-backfill-previews-production.sh 2>"$launcher_evidence/create.log")"
  rc=$?
  set -e
  case "$rc" in 124|137|143) launcher_uncertain=1 ;; esac
  [[ "$rc" -eq 0 ]] || exit "$rc"
  [[ "$launcher_id" =~ ^[a-f0-9]{64}$ ]]
  printf '%s\n' "$launcher_id" >"$launcher_evidence/container.id"
  [[ "$(launcher_docker 30s inspect "$launcher_id" --format '{{.Id}} {{index .Config.Labels "topsv3.preview.fixture"}} {{.Image}}')" == "$launcher_id $launcher_owner $fixture_image" ]]
  set +e
  launcher_docker 180s start --attach "$launcher_id"
  rc=$?
  set -e
  case "$rc" in 124|137|143) launcher_uncertain=1 ;; esac
  [[ "$rc" -eq 0 ]] || exit "$rc"
  terminal="$(launcher_docker 30s inspect "$launcher_id" --format '{{.Id}} {{index .Config.Labels "topsv3.preview.fixture"}} {{.State.Running}} {{.State.ExitCode}}')"
  read -r terminal_id terminal_owner terminal_running terminal_rc <<<"$terminal"
  [[ "$terminal_id" == "$launcher_id" && "$terminal_owner" == "$launcher_owner" && "$terminal_running" == false && "$terminal_rc" =~ ^[0-9]+$ ]]
  exit "$terminal_rc"
)
if ! fixture_is_isolated; then
  [[ "${TOPSV3_BACKFILL_FIXTURE_ISOLATED:-false}" != true ]] || {
    printf 'ERRO: isolamento real do filho nao comprovado; sem recursao\n' >&2; exit 1;
  }
  launch_isolated_fixture
  exit "$?"
fi
for fixture_command in bash python3 flock sync readlink timeout; do command -v "$fixture_command" >/dev/null; done
helper="${root}/scripts/deploy/executar-backfill-previews-production.sh"
protector="${root}/scripts/deploy/proteger-operacao-production.sh"
service="${root}/backend/src/main/java/br/com/topsdojob/v3/application/operacional/midia/MidiaRestritaRegularizacaoService.java"
repository="${root}/backend/src/main/java/br/com/topsdojob/v3/persistence/repository/PreviewRestritoBackfillJdbcRepository.java"
bootstrap="${root}/backend/src/main/java/br/com/topsdojob/v3/application/operacional/midia/backfill/RestrictedMediaPreviewBackfillBootstrap.java"
configuration="${root}/backend/src/main/java/br/com/topsdojob/v3/application/operacional/midia/backfill/RestrictedMediaPreviewBackfillConfiguration.java"
inventory="${root}/backend/src/main/java/br/com/topsdojob/v3/infrastructure/storage/ObjectStorageInventory.java"
temporary="$(mktemp -d)"
lock_pid=
fixture_owner="synthetic-backfill-${BASHPID}"
previous_case=
canonical_owned=0
cleanup() {
  if [[ -n "$lock_pid" ]]; then
    touch "${temporary}/release-lock"
    wait "$lock_pid" || true
  fi
  if [[ "$canonical_owned" -eq 1 && -f /opt/topsv3/production/.fixture-owner ]] \
      && [[ "$(cat /opt/topsv3/production/.fixture-owner)" == "$fixture_owner" ]]; then
    mv -- /opt/topsv3/production "$temporary/final-production"
    rm -- /opt/topsv3/secrets/production.env
    rmdir -- /opt/topsv3/secrets
  fi
  rm -rf -- "$temporary"
}
trap cleanup EXIT
fail() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }
[[ "$(uname -s)" == Linux ]] || fail 'execute em Linux isolado; nao simular o destino canonico'
awk '
  $5 == "/opt" || $5 == "/opt/topsv3" {
    for (i=6;i<=NF;i++) if ($i == "-" && $(i+1) == "tmpfs") found=1
  }
  END { exit !found }
' /proc/self/mountinfo || fail 'fixture exige tmpfs exclusivo em /opt ou /opt/topsv3'
[[ ! -e /opt/topsv3/production && ! -L /opt/topsv3/production \
   && ! -e /opt/topsv3/secrets && ! -L /opt/topsv3/secrets ]] || fail 'namespace canonico da fixture preexistente'
sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
physical=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
container_id=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
mock_bin="${temporary}/bin"
mkdir -p "$mock_bin"
native_flock="$(command -v flock || true)"
real_python="$(command -v python3 || command -v python || true)"
# Required only if the portable helper obtains its UUID with Python.
export BACKFILL_FIXTURE_REAL_PYTHON="$real_python"

cat >"${mock_bin}/docker" <<'MOCK_DOCKER'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$#" -eq 1 && "$1" == --fixture-probe ]]; then printf 'SYNTHETIC_DOCKER_ONLY\n'; exit 0; fi
printf 'DOCKER|%s\n' "$*" >>"$BACKFILL_FIXTURE_EVENT_LOG"
state="$BACKFILL_FIXTURE_STATE"
id=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
case "$1" in
  ps)
    [[ "$*" == 'ps --all --quiet --filter label=topsv3.preview.operation' ]] || exit 91
    [[ ! -f "$state/container" && "${BACKFILL_FIXTURE_EXISTING_JOB:-false}" != true ]] || printf '%s\n' "$id"
    exit 0 ;;
  inspect)
    [[ -f "$state/container" ]] || exit 1
    [[ "$2" == "$id" || "$2" == "$(cat "$state/container")" ]] || exit 92
    if [[ $# -eq 2 ]]; then printf '{}\n'; exit 0; fi
    [[ "$3" == --format ]] || exit 93
    case "$4" in
      '{{.Id}}') printf '%s\n' "$id" ;;
      *topsv3.preview.operation*)
        if [[ "${BACKFILL_FIXTURE_OWNER_MISMATCH:-false}" == true ]]; then
          printf 'dddddddd-dddd-dddd-dddd-dddddddddddd\n'
        else cat "$state/owner"; fi ;;
      '{{.State.Running}}') printf '%s\n' "${BACKFILL_FIXTURE_CLEANUP_RUNNING:-false}" ;;
      *) exit 94 ;;
    esac
    exit 0 ;;
  rm)
    [[ "$#" -eq 2 && "$2" == "$id" && -f "$state/container" ]] || exit 95
    printf 'REMOVE|%s\n' "$2" >>"$BACKFILL_FIXTURE_EVENT_LOG"
    rm -- "$state/container"
    exit 0 ;;
  compose) ;;
  *) exit 96 ;;
esac
mode=UNKNOWN name= owner= report=
for ((index=1; index<=$#; index++)); do
  argument="${!index}"
  case "$argument" in
    --app.restricted-media-preview-reconciliation.mode=*) mode="${argument#*=}" ;;
    --name) next=$((index+1)); name="${!next}" ;;
    --label) next=$((index+1)); owner="${!next}"; owner="${owner#topsv3.preview.operation=}" ;;
    --app.restricted-media-preview-reconciliation.report-path=*) report="${argument##*/}" ;;
  esac
done
[[ "$mode" =~ ^(PLAN|APPLY|VALIDATE)$ && -n "$owner" && -n "$name" ]] || exit 97
printf 'RUN|%s\n' "$mode" >>"$BACKFILL_FIXTURE_EVENT_LOG"
printf '%s\n' "$name" >"$state/container"
printf '%s\n' "$owner" >"$state/owner"
printf '{"mode":"%s","commit":"%s"}\n' "$mode" "$BACKFILL_FIXTURE_COMMIT" >"$BACKFILL_FIXTURE_REPORT/$report.state.jsonl"
printf 'synthetic-before-no-real-identifiers\n' >"$BACKFILL_FIXTURE_REPORT/$report.before.json"
if [[ "$BACKFILL_FIXTURE_FAIL_APPLY" == true ]]; then
  printf 'fixture diagnostic_url=https://private.invalid/object\n' >&2
  exit 42
fi
updated=0 unchanged=0 batches=0 db_available=-1 db_unknown=-1 db_pending=-1 db_inconsistent=-1
if [[ "$mode" == APPLY ]]; then
  updated="$BACKFILL_FIXTURE_APPLY_UPDATED"; unchanged=$((1344-updated)); batches=7
  db_available=1344; db_unknown=0; db_pending=0; db_inconsistent=0
elif [[ "$mode" == VALIDATE ]]; then
  unchanged=1344; db_available=1344; db_unknown=0; db_pending=0; db_inconsistent=0
fi
printf 'RESTRICTED_MEDIA_PREVIEW_RECONCILIATION_RESULT mode=%s links=1344 eligible=1344 r2_available=1344 missing=0 inconsistent=0 unproven=0 r2_pages=2 db_updated=%s db_unchanged=%s batches=%s db_available=%s db_unknown=%s db_pending=%s db_inconsistent=%s\n' \
 "$mode" "$updated" "$unchanged" "$batches" "$db_available" "$db_unknown" "$db_pending" "$db_inconsistent"
MOCK_DOCKER
chmod 0700 "${mock_bin}/docker"

cat >"${mock_bin}/python3" <<'MOCK_OBSERVER'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$#" -eq 1 && "$1" == --fixture-probe ]]; then printf 'SYNTHETIC_OBSERVER_ONLY\n'; exit 0; fi
if [[ "$1" != */validar-transicao-previews-runtime.py ]]; then
  [[ -n "$BACKFILL_FIXTURE_REAL_PYTHON" ]] || exit 90
  exec "$BACKFILL_FIXTURE_REAL_PYTHON" "$@"
fi
printf 'OBSERVER|%s\n' "$2" >>"$BACKFILL_FIXTURE_EVENT_LOG"
case "$2" in
  pin)
    [[ "$3" == "$BACKFILL_FIXTURE_REPORT" && "$4" == "$BACKFILL_FIXTURE_SHA" ]] || exit 91
    [[ "${10}" == "$BACKFILL_FIXTURE_IMAGE" && "$#" -eq 10 ]] || exit 92
    [[ "${BACKFILL_FIXTURE_PIN_FAIL:-false}" != true ]] || exit 51
    printf 'synthetic physical pin\n' >"$3/pinned-backfill.compose.yml"
    printf '{"image":"%s"}\n' "$BACKFILL_FIXTURE_IMAGE" >"$3/image-pin.json"
    printf 'PREVIEW_IMAGE_PIN=PASS\n' ;;
  outcome)
    printf '{"commit":"%s","retry_allowed":false}\n' "$BACKFILL_FIXTURE_COMMIT" >"$3/outcome.json"
    printf 'PREVIEW_JOB_OUTCOME=%s terminal=true\n' "$BACKFILL_FIXTURE_COMMIT" ;;
  terminal)
    [[ "${BACKFILL_FIXTURE_TERMINAL_FAIL:-false}" != true ]] || exit 52
    [[ -f "$BACKFILL_FIXTURE_STATE/container" && "$5" == "$(cat "$BACKFILL_FIXTURE_STATE/owner")" ]] || exit 93
    printf '{"synthetic_terminal":true}\n' >"$3/container-terminal.json"
    printf 'PREVIEW_JOB_TERMINAL=PASS\n' ;;
  *) exit 94 ;;
esac
MOCK_OBSERVER
chmod 0700 "${mock_bin}/python3"
# Prove kernel execution, not merely mode bits: a noexec /tmp can otherwise
# make PATH silently skip the fixture and reach a real Docker executable.
[[ "$("${mock_bin}/docker" --fixture-probe)" == SYNTHETIC_DOCKER_ONLY ]] || fail 'mock Docker nao executavel; confira tmpfs exec'
[[ "$("${mock_bin}/python3" --fixture-probe)" == SYNTHETIC_OBSERVER_ONLY ]] || fail 'mock observer nao executavel; confira tmpfs exec'
[[ "$(PATH="$mock_bin:$PATH" command -v docker)" == "$mock_bin/docker" ]] || fail 'PATH nao seleciona Docker sintetico'
[[ "$(PATH="$mock_bin:$PATH" command -v python3)" == "$mock_bin/python3" ]] || fail 'PATH nao seleciona observer sintetico'

mutex_scope=real_flock
if [[ -z "$native_flock" ]]; then
  mutex_scope=synthetic_flock_boundary
  cat >"${mock_bin}/flock" <<'MOCK_FLOCK'
#!/usr/bin/env bash
set -euo pipefail
[[ "$#" -eq 2 && "$1" == -n && "$2" =~ ^[0-9]+$ ]] || exit 91
printf 'FLOCK|synthetic|%s\n' "$*" >>"$BACKFILL_FIXTURE_EVENT_LOG"
[[ "${BACKFILL_FIXTURE_LOCK_BUSY:-false}" != true ]]
MOCK_FLOCK
  chmod 0700 "${mock_bin}/flock"
fi

new_fixture() {
  name="$1"
  case_dir="${temporary}/$name"
  if [[ -d /opt/topsv3/production ]]; then
    [[ -n "$previous_case" && "$(cat /opt/topsv3/production/.fixture-owner)" == "$fixture_owner" ]] || fail 'arvore anterior sem ownership'
    mv -- /opt/topsv3/production "$previous_case/production-final"
  fi
  deploy_root=/opt/topsv3/production
  release_dir="$deploy_root/releases/$sha"
  env_file=/opt/topsv3/secrets/production.env
  report_dir="$case_dir/evidence"
  events="$case_dir/events"
  state="$case_dir/docker-state"
  mkdir -p "$release_dir/deploy/production" "$release_dir/scripts/deploy" /opt/topsv3/secrets "$report_dir" "$state"
  printf '%s\n' "$fixture_owner" >"$deploy_root/.fixture-owner"
  canonical_owned=1
  : >"$release_dir/deploy/production/docker-compose.yml"
  : >"$env_file"
  printf '%s\n' "$sha" >"$release_dir/.release-sha"
  cp -- "$protector" "$release_dir/scripts/deploy/proteger-operacao-production.sh"
  ln -s "$release_dir" "$deploy_root/current"
  [[ -L "$deploy_root/current" ]] || fail 'fixture exige current symlink real; nao simular _op_lock'
  previous_case="$case_dir"
  : >"$events"
  confirm="APPLY:$sha"; image_id="$physical"; active=0; pin_fail=false; terminal_fail=false
  owner_mismatch=false; cleanup_running=false; busy=false; existing_job=false
  updated=1344; fail_apply=false; commit=COMMITTED
}

invoke() {
  local mode="$1" output="$2"
  set +e
  PATH="$mock_bin:$PATH" \
    OP_ACTIVE="$active" TOPSV3_PREVIEW_BACKFILL_CONFIRM="$confirm" TOPSV3_PREVIEW_BACKFILL_IMAGE_ID="$image_id" \
    BACKFILL_FIXTURE_EVENT_LOG="$events" BACKFILL_FIXTURE_STATE="$state" \
    BACKFILL_FIXTURE_REPORT="$report_dir" BACKFILL_FIXTURE_SHA="$sha" BACKFILL_FIXTURE_IMAGE="$physical" \
    BACKFILL_FIXTURE_APPLY_UPDATED="$updated" BACKFILL_FIXTURE_FAIL_APPLY="$fail_apply" \
    BACKFILL_FIXTURE_COMMIT="$commit" BACKFILL_FIXTURE_PIN_FAIL="$pin_fail" \
    BACKFILL_FIXTURE_TERMINAL_FAIL="$terminal_fail" BACKFILL_FIXTURE_OWNER_MISMATCH="$owner_mismatch" \
    BACKFILL_FIXTURE_CLEANUP_RUNNING="$cleanup_running" BACKFILL_FIXTURE_LOCK_BUSY="$busy" \
    BACKFILL_FIXTURE_EXISTING_JOB="$existing_job" \
    bash "$helper" "$mode" initial "$sha" "$release_dir" "$env_file" \
      topsv3-production topsv3-production topsv3-production-net "$report_dir" >"$output" 2>&1
  rc=$?
  set -e
}
assert_no_run() { ! grep -q '^RUN|' "$events" || fail "$name executou Docker run indevidamente"; }
assert_runs() { [[ "$(grep -c '^RUN|' "$events" || true)" -eq "$1" ]] || fail "$name numero de execucoes"; }
assert_rc() { [[ "$rc" -eq "$1" ]] || { cat "$2" >&2; fail "$name exit=$rc esperado=$1"; }; }

run_fixture() {
  local fixture_name="$1" mode="$2" delta="${3:-1344}"
  new_fixture "$fixture_name"
  updated="$delta"
  [[ "$mode" == APPLY ]] || { confirm=; commit=NOT_STARTED; }
  invoke "$mode" "$case_dir/output"
  assert_rc 0 "$case_dir/output"
  assert_runs 1
  [[ ! -e "$deploy_root/operations/preview-backfill.pending" && ! -e "$state/container" ]] || fail "$name cleanup incompleto"
  grep -Fxq "REMOVE|$container_id" "$events" || fail "$name cleanup nao usou ID imutavel"
  grep -Fq -- '--app.bootstrap=restricted-media-preview-backfill' "$events"
  grep -Fq -- '--pull never' "$events"
  grep -Fq -- '/pinned-backfill.compose.yml' "$events"
  [[ "$(grep '^OBSERVER|' "$events")" == $'OBSERVER|pin\nOBSERVER|outcome\nOBSERVER|terminal' ]] || fail "$name ordem de observacao"
  if [[ "$mode" == APPLY ]]; then expected=true; else expected=false; fi
  grep -Fq -- "--app.restricted-media-preview-reconciliation.apply-confirmed=$expected" "$events"
}
run_fixture plan PLAN
run_fixture apply APPLY 1344
run_fixture apply_idempotent APPLY 0
grep -Fq 'mode=APPLY updated=0 unchanged=1344 batches=7' "$case_dir/output"
run_fixture validate VALIDATE

for failure in mode_alone wrong_confirmation invalid_image active_operation pin_failure existing_job prior_report root_divergent; do
  new_fixture "$failure"
  expected=1
  case "$failure" in
    mode_alone) confirm= ;;
    wrong_confirmation) confirm="APPLY:dddddddddddddddddddddddddddddddddddddddd" ;;
    invalid_image) image_id=sha256:invalid ;;
    active_operation) active=1 ;;
    pin_failure) pin_fail=true; commit=UNKNOWN; expected=51 ;;
    existing_job) existing_job=true ;;
    prior_report) printf 'existing journal\n' >"$report_dir/initial-apply.tsv.state.jsonl" ;;
    root_divergent)
      release_dir="$case_dir/other/production/releases/$sha"
      mkdir -p "$release_dir/deploy/production" "$release_dir/scripts/deploy"
      : >"$release_dir/deploy/production/docker-compose.yml"
      printf '%s\n' "$sha" >"$release_dir/.release-sha"
      cp -- "$protector" "$release_dir/scripts/deploy/proteger-operacao-production.sh"
      ln -s "$release_dir" "$case_dir/other/production/current" ;;
  esac
  invoke APPLY "$case_dir/output"
  assert_rc "$expected" "$case_dir/output"
  assert_no_run
  [[ ! -e "$deploy_root/operations/preview-backfill.pending" ]] || fail "$name criou intent antes do pin"
  if [[ "$failure" == root_divergent ]]; then
    grep -Fq 'identidade da release independente divergente' "$case_dir/output"
    [[ ! -e "$deploy_root/deploy.lock" && ! -e "$case_dir/other/production/deploy.lock" ]] || fail 'raiz divergente alcancou mutex'
    [[ ! -s "$events" ]] || fail 'raiz divergente alcancou Docker/observer'
  fi
done

for failure in pending nonterminal_deploy mutex; do
  new_fixture "$failure"
  mkdir -p "$deploy_root/operations"
  case "$failure" in
    pending) printf 'prior-owner\n' >"$deploy_root/operations/preview-backfill.pending"; expected=76 ;;
    nonterminal_deploy)
      printf '%s\n' version=1 id=dddddddd-dddd-dddd-dddd-dddddddddddd "candidate=$sha" previous= \
        phase=PREVIEW_APPLY pid=1 start=1 boot=eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee child_pid= child_start= \
        mutated=1 snapshot=1 result=INCOMPLETE original_rc=42 ambiguous=1 migration_started=0 >"$deploy_root/operations/active.state"
      expected=1 ;;
    mutex)
      expected=75
      if [[ -n "$native_flock" ]]; then
        (
          exec 9>"$deploy_root/deploy.lock"
          "$native_flock" -n 9 || exit 90
          touch "$temporary/lock-held"
          deadline=$((SECONDS+10))
          until [[ -e "$temporary/release-lock" ]]; do (( SECONDS < deadline )) || exit 91; sleep 0.05; done
        ) &
        lock_pid=$!
        deadline=$((SECONDS+5))
        until [[ -e "$temporary/lock-held" ]]; do (( SECONDS < deadline )) || fail 'mutex nao adquirido'; sleep 0.05; done
      else busy=true; fi ;;
  esac
  invoke APPLY "$case_dir/output"
  assert_rc "$expected" "$case_dir/output"
  assert_no_run
  if [[ -n "$lock_pid" ]]; then touch "$temporary/release-lock"; wait "$lock_pid"; lock_pid=; fi
done

for observed in ROLLED_BACK COMMITTED UNKNOWN; do
  new_fixture "failure_$observed"
  fail_apply=true; commit="$observed"
  invoke APPLY "$case_dir/output"
  assert_rc 42 "$case_dir/output"
  assert_runs 1
  grep -Fq "PREVIEW_JOB_OUTCOME=$observed" "$case_dir/output"
  ! grep -Fq 'private.invalid' "$case_dir/output" || fail 'diagnostico privado vazou'
  [[ -e "$state/container" && -e "$deploy_root/operations/preview-backfill.pending" ]] || fail 'erro apagou evidencia/intent'
  ! grep -q '^REMOVE|' "$events" || fail 'erro removeu container'
  ! grep -q '^OBSERVER|terminal' "$events" || fail 'erro avancou ao gate positivo'
  before="$(sha256sum "$deploy_root/operations/preview-backfill.pending" "$report_dir/initial-apply.tsv.state.jsonl")"
  invoke APPLY "$case_dir/retry-output"
  assert_rc 76 "$case_dir/retry-output"
  assert_runs 1
  [[ "$(sha256sum "$deploy_root/operations/preview-backfill.pending" "$report_dir/initial-apply.tsv.state.jsonl")" == "$before" ]] || fail 'retry alterou evidencias'
done

for failure in terminal_failure owner_changed running_after_receipt; do
  new_fixture "$failure"
  case "$failure" in
    terminal_failure) terminal_fail=true; expected=52 ;;
    owner_changed) owner_mismatch=true; expected=1 ;;
    running_after_receipt) cleanup_running=true; expected=1 ;;
  esac
  invoke APPLY "$case_dir/output"
  assert_rc "$expected" "$case_dir/output"
  assert_runs 1
  [[ -e "$state/container" && -e "$deploy_root/operations/preview-backfill.pending" ]] || fail 'cleanup apagou estado nao comprovado'
  ! grep -q '^REMOVE|' "$events" || fail 'cleanup removeu sem terminal/ownership'
done
printf 'PREVIEW_BACKFILL_TEST_SCOPE helper=real shared_lock_function=real mutex=%s docker=synthetic observer=synthetic\n' "$mutex_scope"

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

printf 'PREVIEW_BACKFILL_DEPLOY_TESTS=PASS\n'
