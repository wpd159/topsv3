#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
self="${script_dir}/testar-operacao-production.sh"
helper="${script_dir}/proteger-operacao-production.sh"
previous_sha=a60b1e74978017a5bba1577f58804933b347c790
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
    printf '%s|legacy-a60\n' "$previous_sha" > "${OP_DIR}/previous.health-profile"
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
    if [[ "$test_mode" == manual_reobserve_failure && "$1" == "$previous_sha" ]]; then return 45; fi
    _op_verify_runtime "$1" || return 1
    if [[ "$1" == "$candidate_sha" ]]; then
      _op_config_hashes > "${OP_DIR}/candidate.config.sha256"
    fi
  }
  if [[ "$test_mode" == manual_rollback || "$test_mode" == manual_without_confirmation || "$test_mode" == manual_reobserve_failure || "$test_mode" == manual_profile_failure ]]; then
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
  OP_DIR="${temp_dir}/content-corpus"
  mkdir "$OP_DIR"
  fixture="${OP_DIR}/captured.body"
  # Only this 25-byte token was retained by the incident diagnostic (one
  # backslash per quote). Wrappers below are synthetic, not captured HTML.
  observed='\"digest\":\"$undefined\"'
  [[ "${#observed}" -eq 25 ]] || fail 'referencia de bytes do incidente alterada'
  # Next 15.5.23 installed serializer: undefined -> $undefined, production
  # error -> id:E{digest}; writeFlightDataInstruction JSON.stringify([1, chunk]).
  benign='<script>self.__next_f.push([1,"1:{'${observed}'}\n"])</script>'
  concrete='<script>self.__next_f.push([1,"2:{\"digest\":\"12345\"}\n"])</script>'
  flight_error='<script>self.__next_f.push([1,"2:E{\"digest\":\"12345\"}\n"])</script>'
  home='<h1>Encontre acompanhantes perto de você</h1>'
  catalog='<h1>Anúncios de acompanhantes</h1><p>Explore perfis publicados</p>'
  empty_catalog="${catalog}<p>Nenhum anúncio publicado.</p>"
  corpus_count=0
  # Historical reference ONLY, preserved verbatim for old/new discrimination.
  # All corrected results below come from the actual sourced helper.
  old_content() {
    ! grep -Eq 'NEXT_HTTP_ERROR_FALLBACK|Application error|Internal Server Error|:E\{|\\?"digest\\?"[[:space:]]*:' "$2" || return 1
    grep -F '<h1' "$2" >/dev/null || return 1
    if [[ "$1" == home ]]; then
      grep -F 'Encontre ' "$2" >/dev/null && grep -F 'perto de voc' "$2" >/dev/null || return 1
    else
      grep -F 'Anúncios de acompanhantes' "$2" >/dev/null && grep -F 'Explore perfis publicados' "$2" >/dev/null || return 1
    fi
  }
  curl() {
    local output=
    while (( $# )); do
      case "$1" in --output) output="$2"; shift ;; esac
      shift
    done
    cp -- "$fixture" "$output" || return 23
    printf '%s 0.001' "$http_status"
    return "$transport_rc"
  }
  content_case() {
    local name="$1" kind="$2" payload="$3" expected="$4" old_expected="$5"
    local expected_probe="$expected" old_rc=0 pure_rc=0 precheck_rc=0 probe_rc=0 before
    http_status="${6:-200}" transport_rc="${7:-0}"
    [[ "$http_status" == 200 && "$transport_rc" == 0 ]] || expected_probe=1
    printf '%s' "$payload" > "$fixture"
    before="$(sha256sum "$fixture")"
    old_content "$kind" "$fixture" || old_rc=$?
    _op_validate_content "$kind" "$fixture" || pure_rc=$?
    # Future precheck invocation: fresh shell, no OP_* state, no op_begin/lock.
    bash -eu -c 'source "$1"; _op_validate_content "$2" "$3"' _ "$helper" "$kind" "$fixture" || precheck_rc=$?
    [[ "$(sha256sum "$fixture")" == "$before" ]] || fail 'predicado modificou corpo'
    [[ ! -e "${OP_DIR}/operations" && ! -e "${OP_DIR}/deploy.lock" ]] || fail 'precheck criou estado'
    _op_http_probe http://127.0.0.1:23000/ "$kind" "corpus-$name" || probe_rc=$?
    printf 'CONTENT_CASE name=%s kind=%s old_rc=%s pure_rc=%s precheck_rc=%s probe_rc=%s http=%s transport_rc=%s expected=%s expected_probe=%s\n' \
      "$name" "$kind" "$old_rc" "$pure_rc" "$precheck_rc" "$probe_rc" "$http_status" "$transport_rc" "$expected" "$expected_probe"
    [[ "$old_rc" -eq "$old_expected" && "$pure_rc" -eq "$expected" && "$precheck_rc" -eq "$expected" && "$probe_rc" -eq "$expected_probe" ]] || fail "classificacao divergente: $name"
    corpus_count=$((corpus_count + 1))
  }
  content_case home_plain home "$home" 0 0
  content_case catalog_plain catalog "$catalog" 0 0
  content_case home_incident_format home "$home$benign" 0 1
  content_case catalog_incident_format catalog "$catalog$benign" 0 1
  content_case catalog_empty catalog "$empty_catalog" 0 0
  content_case catalog_empty_benign catalog "$empty_catalog$benign" 0 1
  content_case multiple_benign home "$home$benign$benign" 0 1
  content_case comma_boundaries home "$home"'<script>self.__next_f.push([1,"1:{\"a\":1,\"digest\":\"$undefined\",\"b\":2}\n"])</script>' 0 1
  content_case concrete_digest home "$home$concrete" 1 1
  content_case flight_error home "$home$flight_error" 1 1
  content_case raw_error home "$home"'{"digest":"12345"}' 1 1
  content_case same_line_benign_error home "$home$benign$concrete" 1 1
  content_case same_line_error_benign home "$home$concrete$benign" 1 1
  content_case different_lines_benign_error home "$home$benign"$'\n'"$concrete" 1 1
  content_case different_lines_error_benign home "$home$concrete"$'\n'"$benign" 1 1
  content_case same_push_benign_error home "$home"'<script>self.__next_f.push([1,"1:{\"digest\":\"$undefined\"}\n2:{\"digest\":\"12345\"}\n"])</script>' 1 1
  content_case same_push_error_benign home "$home"'<script>self.__next_f.push([1,"2:{\"digest\":\"12345\"}\n1:{\"digest\":\"$undefined\"}\n"])</script>' 1 1
  content_case same_object_benign_error home "$home"'{\"digest\":\"$undefined\",\"digest\":\"12345\"}' 1 1
  content_case same_object_error_benign home "$home"'{\"digest\":\"12345\",\"digest\":\"$undefined\"}' 1 1
  for marker in NEXT_HTTP_ERROR_FALLBACK 'Application error' 'Internal Server Error' ':E{'; do
    content_case "benign_marker_${corpus_count}" home "$home$benign$marker" 1 1
    content_case "marker_benign_${corpus_count}" home "$home$marker$benign" 1 1
  done
  content_case flight_error_empty home "$home$benign"'<script>self.__next_f.push([1,"2:E{\"digest\":\"\"}\n"])</script>' 1 1
  content_case flight_error_undefined home "$home"'<script>self.__next_f.push([1,"2:E{\"digest\":\"$undefined\"}\n"])</script>' 1 1
  for value in '\"$undefined-extra\"' '\"$$undefined\"' '\"$undefinedX\"' 'null' '\"\"' '\"$unknown\"' '12345' 'undefined'; do
    content_case "non_benign_value_${corpus_count}" home "$home$benign"'{\"digest\":'"$value"'}' 1 1
  done
  content_case raw_undefined_not_observed home "$home"'{"digest":"$undefined"}' 1 1
  # Historical predicate missed this extra layer; the exact exception must not.
  content_case extra_escape_layer home "$home"'{\\\"digest\\\":\\\"$undefined\\\"}' 1 0
  content_case extra_opening_escape home "$home"'{\\\"digest\":\"$undefined\"}' 1 1
  content_case property_without_context home "$home$observed" 1 1
  content_case invalid_value_suffix home "$home"'{\"digest\":\"$undefined\"extra}' 1 1
  content_case truncated_value home "$home"'{\"digest\":\"$undefined' 1 1
  content_case missing_value_boundary home "$home"'{\"digest\":\"$undefined\"' 1 1
  content_case fragmented_value home "$home"'<script>self.__next_f.push([1,"1:{\"digest\":\"$undef"])</script><script>self.__next_f.push([1,"ined\"}\n"])</script>' 1 1
  # JSON.stringify emits compact properties. Synthetic spacing variants are
  # intentionally not evidence to broaden the confirmed compact exception.
  content_case spacing_before_colon home "$home"'{\"digest\" :\"$undefined\"}' 1 1
  content_case spacing_after_colon home "$home"'{\"digest\": \"$undefined\"}' 1 1
  content_case newline_after_colon home "$home"'{\"digest\":'$'\n''\"$undefined\"}' 1 1
  content_case whitespace_outside_script home "$home"$'\n \t'"$benign"$'\n' 0 1
  content_case error_200 home '<h1>Application error</h1>' 1 1
  content_case empty_200 home '' 1 1
  content_case incomplete_200 home '<html><head><title>Loading</title></head><body>' 1 1
  content_case heading_only home '<h1>Loading</h1>' 1 1
  content_case missing_heading home 'Encontre acompanhantes perto de você' 1 1
  content_case missing_home_second home '<h1>Encontre acompanhantes</h1>' 1 1
  content_case missing_catalog_second catalog '<h1>Anúncios de acompanhantes</h1>' 1 1
  content_case trailing_error home "$home$benign"$'\n<footer>End</footer>\n'"$flight_error" 1 1
  for status in 204 301 404 500 503; do
    content_case "http_$status" home "$home$benign" 0 1 "$status"
  done
  content_case timeout_with_partial_healthy_body home "$home$benign" 0 1 200 28
  content_case transport_failure home "$home$benign" 0 1 000 7
  # Actual helper failures, not assertions about text in its source.
  missing_rc=0 directory_rc=0 invalid_kind_rc=0
  _op_validate_content home "${OP_DIR}/missing" || missing_rc=$?
  _op_validate_content home "$OP_DIR" || directory_rc=$?
  _op_validate_content unknown "$fixture" || invalid_kind_rc=$?
  printf 'CONTENT_FAILURE missing_rc=%s directory_rc=%s invalid_kind_rc=%s\n' "$missing_rc" "$directory_rc" "$invalid_kind_rc"
  [[ "$missing_rc" == 1 && "$directory_rc" == 1 && "$invalid_kind_rc" == 2 ]] || fail 'leitura/tipo invalido aceito'
  printf '%s' "$home$benign" > "$fixture"
  http_status=200 transport_rc=0
  awk() { return 2; }
  tool_rc=0 probe_tool_rc=0
  _op_validate_content home "$fixture" || tool_rc=$?
  _op_http_probe http://127.0.0.1:23000/ home corpus-tool-failure || probe_tool_rc=$?
  unset -f awk
  printf 'CONTENT_FAILURE awk_rc=2 pure_rc=%s probe_rc=%s\n' "$tool_rc" "$probe_tool_rc"
  [[ "$tool_rc" == 1 && "$probe_tool_rc" == 1 ]] || fail 'falha da ferramenta foi aceita'
  # Linux non-root final run also proves the actual unreadable-file boundary.
  if [[ "$EUID" -ne 0 ]]; then
    chmod 000 "$fixture"
    unreadable_rc=0
    _op_validate_content home "$fixture" || unreadable_rc=$?
    chmod 600 "$fixture"
    printf 'CONTENT_FAILURE unreadable_rc=%s uid=%s\n' "$unreadable_rc" "$EUID"
    [[ "$unreadable_rc" == 1 ]] || fail 'arquivo ilegivel aceito'
  fi
  for readiness_case in healthy down wrong_app empty; do
    case "$readiness_case" in
      healthy) payload='{"status":"UP","app":"topsdojob-v3-backend"}'; expected=0 ;;
      down) payload='{"status":"DOWN","app":"topsdojob-v3-backend"}'; expected=1 ;;
      wrong_app) payload='{"status":"UP","app":"another-app"}'; expected=1 ;;
      empty) payload=''; expected=1 ;;
    esac
    printf '%s' "$payload" > "$fixture"
    readiness_rc=0
    _op_http_probe http://127.0.0.1:28080/api/health/readiness readiness "corpus-$readiness_case" || readiness_rc=$?
    printf 'READINESS_CASE name=%s probe_rc=%s expected=%s\n' "$readiness_case" "$readiness_rc" "$expected"
    [[ "$readiness_rc" == "$expected" ]] || fail 'classificacao readiness alterada'
  done
  printf 'CONTENT_CORPUS=PASS cases=%s failure_checks=5 unreadable_checked=%s readiness_cases=4 precheck_implementation=shared\n' "$corpus_count" "$((EUID != 0))"
)

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

