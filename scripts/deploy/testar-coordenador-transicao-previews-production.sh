#!/usr/bin/env bash
set -euo pipefail
# Real coordinator; explicitly synthetic supervisor, runner, observer and Docker.
# No database, storage, service activation or production runtime is exercised.
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
self="${script_dir}/$(basename -- "${BASH_SOURCE[0]}")"
coordinator="${script_dir}/coordenar-transicao-previews-production.sh"
fail() { printf 'PREVIEW_COORDINATOR_TESTS_FAIL: %s\n' "$*" >&2; exit 1; }
record() { printf '%s\n' "$*" >> "$FIXTURE_EVENTS"; }

fixture() {
  local case_dir="$1" scenario="$2" release
  FIXTURE_CASE_DIR="$case_dir"
  FIXTURE_SCENARIO="$scenario" FIXTURE_EVENTS="$case_dir/events"
  OP_ROOT="$case_dir/root" OP_ID=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
  OP_CANDIDATE=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
  OP_DIR="$OP_ROOT/operations/$OP_ID" OP_SECRETS="$case_dir/config"
  OP_ACTIVE=1 OP_SNAPSHOT_READY=1 OP_AMBIGUOUS=0 OP_CANCEL_RC=0 OP_CHILD_PID= OP_PHASE=BUILDING
  FIXTURE_ID=3333333333333333333333333333333333333333333333333333333333333333
  FIXTURE_JOB=0
  release="$OP_ROOT/releases/$OP_CANDIDATE"
  mkdir -p "$release/scripts/deploy" "$OP_DIR" "$OP_SECRETS"
  : > "$FIXTURE_EVENTS"
  printf 'backend\tsynthetic-physical-image\tsynthetic-reference\n' > "$OP_DIR/candidate.images.tsv"
  fixture_exit() {
    local rc=$?
    trap - EXIT
    printf 'rc=%s\nambiguous=%s\njob=%s\n' "$rc" "$OP_AMBIGUOUS" "$FIXTURE_JOB" > "$FIXTURE_CASE_DIR/state"
    exit "$rc"
  }
  trap fixture_exit EXIT
  case "$scenario" in
    pending) printf 'preserved-owner\n' > "$OP_ROOT/operations/preview-backfill.pending" ;;
    pending_link) ln -s "$case_dir/absent" "$OP_ROOT/operations/preview-backfill.pending" ;;
  esac

  # Boundary of the Java runner; no redefinition of the versioned coordinator.
  cat > "$release/scripts/deploy/executar-backfill-previews-production.sh" <<'RUNNER'
preview_backfill_main() {
  [[ "$#" -eq 9 && "$1" == VALIDATE && "$2" == final && "$3" == "$OP_CANDIDATE" \
    && "$4" == "$OP_ROOT/releases/$OP_CANDIDATE" && "$5" == "$OP_SECRETS/production.env" \
    && "$6" == topsv3-production && "$7" == topsv3-production && "$8" == topsv3-production-net \
    && "$9" == "$OP_PREVIEW_DIR/validate" && "$OP_PHASE" == PREVIEW_VALIDATE ]] || return 91
  record 'boundary|VALIDATE'
  [[ "$FIXTURE_SCENARIO" != execution_failure ]] || return 42
  FIXTURE_JOB=1
  JOB_CONTAINER="topsv3-preview-${OP_CANDIDATE:0:12}-final-validate"
  case "$FIXTURE_SCENARIO" in
    missing_metadata|inconsistent_metadata|validation_and_cleanup_failure) return 43 ;;
  esac
}
RUNNER
  op_phase() { OP_PHASE="$1"; record "phase|$1"; }
  _op_state() { record "state|ambiguous=$OP_AMBIGUOUS"; }
  op_run() {
    local kind="$1" rc
    shift
    [[ "$kind" == readonly || "$kind" == mutating ]] || return 91
    record "supervisor|$kind|$1"
    if "$@"; then return 0; else rc=$?; fi
    [[ "$kind" != mutating ]] || OP_AMBIGUOUS=1
    return "$rc"
  }
  docker() {
    [[ "$#" -eq 2 && "$1" == rm && "$2" == "$FIXTURE_ID" && "$FIXTURE_JOB" -eq 1 ]] || return 91
    record 'boundary|RM'
    case "$FIXTURE_SCENARIO" in cleanup_failure|validation_and_cleanup_failure) return 46 ;; esac
    FIXTURE_JOB=0
  }
  python3() {
    local action report
    if [[ "$1" == -c ]]; then
      [[ "$#" -eq 3 && "$3" == "$OP_PREVIEW_DIR/validate/cleanup-proof.json" && -f "$3" ]] || return 91
      cat "$3" # Synthetic observer receipt, not JSON parsing under test here.
      return 0
    fi
    [[ "$1" == "$OP_PREVIEW_HELPER" ]] || return 91
    action="$2" report="$3"
    case "$action" in
      public)
        [[ "$#" -eq 5 && "$report" == "$OP_PREVIEW_DIR" && "$5" == "$JOB_CONTAINER" \
          && "$4" == "$OP_ROOT/releases/$OP_CANDIDATE/scripts/deploy/validar-previews-publicos-production.sql" ]] || return 91
        record 'boundary|PUBLIC'
        [[ "$FIXTURE_SCENARIO" != public_failure ]] || return 44
        [[ "$FIXTURE_SCENARIO" != unknown_result ]] || return 45
        ;;
      cleanup-proof)
        [[ "$#" -eq 5 && "$report" == "$OP_PREVIEW_DIR/validate" && "$4" == "$JOB_CONTAINER" && "$5" == "$OP_ID" ]] || return 91
        record 'boundary|CLEANUP_PROOF'
        case "$FIXTURE_SCENARIO" in foreign_owner|running_job|observation_failure) return 53 ;; esac
        if [[ "$FIXTURE_JOB" -eq 1 ]]; then
          printf 'REMOVE|%s\n' "$FIXTURE_ID" > "$report/cleanup-proof.json"
        else
          printf 'ABSENT|\n' > "$report/cleanup-proof.json"
        fi
        ;;
      *) return 91 ;;
    esac
  }
  source "$coordinator"
  op_preview_validate_before_activation
  # The workflow's next phase, not an actual activation or replacement service.
  op_phase ACTIVATING
  record 'boundary|ACTIVATING'
}

