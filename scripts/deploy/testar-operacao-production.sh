#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
self="${script_dir}/testar-operacao-production.sh"
helper="${script_dir}/proteger-operacao-production.sh"
previous_sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
candidate_sha=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb

fail() { printf 'FALHA_OPERACAO_TESTE: %s\n' "$*" >&2; exit 1; }
event() { printf '%s\n' "$1" >> "${test_root}/events"; }

if [[ "${1:-}" == --controller ]]; then
  env --default-signal=INT,TERM,HUP bash "$self" --fixture "$2" hold &
  wait "$!"
  exit "$?"
fi

if [[ "${1:-}" == --daemon ]]; then
  test_root="$2"
  deadline=$((SECONDS + 12))
  event daemon_started
  until [[ -f "${test_root}/daemon-request" ]]; do
    (( SECONDS < deadline )) || exit 92
    sleep 0.05
  done
  event daemon_pending
  until [[ -f "${test_root}/release-daemon" ]]; do
    (( SECONDS < deadline )) || exit 93
    sleep 0.05
  done
  printf '%s\n' "$candidate_sha" > "${test_root}/runtime"
  printf 'config-%s\n' "$candidate_sha" > "${test_root}/config"
  event daemon_finished
  exit 0
fi

# Only service boundaries are controlled here. Ownership, journal, signals,
# process groups, flock and recovery dispatch are the production helper itself.
if [[ "${1:-}" == --fixture ]]; then
  test_root="$2"
  test_mode="$3"
  source "$helper"
  _op_capture_baseline() {
    OP_PREVIOUS_SHA="$previous_sha"
    OP_PREVIOUS_RELEASE="${test_root}/releases/${previous_sha}"
    cp "${test_root}/config" "${test_root}/config.before"
    _op_config_hashes > "${OP_DIR}/config.sha256"
    event baseline
  }
  _op_config_hashes() { sha256sum "${test_root}/config"; }
  _op_verify_runtime() {
    if [[ "$test_mode" == finish_hold && "${2:-}" == "${OP_DIR}/candidate.images.tsv" ]]; then
      event finish_verify_started
      local deadline=$((SECONDS + 12))
      until [[ -f "${test_root}/release-finish" ]]; do
        (( SECONDS < deadline )) || return 95
        sleep 0.05
      done
    fi
    [[ "$(cat "${test_root}/runtime")" == "$1" ]]
  }
  _op_restore() {
    event restore
    [[ "$test_mode" != restore_failure ]] || return 44
    printf '%s\n' "$previous_sha" > "${test_root}/runtime"
    cp "${test_root}/config.before" "${test_root}/config"
    ln -sfn "releases/${previous_sha}" "${test_root}/current"
  }
  op_smoke() {
    event "smoke:$1"
    if [[ "$test_mode" == smoke_failure && "$1" == "$candidate_sha" ]]; then return 43; fi
    _op_verify_runtime "$1" || return 1
    if [[ "$1" == "$candidate_sha" ]]; then
      _op_config_hashes > "${OP_DIR}/candidate.config.sha256"
    fi
  }
  if [[ "$test_mode" == manual_rollback || "$test_mode" == manual_without_confirmation ]]; then
    operation="$(sed -n 's/^id=//p' "${test_root}/operations/active.state")"
    if [[ "$test_mode" == manual_without_confirmation ]]; then
      _op_manual rollback "$test_root" "$operation"
    else
      _op_manual rollback "$test_root" "$operation" --confirm-daemon-quiescent
    fi
    exit 0
  fi
  op_begin "$test_root" "$candidate_sha"
  event begun
  case "$test_mode" in
    before_failure) op_run readonly bash -c 'exit 41' ;;
    after_failure|restore_failure)
      op_run mutating bash "$self" --mutator "$test_root" fail ;;
    explicit_exit)
      op_run mutating bash "$self" --mutator "$test_root" immediate
      exit 23 ;;
    hold) op_run mutating bash "$self" --mutator "$test_root" gated ;;
    descendant) op_run mutating bash "$self" --mutator "$test_root" descendant ;;
    daemon_failure) op_run mutating bash "${test_root}/bin/docker" "$test_root" ;;
    stdin)
      op_run mutating bash -c 'IFS= read -r payload; printf "%s\n" "$payload" > "$1/payload"' \
        -- "$test_root" <<< 'INDEXNOW-SYNTHETIC-OPERATION'
      op_run readonly bash -c 'cat > "$1/unexpected-stdin"' -- "$test_root"
      op_run mutating bash "$self" --mutator "$test_root" immediate ;;
    success|smoke_failure|finish_hold)
      op_run mutating bash "$self" --mutator "$test_root" immediate ;;
    *) fail "modo fixture desconhecido" ;;
  esac
  op_phase SMOKE
  op_smoke "$candidate_sha"
  op_phase PROMOTING
  ln -s "releases/${candidate_sha}" "${test_root}/.candidate"
  mv -Tf "${test_root}/.candidate" "${test_root}/current"
  op_finish
  event finished
  exit 0