new_case manual_reobserve_failure
expect_rc 42 bash "$self" --fixture "$test_root" after_failure
assert_result ROLLED_BACK
assert_restore_count 1
expect_rc 1 bash "$self" --fixture "$test_root" manual_reobserve_failure
assert_result INCOMPLETE
assert_runtime "$previous_sha"
assert_restore_count 1
grep -qx original_rc=42 "${test_root}/operations/active.state" || fail 'reobservacao perdeu o primeiro erro'
[[ "$(grep -c "^smoke:$previous_sha$" "${test_root}/events")" == 1 ]] || fail 'reobservacao abriu segunda janela funcional'
echo 'PASS: rollback_terminal_reobservacao_falha_sem_segundo_restauro_ou_janela'

new_case manual_profile_failure
expect_rc 42 bash "$self" --fixture "$test_root" after_failure
assert_result ROLLED_BACK
assert_restore_count 1
rm -- "${test_root}/operations/$(operation_id)/previous.health-profile"
printf '%s\n' "$candidate_sha" > "${test_root}/releases/${previous_sha}/.release-sha"
expect_rc 1 bash "$self" --fixture "$test_root" manual_profile_failure
assert_result INCOMPLETE
assert_runtime "$previous_sha"
assert_restore_count 1
grep -qx original_rc=42 "${test_root}/operations/active.state" || fail 'falha de perfil perdeu primeiro erro'
if grep -q "^smoke:" "${test_root}/events"; then fail 'perfil invalido iniciou janela funcional'; fi
echo 'PASS: rollback_terminal_perfil_invalido_sem_recuperacao_no_exit'

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
    printf '%s|legacy-a60\n' "$previous_sha" > "${OP_DIR}/previous.health-profile"
    printf '%s|main-v1\n' "$candidate_sha" > "${OP_DIR}/candidate.health-profile"
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
      # Extra health routes are exercised separately below; no simulated cost
      # is added here to the historical scheduler's 32/183/300s fixture.
      case "$kind" in backend-health|frontend-health) return 0 ;; esac
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