if [[ "${1:-}" == --fixture ]]; then
  fixture "$2" "$3"
  exit 0
fi
[[ "$#" -eq 0 ]] || fail 'no external target arguments accepted'
evidence="$(mktemp -d -t topsv3-preview-coordinator.XXXXXXXX)"
original="$(sha256sum "$coordinator")"
cases=(success missing_metadata inconsistent_metadata execution_failure public_failure unknown_result pending pending_link foreign_owner running_job observation_failure cleanup_failure validation_and_cleanup_failure)
for scenario in "${cases[@]}"; do
  case_dir="$evidence/$scenario"
  mkdir "$case_dir"
  rc=0
  bash "$self" --fixture "$case_dir" "$scenario" > "$case_dir/output.log" 2>&1 || rc=$?
  ambiguity=0 job=0
  case "$scenario" in
    success) expected=0; stages=$'VALIDATE\nPUBLIC\nCLEANUP_PROOF\nRM\nACTIVATING' ;;
    missing_metadata|inconsistent_metadata) expected=43; stages=$'VALIDATE\nCLEANUP_PROOF\nRM' ;;
    execution_failure) expected=42; stages=$'VALIDATE\nCLEANUP_PROOF' ;;
    public_failure) expected=44; stages=$'VALIDATE\nPUBLIC\nCLEANUP_PROOF\nRM' ;;
    unknown_result) expected=45; stages=$'VALIDATE\nPUBLIC\nCLEANUP_PROOF\nRM' ;;
    pending|pending_link) expected=76; stages= ;;
    foreign_owner|running_job|observation_failure) expected=53; ambiguity=1; job=1; stages=$'VALIDATE\nPUBLIC\nCLEANUP_PROOF' ;;
    cleanup_failure) expected=46; ambiguity=1; job=1; stages=$'VALIDATE\nPUBLIC\nCLEANUP_PROOF\nRM' ;;
    validation_and_cleanup_failure) expected=43; ambiguity=1; job=1; stages=$'VALIDATE\nCLEANUP_PROOF\nRM' ;;
  esac
  [[ "$rc" -eq "$expected" ]] || { cat "$case_dir/output.log" >&2; fail "$scenario exit=$rc expected=$expected"; }
  grep -qx "rc=$expected" "$case_dir/state"
  grep -qx "ambiguous=$ambiguity" "$case_dir/state"
  grep -qx "job=$job" "$case_dir/state"
  [[ "$(sed -n 's/^boundary|//p' "$case_dir/events")" == "$stages" ]] || fail "$scenario reordered or skipped boundary"
  ! grep -Eq '^boundary\|(APPLY|PLAN|STOP|DRAIN)' "$case_dir/events" || fail "$scenario invoked mutation/drain"
  if [[ "$expected" -eq 0 ]]; then
    grep -qx 'PREVIEW_TRANSITION_READY=PASS readonly=true' "$case_dir/output.log"
  else
    ! grep -q 'PREVIEW_TRANSITION_READY=PASS' "$case_dir/output.log"
    ! grep -q 'phase|ACTIVATING' "$case_dir/events"
  fi
  if [[ "$scenario" == pending ]]; then
    grep -qx 'preserved-owner' "$case_dir/root/operations/preview-backfill.pending"
  fi
  # Real coordinator must supervise all potentially mutating Docker cleanup.
  if grep -q '^boundary|RM$' "$case_dir/events"; then
    grep -qx 'supervisor|mutating|docker' "$case_dir/events"
  fi
  printf 'PASS: coordinator_%s original_rc=%s activation=%s\n' "$scenario" "$rc" "$([[ "$rc" -eq 0 ]] && echo allowed || echo blocked)"
done
[[ "$(sha256sum "$coordinator")" == "$original" ]] || fail 'coordinator changed during tests'
printf 'PREVIEW_COORDINATOR_TEST_EVIDENCE=%s\n' "$evidence"
printf 'PREVIEW_COORDINATOR_TESTS=PASS cases=%s scope=real_coordinator_synthetic_boundaries no_real_runtime_claim=true\n' "${#cases[@]}"