fi

if [[ "${1:-}" == --mutator ]]; then
  test_root="$2"
  mutator_mode="$3"
  if [[ "$mutator_mode" == descendant ]]; then
    bash "$self" --mutator "$test_root" gated &
    exit 0
  fi
  printf '%s\n' "$$" > "${test_root}/child.pid"
  event child_started
  if [[ "$mutator_mode" == gated ]]; then
    deadline=$((SECONDS + 12))
    until [[ -f "${test_root}/release-child" ]]; do
      (( SECONDS < deadline )) || exit 91
      sleep 0.05
    done
  fi
  printf '%s\n' "$candidate_sha" > "${test_root}/runtime"
  printf 'config-%s\n' "$candidate_sha" > "${test_root}/config"
  event child_mutated
  [[ "$mutator_mode" != fail ]] || exit 42
  event child_finished
  exit 0
fi

[[ "$(uname -s)" == Linux ]] || fail 'execute em Linux real, nao em MSYS/Git Bash'
for executable in bash flock setsid timeout ps env; do command -v "$executable" >/dev/null || fail "ferramenta ausente: $executable"; done
[[ -r "$helper" ]] || fail 'helper real ausente'
temp_dir="$(mktemp -d)"
declare -a case_roots=() owner_pids=()
process_start() {
  local stat_line remainder
  local -a fields
  [[ -r "/proc/$1/stat" ]] || return 1
  IFS= read -r stat_line < "/proc/$1/stat" || return 1
  remainder="${stat_line##*) }"
  read -r -a fields <<< "$remainder"
  printf '%s\n' "${fields[19]}"
}
cleanup() {
  local rc=$? root pid ownership started
  trap - EXIT
  for root in "${case_roots[@]}"; do touch "${root}/release-child" "${root}/release-daemon" "${root}/release-finish"; done
  for ownership in "${owner_pids[@]}"; do
    pid="${ownership%%:*}"
    started="${ownership#*:}"
    # PID plus kernel start time prevents a stale test record killing a reused PID.
    if [[ "$(process_start "$pid" || true)" == "$started" ]]; then kill -TERM "$pid" 2>/dev/null || true; fi
    wait "$pid" 2>/dev/null || true
  done
  [[ "$(cat "${temp_dir}/sentinel")" == preserve ]] || rc=1
  rm -rf -- "$temp_dir" || { [[ "$rc" -ne 0 ]] || rc=1; }
  exit "$rc"
}
printf 'preserve\n' > "${temp_dir}/sentinel"
trap cleanup EXIT

