#!/usr/bin/env bash
set -euo pipefail

# Real JVM/Docker/PostgreSQL fixture, invoked by the existing Java integration
# test on a Linux HOST. The observer is never a container or root. The canonical
# production entry prechecks remain covered by the isolated shell suite; here
# the real execution/observation/cleanup functions share a private fixture lock.
[[ $# -eq 4 && "$(uname -s)" == Linux && "$(id -u)" -ne 0 ]]
[[ -z "${DOCKER_HOST:-}" && -z "${DOCKER_CONTEXT:-}" ]]
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fixture="$(readlink -e "$1")"; network="$2"; physical="$3"; sha="$4"
[[ "$fixture" == "$repo"/backend/target/preview-job-user-* && ! -L "$1" ]]
[[ "$network" =~ ^[a-f0-9]{64}$ && "$physical" =~ ^sha256:[a-f0-9]{64}$ && "$sha" =~ ^[a-f0-9]{40}$ ]]
docker context inspect | python3 -c 'import json,sys; assert json.load(sys.stdin)[0]["Endpoints"]["docker"]["Host"].startswith("unix://")'
[[ "$(docker image inspect "$physical" --format '{{.Id}}')" == "$physical" ]]
owner="$(cat /proc/sys/kernel/random/uuid)"
prefix="topsv3-preview-uid-${owner:0:8}"
reference="$prefix-backend:$sha"
root="$fixture/operation"; release="$root/releases/$sha"
evidence="$fixture/evidence"
helper="$repo/scripts/deploy/executar-backfill-previews-production.sh"
observer="$repo/scripts/deploy/validar-transicao-previews-runtime.py"
protector="$repo/scripts/deploy/proteger-operacao-production.sh"
tag_created=0
[[ -d "$evidence" ]]
mkdir -m 0700 "$root" "$root/releases" "$release" "$root/operations" "$fixture/secrets"
ln -s "$release" "$root/current"

cleanup_fixture() {
  local original=$? cleanup=0 identifier identity remaining
  trap - EXIT
  set +e
  # Only this UUID, exact IDs and terminal jobs. Failure is not absence and is
  # never converted into success. Private evidence/pending stays on any error.
  remaining="$(docker ps -aq --no-trunc --filter "label=topsv3.preview.operation=$owner")"
  if [[ $? -ne 0 ]]; then cleanup=1
  else
    for identifier in $remaining; do
      identity="$(docker inspect "$identifier" --format '{{.Id}} {{index .Config.Labels "topsv3.preview.operation"}} {{.State.Running}}')"
      if [[ $? -ne 0 || "$identity" != "$identifier $owner false" ]]; then cleanup=1; continue; fi
      docker rm "$identifier" >/dev/null || cleanup=1
    done
  fi
  remaining="$(docker ps -aq --no-trunc --filter "label=topsv3.preview.operation=$owner")"
  [[ $? -eq 0 && -z "$remaining" ]] || cleanup=1
  if [[ "$tag_created" == 1 ]]; then
    identity="$(docker image inspect "$reference" --format '{{.Id}}')"
    if [[ $? -eq 0 && "$identity" == "$physical" ]]; then
      docker image rm --no-prune "$reference" >/dev/null || cleanup=1
    else cleanup=1; fi
  fi
  printf 'PREVIEW_JOB_USER_FIXTURE_CLEANUP original_exit=%s cleanup_exit=%s owner=%s\n' "$original" "$cleanup" "$owner" | tee "$evidence/cleanup.log"
  local -a receipt_status=("${PIPESTATUS[@]}")
  [[ "${receipt_status[0]}" -eq 0 && "${receipt_status[1]}" -eq 0 ]] || cleanup=1
  [[ "$original" -ne 0 || "$cleanup" -eq 0 ]] || original=1
  exit "$original"
}
trap cleanup_fixture EXIT
existing_reference="$(docker image ls --quiet "$reference")"
[[ -z "$existing_reference" ]]
docker tag "$physical" "$reference"
tag_created=1

# Synthetic configuration is born private and remains read-only in the job.
# No production secret, application image or permanent service is modified.
python3 - "$fixture" "$release" "$reference" "$network" "$repo" <<'CONFIG'
import json, os, pathlib, sys
folder, release, reference, network, repo = sys.argv[1:]
root = pathlib.Path(folder)
assert root.stat().st_uid == os.geteuid() and os.geteuid() != 0
def private(path, payload):
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as handle:
        handle.write(payload)
for name in ('application.properties', 'inventory.keys'):
    metadata = (root / 'data' / name).stat()
    assert (metadata.st_uid, metadata.st_gid, metadata.st_mode & 0o7777) == (os.geteuid(), os.getegid(), 0o600)
paths = (root / 'classpath.txt').read_text().splitlines()
assert paths and all(pathlib.Path(item).is_absolute() and pathlib.Path(item).exists() for item in paths)
mounts = [str(root / 'data') + ':/fixture-data:ro']
mounts += [item + ':' + item + ':ro' for item in paths]
service = {'image': reference, 'restart': 'no',
    'entrypoint': ['java', '-cp', ':'.join(paths),
        'br.com.topsdojob.v3.application.operacional.midia.backfill.PreviewBackfillJobUserFixtureMain'],
    'environment': {'PREVIEW_JOB_FIXTURE_DATA': '/fixture-data'},
    'volumes': mounts, 'networks': ['fixture']}
target = pathlib.Path(release) / 'deploy' / 'production'
target.mkdir(parents=True)
private(target / 'docker-compose.yml', json.dumps({'services': {'backend': service},
    'networks': {'fixture': {'external': True, 'name': network}}}))
scripts = pathlib.Path(release) / 'scripts' / 'deploy'
scripts.mkdir(parents=True)
for name in ('validar-transicao-previews-runtime.py',):
    (scripts / name).symlink_to(pathlib.Path(repo) / 'scripts' / 'deploy' / name)
private(root / 'secrets' / 'production.env', '# synthetic fixture only\n')
CONFIG

run_phase() (
  source "$helper"
  source "$protector"
  MODE="$1" PHASE=initial RELEASE_SHA="$sha" RELEASE_DIR="${3:-$release}"
  phase_observer="${4:-$observer}"
  ENV_FILE="$fixture/secrets/production.env" COMPOSE_PROJECT="$prefix" APP_PREFIX="$prefix" APP_NETWORK="$network"
  COMPOSE_FILE="$release/deploy/production/docker-compose.yml"
  REPORT_DIR="$fixture/${2:-${MODE,,}}"
  install -d -m 0700 "$REPORT_DIR"
  JOB_CONTAINER="$prefix-${2:-${MODE,,}}" JOB_OWNER="$owner" JOB_VERIFIED=0 PENDING_FILE=
  OP_ACTIVE=0 BATCH_SIZE=1
  TOPSV3_PREVIEW_BACKFILL_IMAGE_ID="$physical"
  TOPSV3_PREVIEW_BACKFILL_CONFIRM="APPLY:$sha"
  CONTAINER_REPORT_PATH="/run/topsv3-preview-backfill/initial-${MODE,,}.tsv"
  RAW_OUTPUT="$(mktemp)" RESULT_LINE=
  trap on_exit EXIT
  _op_lock "$root"
  rc=0
  run_backfill || rc=$?
  python3 "$phase_observer" outcome "$REPORT_DIR" "$JOB_CONTAINER" "$owner" "$MODE" "initial-${MODE,,}.tsv.state.jsonl"
  [[ "$rc" -eq 0 ]] || exit "$rc"
  validate_result
  if [[ "$MODE" == APPLY && "$REPORT_DIR" == "$fixture/apply" ]]; then
    [[ "$(result_value db_updated)" == 2 ]]
  fi
  python3 "$phase_observer" terminal "$REPORT_DIR" "$JOB_CONTAINER" "$owner" "$MODE" "initial-${MODE,,}.tsv.state.jsonl"
  python3 - "$REPORT_DIR" "$MODE" "$root/operations/preview-backfill.pending" "$owner" <<'PROOF'
import hashlib, json, os, pathlib, sys
folder, mode, pending, owner = sys.argv[1:]
root = pathlib.Path(folder)
assert pathlib.Path(pending).read_text().splitlines()[0] == owner
outcome = json.loads((root / 'outcome.json').read_text())
terminal = json.loads((root / 'container-terminal.json').read_text())
assert outcome['identity_verified'] and outcome['terminal'] and not outcome['retry_allowed']
assert outcome['commit'] == terminal['commit'] == ('COMMITTED' if mode == 'APPLY' else 'NOT_STARTED')
if mode == 'APPLY':
    capture = root / 'initial-apply.tsv.before.json'
    metadata = capture.stat()
    assert (metadata.st_uid, metadata.st_gid, metadata.st_mode & 0o7777) == (os.geteuid(), os.getegid(), 0o600)
    checksum = hashlib.sha256(capture.read_bytes()).hexdigest()
    assert checksum == outcome['before_sha256']
    print(f'JVM_CAPTURE_OWNER=PASS uid={metadata.st_uid} gid={metadata.st_gid} mode=0600 sha256={checksum}')
print(f'JVM_TERMINAL=PASS mode={mode} commit={terminal["commit"]} pending_until_cleanup=true')
PROOF
  docker logs "$JOB_CONTAINER" 2>&1 | python3 -c '
import os,sys
lines=sys.stdin.read().splitlines()
for name, expected in (("Uid:", str(os.geteuid())), ("Gid:", str(os.getegid()))):
    values=[line.split()[2:] for line in lines if line.startswith("PREVIEW_JOB_FIXTURE_PROCESS " + name)]
    assert len(values)==1 and values[0]==[expected]*4
assert "PREVIEW_JOB_FIXTURE_PROCESS CapEff:\t0000000000000000" in lines
assert "PREVIEW_JOB_FIXTURE_CONFIGURATION_READ=PASS" in lines
print("JVM_CONFIGURATION_AND_IDENTITY=PASS uid="+str(os.geteuid())+" gid="+str(os.getegid())+" capabilities=0")'
  JOB_VERIFIED=1
)
for mode in PLAN APPLY VALIDATE; do
  run_phase "$mode" >"$evidence/${mode,,}.log" 2>&1
  [[ ! -e "$root/operations/preview-backfill.pending" ]]
  remaining="$(docker ps -aq --filter "label=topsv3.preview.operation=$owner")"
  [[ -z "$remaining" ]]
  cat "$evidence/${mode,,}.log"
done

# The resolved Docker Compose configuration, not a mocked dictionary, must
# reject a user differing from the non-root observer before any job starts.
python3 - "$fixture" "$release" "$observer" "$physical" <<'DIVERGENCE'
import importlib.util, json, os, pathlib, subprocess, sys
folder, release, observer, physical = sys.argv[1:]
spec = importlib.util.spec_from_file_location('runtime', observer)
runtime = importlib.util.module_from_spec(spec); spec.loader.exec_module(runtime)
root = pathlib.Path(folder)
bad = root / 'divergent.compose.json'
with os.fdopen(os.open(bad, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as handle:
    json.dump({'services': {'backend': {'user': '0:0'}}}, handle)
config = subprocess.run(['docker','compose','-f',release+'/deploy/production/docker-compose.yml',
    '-f',str(root/'apply'/'pinned-backfill.compose.yml'),'-f',str(bad),'config','--format','json'],
    capture_output=True, text=True, check=True)
try:
    runtime.validate_pin(json.loads(config.stdout), physical, runtime.executor_user())
except RuntimeError as error:
    assert str(error) == 'backfill_executor_identity_mismatch'
else:
    raise AssertionError('divergent runtime user accepted')
assert not (root/'operation'/'operations'/'preview-backfill.pending').exists()
print('JVM_USER_DIVERGENCE=REJECTED before_run=true')
DIVERGENCE

# Regression of the original root image behavior: a REAL runner creates the
# private file. This intentional negative JOB is root; the observer stays the
# original unprivileged host user. No privileged read or chmod/chown repair.
regression="$fixture/root-regression"
root_name="$prefix-root-regression"
legacy_release="$fixture/legacy-release"
mkdir -m 0700 "$legacy_release" "$legacy_release/scripts" "$legacy_release/scripts/deploy"
legacy_observer="$legacy_release/scripts/deploy/validar-transicao-previews-runtime.py"
git -C "$repo" show ed57ed45d76e8360c25375fa32320e520e8b62d9:scripts/deploy/validar-transicao-previews-runtime.py >"$legacy_observer"
set +e
run_phase APPLY root-regression "$legacy_release" "$legacy_observer" >"$evidence/root-regression.log" 2>&1
legacy_exit=$?
set -e
[[ "$legacy_exit" -ne 0 ]]
printf 'LEGACY_OBSERVER_EXIT=%s\n' "$legacy_exit"
python3 - "$regression" "$root_name" "$observer" "$owner" <<'ROOT_REGRESSION'
import importlib.util, json, os, pathlib, subprocess, sys
folder, name, observer, owner = sys.argv[1:]
assert os.geteuid() != 0
root = pathlib.Path(folder)
capture = root/'initial-apply.tsv.before.json'
metadata = capture.stat()
assert metadata.st_uid == 0 and metadata.st_mode & 0o7777 == 0o600
try:
    capture.read_bytes()
except PermissionError:
    pass
else:
    raise AssertionError('non-root observer read root private capture')
events = [json.loads(line) for line in (root/'initial-apply.tsv.state.jsonl').read_text().splitlines()]
assert events[-1]['stage'] == 'SUCCEEDED' and events[-1]['commit'] == 'COMMITTED'
proof = json.loads((root/'outcome.json').read_text())
assert proof['commit'] == 'UNKNOWN' and proof['retry_allowed'] is False
assert proof['reason'] == 'observation_failed'
spec = importlib.util.spec_from_file_location('runtime', observer)
runtime = importlib.util.module_from_spec(spec); spec.loader.exec_module(runtime)
try:
    runtime.owned_job(folder, name, owner)
except RuntimeError as error:
    assert str(error) == 'backfill_executor_identity_mismatch'
else:
    raise AssertionError('real root job accepted as matching observer')
pending = root.parent/'operation'/'operations'/'preview-backfill.pending'
assert pending.read_text().splitlines()[0] == owner
print('JVM_ROOT_REGRESSION=PASS capture_uid=0 mode=0600 read=PermissionError journal=COMMITTED outcome=UNKNOWN retry=false pending=preserved')
ROOT_REGRESSION
set +e
(source "$protector"; _op_lock "$root") >"$evidence/pending-refusal.log" 2>&1
pending_exit=$?
set -e
[[ "$pending_exit" -eq 76 ]]
printf 'JVM_PENDING_REENTRY=REJECTED exit=%s\n' "$pending_exit"
printf 'PREVIEW_JOB_USER_INTEGRATION=PASS observer_uid=%s observer_gid=%s socket_in_container=false\n' "$(id -u)" "$(id -g)" | tee "$evidence/result.log"
