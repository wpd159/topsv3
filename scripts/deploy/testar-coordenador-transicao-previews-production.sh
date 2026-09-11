#!/usr/bin/env bash
set -euo pipefail

# Scope: the REAL coordinator's control flow, with explicit synthetic supervisor,
# observer, Docker and backfill boundaries. No Docker daemon, database, network,
# real drain, R2 inventory or production runtime is used or proved by this test.
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
self="${script_dir}/$(basename -- "${BASH_SOURCE[0]}")"
coordinator="${script_dir}/coordenar-transicao-previews-production.sh"

fail() { printf 'PREVIEW_COORDINATOR_TESTS_FAIL: %s\n' "$*" >&2; exit 1; }
record() { printf '%s\n' "$*" >> "${FIXTURE_EVENTS}"; }

fixture() {
  local case_dir="$1"
  FIXTURE_CASE_DIR="$case_dir"
  FIXTURE_SCENARIO="$2"
  FIXTURE_EVENTS="${case_dir}/events"
  OP_ROOT="${case_dir}/root"
  OP_ID=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
  OP_CANDIDATE=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
  OP_PREVIOUS_SHA=cccccccccccccccccccccccccccccccccccccccc
  OP_DIR="${OP_ROOT}/operations/${OP_ID}"
  OP_SECRETS="${case_dir}/synthetic-config"
  OP_ACTIVE=1 OP_SNAPSHOT_READY=1 OP_AMBIGUOUS=0 OP_PHASE=PREPARING
  FIXTURE_SOURCE_ID=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  FIXTURE_PLAN_ID=1111111111111111111111111111111111111111111111111111111111111111
  FIXTURE_APPLY_ID=2222222222222222222222222222222222222222222222222222222222222222
  FIXTURE_VALIDATE_ID=3333333333333333333333333333333333333333333333333333333333333333
  FIXTURE_APPLY_SUCCEEDED=0
  local release="${OP_ROOT}/releases/${OP_CANDIDATE}"
  mkdir -p "${release}/scripts/deploy" "${OP_DIR}" "${OP_SECRETS}"
  : > "${FIXTURE_EVENTS}"

  fixture_exit() {
    local original_exit=$?
    trap - EXIT
    printf 'rc=%s\nambiguous=%s\nphase=%s\napply_succeeded=%s\n' \
      "${original_exit}" "${OP_AMBIGUOUS}" "${OP_PHASE}" "${FIXTURE_APPLY_SUCCEEDED}" \
      > "${FIXTURE_CASE_DIR}/state"
    exit "${original_exit}"
  }
  trap fixture_exit EXIT

  # The sourced backfill is a named synthetic boundary, not the real APPLY engine.
  # Its transaction, image and journal contracts have their own independent tests.
  cat > "${release}/scripts/deploy/executar-backfill-previews-production.sh" <<'BACKFILL_BOUNDARY'
preview_backfill_main() {
  local mode="$1" phase="$2" report="${9}" identifier expected_phase
  [[ "$#" -eq 9 && "$3" == "$OP_CANDIDATE" && "$4" == "$OP_ROOT/releases/$OP_CANDIDATE" ]] || return 91
  [[ "$5" == "$OP_SECRETS/production.env" && "$6" == topsv3-production \
     && "$7" == topsv3-production && "$8" == topsv3-production-net ]] || return 91
  case "$mode" in
    PLAN) identifier="$FIXTURE_PLAN_ID"; expected_phase=delta ;;
    APPLY) identifier="$FIXTURE_APPLY_ID"; expected_phase=delta ;;
    VALIDATE) identifier="$FIXTURE_VALIDATE_ID"; expected_phase=final ;;
    *) return 91 ;;
  esac
  [[ "$phase" == "$expected_phase" && "$OP_PHASE" == "PREVIEW_$mode" \
     && "$report" == "$OP_PREVIEW_DIR/${mode,,}" && -d "$report" ]] || return 91
  record "boundary|$mode"
  case "$FIXTURE_SCENARIO:$mode" in
    plan_failure:PLAN) OP_AMBIGUOUS=1; return 41 ;;
    apply_failure:APPLY) OP_AMBIGUOUS=1; return 42 ;;
    validate_failure:VALIDATE) OP_AMBIGUOUS=1; return 43 ;;
  esac
  [[ "$mode" != APPLY ]] || FIXTURE_APPLY_SUCCEEDED=1
  JOB_CONTAINER="synthetic-backfill-${mode,,}"
  printf '{"id":"%s"}\n' "$identifier" > "$report/container-terminal.json"
}
BACKFILL_BOUNDARY

  # All commands with these names resolve to these functions in this process;
  # no real executable/daemon is contacted. Unexpected calls fail closed.
  op_phase() { [[ "$#" -eq 1 ]] || return 91; OP_PHASE="$1"; record "phase|$1"; }
  op_run() {
    local kind="$1" status
    shift
    [[ "$kind" == readonly || "$kind" == mutating ]] || return 91
    record "supervisor|$kind|$1"
    if "$@"; then return 0; else status=$?; fi
    [[ "$kind" != mutating ]] || OP_AMBIGUOUS=1
    return "$status"
  }
  timeout() {
    [[ "$#" -eq 6 && "$1" == --signal=TERM && "$2" == 150s \
       && "$3" == docker && "$4" == stop && "$5" == --time=-1 \
       && "$6" == "$FIXTURE_SOURCE_ID" ]] || return 91
    shift 2
    "$@"
  }
  docker() {
    local identifier role
    case "${1:-}" in
      stop)
        [[ "$#" -eq 3 && "$2" == --time=-1 && "$3" == "$FIXTURE_SOURCE_ID" \
           && "$OP_PHASE" == PREVIEW_DRAINING ]] || return 91
        record 'boundary|STOP'
        [[ "$FIXTURE_SCENARIO" != stop_timeout ]] || return 124
        ;;
      inspect|rm)
        identifier="${2:-}"
        case "$identifier" in
          "$FIXTURE_PLAN_ID") role=PLAN ;;
          "$FIXTURE_APPLY_ID") role=APPLY ;;
          "$FIXTURE_VALIDATE_ID") role=VALIDATE ;;
          *) return 91 ;;
        esac
        if [[ "$1" == inspect ]]; then
          [[ "$#" -eq 4 && "$3" == --format ]] || return 91
          case "$4" in
            '{{index .Config.Labels "topsv3.preview.operation"}}')
              if [[ "$FIXTURE_SCENARIO" == cleanup_foreign_owner && "$role" == PLAN ]]; then
                printf 'foreign-owner\n'
              else printf '%s\n' "$OP_ID"; fi ;;
            '{{.State.Running}}')
              if [[ "$FIXTURE_SCENARIO" == cleanup_running && "$role" == PLAN ]]; then
                printf 'true\n'
              else printf 'false\n'; fi ;;
            *) return 91 ;;
          esac
        else
          [[ "$#" -eq 2 ]] || return 91
          record "boundary|RM_$role"
          [[ "$FIXTURE_SCENARIO" != cleanup_failure ]] || return 46
        fi
        ;;
      *) return 91 ;;
    esac
  }
  python3() {
    local action directory checkpoint identifier
    if [[ "${1:-}" == -c ]]; then
      [[ "$#" -eq 3 && "$2" == 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' ]] || return 91
      [[ -f "$3" && "$3" == "$OP_PREVIEW_DIR/"* ]] || return 91
      identifier="$(sed -n 's/^{"id":"\([a-f0-9]\{64\}\)"}$/\1/p' "$3")"
      [[ "$identifier" =~ ^[a-f0-9]{64}$ ]] || return 91
      printf '%s\n' "$identifier"
      return 0
    fi
    [[ "$1" == "$OP_ROOT/releases/$OP_CANDIDATE/scripts/deploy/validar-transicao-previews-runtime.py" ]] || return 91
    action="$2" directory="$3"
    case "$action" in
      preflight)
        [[ "$#" -eq 4 && "$4" == "$OP_PREVIOUS_SHA" && -d "$directory" ]] || return 91
        case "$directory" in
          "$OP_PREVIEW_DIR/preflight") checkpoint=PREFLIGHT_INITIAL ;;
          "$OP_PREVIEW_DIR/final") checkpoint=PREFLIGHT_FINAL ;;
          *) return 91 ;;
        esac
        record "boundary|$checkpoint"
        [[ "$FIXTURE_SCENARIO:$checkpoint" != initial_preflight_failure:PREFLIGHT_INITIAL ]] || return 31
        [[ "$FIXTURE_SCENARIO:$checkpoint" != final_preflight_failure:PREFLIGHT_FINAL ]] || return 32
        printf '{"id":"%s"}\n' "$FIXTURE_SOURCE_ID" > "$directory/preview-source.json"
        ;;
      stopped)
        [[ "$#" -eq 4 && -n "$4" && -f "$directory/preview-source.json" ]] || return 91
        case "$directory" in
          "$OP_PREVIEW_DIR/final") checkpoint=DRAINED ;;
          "$OP_PREVIEW_DIR/accepted") checkpoint=FINAL_DRAIN ;;
          *) return 91 ;;
        esac
        record "boundary|$checkpoint"
        [[ "$FIXTURE_SCENARIO:$checkpoint" != drain_failure:DRAINED ]] || return 34
        [[ "$FIXTURE_SCENARIO:$checkpoint" != accepted_failure:FINAL_DRAIN ]] || return 45
        printf '{}\n' > "$directory/preview-drained.json"
        ;;
      public)
        [[ "$#" -eq 5 && "$directory" == "$OP_PREVIEW_DIR" \
           && "$4" == "$OP_ROOT/releases/$OP_CANDIDATE/scripts/deploy/validar-previews-publicos-production.sql" \
           && "$5" == synthetic-backfill-validate ]] || return 91
        record 'boundary|PUBLIC'
        [[ "$FIXTURE_SCENARIO" != public_failure ]] || return 44
        printf '{}\n' > "$directory/public-preview-contract.json"
        ;;
      *) return 91 ;;
    esac
  }

  # Never redefine, extract or edit op_preview_*: execute the complete real file.
  source "$coordinator"
  op_preview_preflight
  op_preview_drain_and_reconcile
}