new_case() {
  test_root="${temp_dir}/$1"
  case_roots+=("$test_root")
  mkdir -p "${test_root}/releases/${previous_sha}" "${test_root}/releases/${candidate_sha}"
  mkdir -p "${temp_dir}/secrets"
  printf '%s\n' "$previous_sha" > "${test_root}/releases/${previous_sha}/.release-sha"
  printf '%s\n' "$candidate_sha" > "${test_root}/releases/${candidate_sha}/.release-sha"
  ln -s "releases/${previous_sha}" "${test_root}/current"
  printf '%s\n' "$previous_sha" > "${test_root}/runtime"
  printf 'config-%s\n' "$previous_sha" > "${test_root}/config"
  touch "${test_root}/events"
}
assert_runtime() { [[ "$(cat "${test_root}/runtime")" == "$1" ]] || fail 'identidade runtime divergente'; }
assert_result() { grep -qx "result=$1" "${test_root}/operations/active.state" || fail "resultado journal esperado: $1"; }
assert_restore_count() {
  local count
  count="$(grep -c '^restore$' "${test_root}/events" || true)"
  [[ "$count" -eq "$1" ]] || fail "recuperacoes=${count}; esperado=$1"
}
expect_rc() {
  local expected="$1" actual
  shift
  if "$@" > "${test_root}/last.log" 2>&1; then actual=0; else actual=$?; fi
  if [[ "$actual" -ne "$expected" ]]; then
    cat "${test_root}/last.log" >&2
    fail "exit=${actual}; esperado=${expected}"
  fi
}
start_fixture() {
  # Reset INT before exec: asynchronous Bash otherwise inherits SIGINT ignored.
  env --default-signal=INT,TERM,HUP setsid bash "$self" --fixture "$test_root" "$1" \
    > "${test_root}/owner.log" 2>&1 &
  owner_pid=$!
  owner_start="$(process_start "$owner_pid")"
  owner_pids+=("${owner_pid}:${owner_start}")
}
signal_owner() {
  [[ "$(process_start "$owner_pid" || true)" == "$owner_start" ]] || fail 'identidade do owner mudou antes do sinal'
  kill -s "$1" "$owner_pid"
}
wait_event() {
  local expected="$1" deadline=$((SECONDS + 5))
  until grep -qx "$expected" "${test_root}/events"; do
    if (( SECONDS >= deadline )); then
      cat "${test_root}/owner.log" >&2
      fail "evento ausente: $expected"
    fi
    sleep 0.05
  done
}
wait_owner() {
  local expected="$1" actual
  if wait "$owner_pid"; then actual=0; else actual=$?; fi
  if [[ "$actual" -ne "$expected" ]]; then cat "${test_root}/owner.log" >&2; fail "owner exit=${actual}; esperado=${expected}"; fi
}
operation_id() { basename "$(find "${test_root}/operations" -mindepth 1 -maxdepth 1 -type d | head -n 1)"; }

(
  source "$helper"
  docker() { printf '%s\n' "$effective_fixture"; }
  effective_fixture=$'env "ALPHA=one"\nenv "BETA=two"\nmount {"Source":"/one","Destination":"/a","RW":false}\nmount {"Source":"/two","Destination":"/b","RW":true}\nports {}\nnetwork "isolated"'
  first="$(_op_effective_hash backend)"
  effective_fixture=$'network "isolated"\nmount {"Source":"/two","Destination":"/b","RW":true}\nenv "BETA=two"\nports {}\nmount {"Source":"/one","Destination":"/a","RW":false}\nenv "ALPHA=one"'
  [[ "$(_op_effective_hash backend)" == "$first" ]] || fail 'ordem equivalente de env/mounts mudou identidade'
  reordered="$effective_fixture"
  effective_fixture="${reordered/BETA=two/BETA=changed}"
  [[ "$(_op_effective_hash backend)" != "$first" ]] || fail 'valor alterado de env passou como equivalente'
  effective_fixture="${reordered/\"RW\":true/\"RW\":false}"
  [[ "$(_op_effective_hash backend)" != "$first" ]] || fail 'campo alterado do mount passou como equivalente'
)
echo 'PASS: hash_efetivo_ignora_ordem_mas_preserva_valores_env_mount'

new_case success
expect_rc 0 timeout --kill-after=2s 15s bash "$self" --fixture "$test_root" success
assert_runtime "$candidate_sha"
assert_result COMPLETED
assert_restore_count 0
echo 'PASS: ativacao_completa'

new_case stdin
if ! bash -s -- "$self" "$test_root" > "${test_root}/stdin.log" 2>&1 <<'REMOTE_STDIN'
set -euo pipefail
bash "$1" --fixture "$2" stdin
printf 'marcador_posterior\n' > "$2/after-stdin"
REMOTE_STDIN
then cat "${test_root}/stdin.log" >&2; fail 'wrapper nao concluiu entrada explicita'; fi
[[ "$(cat "${test_root}/payload")" == INDEXNOW-SYNTHETIC-OPERATION ]] || fail 'here-string nao chegou ao filho mutante'
[[ ! -s "${test_root}/unexpected-stdin" ]] || fail 'filho readonly consumiu controle stdin'
grep -qx marcador_posterior "${test_root}/after-stdin" || fail 'marcador posterior do bash -s perdido'
assert_result COMPLETED
echo 'PASS: op_run_preserva_here_string_e_nao_drena_bash_stdin'