(
  source "$helper"
  OP_ROOT="${temp_dir}/profiles"
  mkdir -p "${OP_ROOT}/releases/${previous_sha}" "${OP_ROOT}/releases/${candidate_sha}/scripts/deploy"
  printf '%s\n' "$previous_sha" > "${OP_ROOT}/releases/${previous_sha}/.release-sha"
  printf '%s\n' "$candidate_sha" > "${OP_ROOT}/releases/${candidate_sha}/.release-sha"
  cp "$helper" "${OP_ROOT}/releases/${candidate_sha}/scripts/deploy/"
  [[ "$(_op_release_profile "$previous_sha")" == legacy-a60 ]] || fail 'SHA legado nao reconhecido'
  for published_sha in 324b9cd4d62990e5d7e553bdfdfc8c3fe6d6a700 b8a88cd7bb469717f09b5aa05a693e33a903cd72 7c2285d21a2d05c4ad9828734c0daebf9d40dd68 451a6cb90dd1e67c5c774a0618c984d6b73735f8; do
    mkdir -p "${OP_ROOT}/releases/${published_sha}"
    printf '%s\n' "$published_sha" > "${OP_ROOT}/releases/${published_sha}/.release-sha"
    [[ "$(_op_release_profile "$published_sha")" == legacy-a60 ]] || fail 'descendente publicado perdeu contrato de origem'
    printf '%s\n' "$candidate_sha" > "${OP_ROOT}/releases/${published_sha}/.release-sha"
    if _op_release_profile "$published_sha"; then fail 'identidade divergente aceitou perfil legado'; fi
  done
  if _op_legacy_source_sha "$candidate_sha"; then fail 'SHA desconhecido aceitou linhagem legada'; fi
  if _op_release_profile "$candidate_sha"; then fail 'candidata sem endpoints aceitou contrato'; fi
  for endpoint in liveness readiness; do
    mkdir -p "${OP_ROOT}/releases/${candidate_sha}/frontend/src/app/health/${endpoint}"
    printf 'synthetic tracked route\n' > "${OP_ROOT}/releases/${candidate_sha}/frontend/src/app/health/${endpoint}/route.ts"
  done
  [[ "$(_op_release_profile "$candidate_sha")" == main-v1 ]] || fail 'contrato main valido recusado'
  calls="${OP_ROOT}/calls"
  _op_http_probe() { printf '%s\n' "$1" >> "$calls"; [[ "$1" != "$broken" ]]; }
  for broken in none http://127.0.0.1:28080/api/health/readiness http://127.0.0.1:28080/api/health/liveness http://127.0.0.1:23010/health/liveness http://127.0.0.1:23010/health/readiness http://127.0.0.1:23000/health/liveness http://127.0.0.1:23000/health/readiness; do
    : > "$calls"
    profile_rc=0
    _op_smoke_health main-v1 profile-fixture || profile_rc=$?
    if [[ "$broken" == none ]]; then
      [[ "$profile_rc" == 0 && "$(wc -l < "$calls")" == 6 ]] || fail 'main nao sondou todos os endpoints'
    else
      [[ "$profile_rc" == 1 ]] && grep -Fxq "$broken" "$calls" || fail 'DOWN nao bloqueou perfil main'
    fi
  done
  broken=http://127.0.0.1:23010/health/readiness
  : > "$calls"
  _op_smoke_health legacy-a60 legacy-fixture
  [[ "$(cat "$calls")" == http://127.0.0.1:28080/api/health/readiness ]] || fail 'perfil legado usa endpoint inexistente'
)
echo 'PASS: perfil_por_identidade_sem_fallback_e_seis_sondas_obrigatorias'
echo 'PASS: linhagem_publicada_ate_451a6cb9_sem_ampliar_permissoes_de_candidata'

sha256sum "$helper"
echo 'PRODUCTION_OPERATION_PROCESS_TESTS=PASS'