if [[ "${1:-}" == --fixture ]]; then
  [[ "$#" -eq 3 ]] || fail 'invalid fixture arguments'
  fixture "$2" "$3"
  exit 0
fi
[[ "$#" -eq 0 ]] || fail 'no production or external target arguments are accepted'
[[ -f "$coordinator" ]] || fail 'real coordinator missing'
evidence="$(mktemp -d -t topsv3-preview-coordinator.XXXXXXXX)"
coordinator_before="$(sha256sum "$coordinator" | awk '{print $1}')"
stages=(PREFLIGHT_INITIAL PREFLIGHT_FINAL STOP DRAINED PLAN APPLY VALIDATE PUBLIC FINAL_DRAIN RM_PLAN RM_APPLY RM_VALIDATE)
cases=(success initial_preflight_failure final_preflight_failure stop_timeout drain_failure plan_failure apply_failure validate_failure public_failure accepted_failure cleanup_foreign_owner cleanup_running cleanup_failure)
for scenario in "${cases[@]}"; do
  case_dir="${evidence}/${scenario}"
  mkdir "$case_dir"
  set +e
  bash "$self" --fixture "$case_dir" "$scenario" > "$case_dir/output.log" 2>&1
  actual_exit=$?
  set -e
  expected_ambiguity=0 expected_apply=0
  case "$scenario" in
    success) expected_exit=0; count=12; expected_apply=1 ;;
    initial_preflight_failure) expected_exit=31; count=1 ;;
    final_preflight_failure) expected_exit=32; count=2 ;;
    stop_timeout) expected_exit=124; count=3; expected_ambiguity=1 ;;
    drain_failure) expected_exit=34; count=4 ;;
    plan_failure) expected_exit=41; count=5; expected_ambiguity=1 ;;
    apply_failure) expected_exit=42; count=6; expected_ambiguity=1 ;;
    validate_failure) expected_exit=43; count=7; expected_ambiguity=1; expected_apply=1 ;;
    public_failure) expected_exit=44; count=8; expected_apply=1 ;;
    accepted_failure) expected_exit=45; count=9; expected_apply=1 ;;
    cleanup_foreign_owner|cleanup_running) expected_exit=1; count=9; expected_apply=1 ;;
    cleanup_failure) expected_exit=46; count=10; expected_ambiguity=1; expected_apply=1 ;;
  esac
  [[ "$actual_exit" -eq "$expected_exit" ]] || { cat "$case_dir/output.log" >&2; fail "$scenario exit $actual_exit expected $expected_exit"; }
  [[ -f "$case_dir/state" ]] || fail "$scenario lost final state"
  grep -qx "rc=$expected_exit" "$case_dir/state" || fail "$scenario lost original rc"
  grep -qx "ambiguous=$expected_ambiguity" "$case_dir/state" || fail "$scenario erased ambiguity"
  grep -qx "apply_succeeded=$expected_apply" "$case_dir/state" || fail "$scenario rewrote prior APPLY result"
  printf '%s\n' "${stages[@]:0:count}" > "$case_dir/expected-boundaries"
  sed -n 's/^boundary|//p' "$case_dir/events" > "$case_dir/actual-boundaries"
  cmp -s "$case_dir/expected-boundaries" "$case_dir/actual-boundaries" || fail "$scenario continued, omitted or reordered a boundary"
  : > "$case_dir/expected-events"
  for stage in "${stages[@]:0:count}"; do
    case "$stage" in
      PREFLIGHT_INITIAL) printf 'phase|PREVIEW_PREFLIGHT\nsupervisor|readonly|python3\n' ;;
      PREFLIGHT_FINAL|DRAINED|FINAL_DRAIN) printf 'supervisor|readonly|python3\n' ;;
      STOP) printf 'phase|PREVIEW_DRAINING\nsupervisor|mutating|timeout\n' ;;
      PLAN|APPLY|VALIDATE) printf 'phase|PREVIEW_%s\n' "$stage" ;;
      PUBLIC) printf 'phase|PREVIEW_PUBLIC_CONTRACT\nsupervisor|readonly|python3\n' ;;
      RM_*) printf 'supervisor|mutating|docker\n' ;;
    esac
    printf 'boundary|%s\n' "$stage"
  done > "$case_dir/expected-events"
  cmp -s "$case_dir/expected-events" "$case_dir/events" || fail "$scenario bypassed or changed supervisor kind/order"
  apply_calls="$(grep -c '^boundary|APPLY$' "$case_dir/events" || true)"
  [[ "$apply_calls" -le 1 ]] || fail "$scenario repeated APPLY"
  if [[ "$scenario" == success ]]; then
    grep -qx 'PREVIEW_TRANSITION_READY=PASS' "$case_dir/output.log" || fail 'success marker missing'
  elif grep -q '^PREVIEW_TRANSITION_READY=PASS$' "$case_dir/output.log"; then
    fail "$scenario accepted a failed transition"
  fi
  printf 'PASS: coordinator_%s original_rc=%s apply_calls=%s ambiguity=%s\n' \
    "$scenario" "$actual_exit" "$apply_calls" "$expected_ambiguity"
done
[[ "$(sha256sum "$coordinator" | awk '{print $1}')" == "$coordinator_before" ]] || fail 'coordinator changed during tests'
printf 'PREVIEW_COORDINATOR_TEST_EVIDENCE=%s\n' "$evidence"
printf 'PREVIEW_COORDINATOR_TESTS=PASS cases=%s scope=real_coordinator_synthetic_boundaries no_real_runtime_claim=true\n' "${#cases[@]}"