new_case competing
start_fixture hold
wait_event child_started
expect_rc 75 bash "$self" --fixture "$test_root" success
expect_rc 75 bash "$helper" rollback "$test_root" "$(operation_id)" --confirm-daemon-quiescent
assert_runtime "$previous_sha"
touch "${test_root}/release-child"
wait_owner 0
assert_runtime "$candidate_sha"
assert_result COMPLETED
assert_restore_count 0
echo 'PASS: duas_ativacoes_e_rollback_manual_compartilham_lock'

for mode in before_failure after_failure smoke_failure explicit_exit restore_failure; do
  new_case "$mode"
  case "$mode" in before_failure) expected=41 ;; smoke_failure) expected=43 ;; explicit_exit) expected=23 ;; *) expected=42 ;; esac
  expect_rc "$expected" timeout --kill-after=2s 15s bash "$self" --fixture "$test_root" "$mode"
  if [[ "$mode" == before_failure ]]; then
    assert_result ABORTED
    assert_restore_count 0
    assert_runtime "$previous_sha"
  elif [[ "$mode" == restore_failure ]]; then
    assert_result INCOMPLETE
    assert_restore_count 1
    assert_runtime "$candidate_sha"
    expect_rc 76 bash "$self" --fixture "$test_root" success
  else
    assert_result ROLLED_BACK
    assert_restore_count 1
    assert_runtime "$previous_sha"
    [[ "$(cat "${test_root}/config")" == "config-${previous_sha}" ]] || fail 'configuracao anterior nao restaurada'
  fi
  printf 'PASS: %s erro_original=%s\n' "$mode" "$expected"
done

for signal in INT TERM HUP; do
  new_case "signal-${signal}"
  start_fixture hold
  wait_event child_started
  signal_owner "$signal"
  expect_rc 75 bash "$self" --fixture "$test_root" success
  assert_runtime "$previous_sha"
  touch "${test_root}/release-child"
  case "$signal" in INT) expected=130 ;; TERM) expected=143 ;; HUP) expected=129 ;; esac
  wait_owner "$expected"
  assert_restore_count 1
  assert_result ROLLED_BACK
  assert_runtime "$previous_sha"
  grep -qx child_finished "${test_root}/events" || fail 'recuperou antes do termino do filho'
  [[ "$(grep -n '^restore$' "${test_root}/events" | cut -d: -f1)" -gt "$(grep -n '^child_finished$' "${test_root}/events" | cut -d: -f1)" ]] || fail 'restore sobreposto ao filho mutante'
  printf 'PASS: sinal_%s_preserva_lock_ate_filho_terminar\n' "$signal"
done

for signal in TERM HUP; do
  new_case "finish-${signal}"
  start_fixture finish_hold
  wait_event finish_verify_started
  signal_owner "$signal"
  expect_rc 75 bash "$self" --fixture "$test_root" success
  touch "${test_root}/release-finish"
  if [[ "$signal" == TERM ]]; then expected=143; else expected=129; fi
  wait_owner "$expected"
  assert_result ROLLED_BACK
  assert_restore_count 1
  assert_runtime "$previous_sha"
  [[ "$(readlink -e "${test_root}/current")" == "${test_root}/releases/${previous_sha}" ]] || fail 'promocao nao recuperada apos sinal final'
  printf 'PASS: sinal_%s_durante_verify_final_nao_publica_COMPLETED\n' "$signal"
done

new_case lost_control_parent
env --default-signal=INT,TERM,HUP setsid bash "$self" --controller "$test_root" \
  > "${test_root}/owner.log" 2>&1 &
owner_pid=$!
owner_start="$(process_start "$owner_pid")"
owner_pids+=("${owner_pid}:${owner_start}")
wait_event child_started
remote_owner="$(sed -n 's/^pid=//p' "${test_root}/operations/active.state")"
remote_start="$(process_start "$remote_owner")"
owner_pids+=("${remote_owner}:${remote_start}")
signal_owner KILL
wait_owner 137
expect_rc 75 bash "$self" --fixture "$test_root" success
# The real helper observes its vanished parent every second. Keep the mutator
# gated past that observation; this is parent-process loss, not an SSH emulation.
sleep 2
touch "${test_root}/release-child"
deadline=$((SECONDS + 5))
until grep -qx 'result=ROLLED_BACK' "${test_root}/operations/active.state"; do
  (( SECONDS < deadline )) || { cat "${test_root}/owner.log" >&2; fail 'perda do controlador nao recuperou'; }
  sleep 0.05
done
assert_restore_count 1
assert_runtime "$previous_sha"
grep -qx original_rc=129 "${test_root}/operations/active.state" || fail 'perda de pai nao preservou HUP original'
echo 'PASS: perda_real_do_pai_de_controle_drena_filho_e_recupera'

new_case descendant
start_fixture descendant
wait_event child_started
expect_rc 75 bash "$self" --fixture "$test_root" success
touch "${test_root}/release-child"
wait_owner 0
assert_runtime "$candidate_sha"
assert_result COMPLETED
echo 'PASS: descendente_sobrevive_ao_comando_sem_liberar_lock'

new_case killed_owner
start_fixture hold
wait_event child_started
signal_owner KILL
wait_owner 137
expect_rc 75 bash "$self" --fixture "$test_root" success
touch "${test_root}/release-child"
wait_event child_finished
deadline=$((SECONDS + 5))
until flock -n "${test_root}/deploy.lock" true; do
  (( SECONDS < deadline )) || fail 'lock persistiu depois do ultimo filho'
  sleep 0.05
done
expect_rc 76 bash "$self" --fixture "$test_root" success
assert_runtime "$candidate_sha"
assert_restore_count 0
echo 'PASS: perda_abrupta_controle_bloqueia_nova_mutacao_ate_reconciliacao'

new_case independent_daemon
mkdir "${test_root}/bin"
cat > "${test_root}/bin/docker" <<'DOCKER_FAILURE'
#!/usr/bin/env bash
set -euo pipefail
touch "$1/daemon-request"
deadline=$((SECONDS + 5))
until grep -qx daemon_pending "$1/events"; do
  (( SECONDS < deadline )) || exit 94
  sleep 0.05
done
exit 1
DOCKER_FAILURE
chmod +x "${test_root}/bin/docker"
# This daemon exists before op_begin, has its own session, and never inherits
# the operation's flock. CLI exit therefore cannot prove service quiescence.
env --default-signal=INT,TERM,HUP setsid bash "$self" --daemon "$test_root" \
  > "${test_root}/daemon.log" 2>&1 &
daemon_pid=$!
owner_pids+=("${daemon_pid}:$(process_start "$daemon_pid")")
wait_event daemon_started
expect_rc 1 bash "$self" --fixture "$test_root" daemon_failure
[[ -f "${test_root}/daemon-request" ]] || { cat "${test_root}/last.log" >&2; fail 'CLI controlada nao enviou pedido ao daemon'; }
assert_result INCOMPLETE
assert_restore_count 0
assert_runtime "$previous_sha"
expect_rc 76 bash "$self" --fixture "$test_root" success
expect_rc 2 bash "$self" --fixture "$test_root" manual_without_confirmation
assert_restore_count 0
touch "${test_root}/release-daemon"
wait "$daemon_pid"
assert_runtime "$candidate_sha"
grep -qx daemon_finished "${test_root}/events" || fail 'daemon independente nao terminou'
expect_rc 0 bash "$self" --fixture "$test_root" manual_rollback
assert_result ROLLED_BACK
assert_runtime "$previous_sha"
assert_restore_count 1
expect_rc 0 bash "$self" --fixture "$test_root" success
assert_result COMPLETED
echo 'PASS: cli_exit1_nao_prova_daemon_parado_recuperacao_exige_confirmacao'

# Exercise the actual smoke scheduler with an explicitly controlled clock.
# These fast checks prove branching/deadline arithmetic, NOT elapsed real time;
# the container suite separately waits the full 32/183/300 seconds.
for smoke_mode in recovery_late recovery_absent candidate_late baseline_late; do
  (
    source "$helper"
    OP_DIR="${temp_dir}/clock-${smoke_mode}"
    mkdir "$OP_DIR"
    OP_ID=11111111-1111-1111-1111-111111111111 OP_PREVIOUS_SHA="$previous_sha"
    OP_SECRETS="$OP_DIR" OP_CANCEL_RC=0 OP_RECOVERING=1
    printf 'fixture\n' > "${OP_DIR}/candidate.images.tsv"
    sha256sum "${OP_DIR}/candidate.images.tsv" > "${OP_DIR}/candidate.config.sha256"
    selected_sha="$previous_sha"
    [[ "$smoke_mode" != candidate_late ]] || selected_sha="$candidate_sha"
    [[ "$smoke_mode" != baseline_late ]] || OP_RECOVERING=0
    probe_count=0 home_count=0 catalog_count=0 active_probe=0 last_probe_end=0
    _op_verify_runtime() { [[ "$1" == "$selected_sha" ]]; }
    sleep() {
      if [[ "$smoke_mode" == recovery_* ]]; then
        (( $1 == 15 || SECONDS + $1 == 300 )) || fail 'intervalo de recovery alterado'
      fi
      SECONDS=$((SECONDS + $1))
    }
    _op_http_probe() {
      local kind="$2" remaining="${4:-999}" cost=1 result=0 start="$SECONDS"
      (( active_probe == 0 )) || fail 'probe controlada sobreposta'
      active_probe=1
      if [[ "$smoke_mode" == recovery_* ]]; then
        (( remaining == 300 - SECONDS && remaining > 0 )) || fail 'saldo absoluto do probe divergente'
        if [[ "$kind" == readiness && "$probe_count" -gt 0 ]]; then
          (( SECONDS - last_probe_end >= 15 )) || fail 'novo lote sem espacamento'
        fi
      fi
      case "$kind" in
        readiness) if (( SECONDS < 32 )); then cost=2; result=1; fi ;;
        home|catalog)
          (( SECONDS >= 32 )) || fail 'SSR antes de readiness'
          if [[ "$kind" == home ]]; then home_count=$((home_count + 1)); else catalog_count=$((catalog_count + 1)); fi
          if (( SECONDS < 183 )) || [[ "$smoke_mode" == recovery_absent ]]; then cost=7; result=1; fi ;;
        *) fail 'tipo de probe desconhecido' ;;
      esac
      (( cost <= remaining )) || cost="$remaining"
      SECONDS=$((SECONDS + cost))
      last_probe_end="$SECONDS" probe_count=$((probe_count + 1)) active_probe=0
      printf 'CLOCK_PROBE scenario=%s kind=%s start_s=%s end_s=%s remaining_s=%s rc=%s clock=controlled\n' \
        "$smoke_mode" "$kind" "$start" "$SECONDS" "$remaining" "$result"
      return "$result"
    }
    SECONDS=0
    if op_smoke "$selected_sha"; then smoke_rc=0; else smoke_rc=$?; fi
    case "$smoke_mode" in
      recovery_late) (( smoke_rc == 0 && SECONDS >= 183 && SECONDS < 300 && catalog_count == 1 && home_count > 4 )) || fail 'recuperacao tardia nao coberta' ;;
      recovery_absent) (( smoke_rc == 1 && SECONDS == 300 && catalog_count == 0 && probe_count <= 42 )) || fail 'limite recovery nao finito/exato' ;;
      *) (( smoke_rc == 1 && SECONDS < 183 && home_count == 4 && catalog_count == 0 )) || fail 'janela candidata/baseline ampliada indevidamente' ;;
    esac
    printf 'PASS: smoke_%s clock=controlled elapsed_s=%s probes=%s exit=%s\n' "$smoke_mode" "$SECONDS" "$probe_count" "$smoke_rc"
  )
done

(
  source "$helper"
  OP_DIR="${temp_dir}/probe-budget"
  mkdir "$OP_DIR"
  curl() {
    local body= limit= argument
    while (( $# )); do
      argument="$1"; shift
      case "$argument" in --output) body="$1"; shift ;; --max-time) limit="$1"; shift ;; esac
    done
    printf '%s\n' "$limit" >> "${OP_DIR}/limits"
    printf '%s\n' '{"status":"UP","app":"topsdojob-v3-backend"}<h1>Encontre acompanhantes perto de você</h1>' > "$body"
    printf '200 0.001'
  }
  _op_http_probe http://127.0.0.1:23000/ home fixture-budget 1
  _op_http_probe http://127.0.0.1:23000/ home fixture-budget 300
  _op_http_probe http://127.0.0.1:28080/api/health/readiness readiness fixture-budget 300
  if _op_http_probe http://127.0.0.1:23000/ home fixture-budget 0; then fail 'probe sem saldo executou'; fi
  [[ "$(cat "${OP_DIR}/limits")" == $'1\n7\n2' ]] || fail 'curl nao limitado pelo saldo ou probe zero fez I/O'
)
echo 'PASS: probe_real_clampa_timeout_ao_saldo_e_zero_nao_chama_curl'

sha256sum "$helper"
echo 'PRODUCTION_OPERATION_PROCESS_TESTS=PASS'
