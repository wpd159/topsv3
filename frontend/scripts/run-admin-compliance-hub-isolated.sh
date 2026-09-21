#!/usr/bin/env bash
set -euo pipefail

# F02 alone needs same-origin fixtures without browser request interception.
# Build, Next, HTTP fixtures and pinned Chromium headless share no egress.
if [[ "${1:-}" == --inside ]]; then
  test "$(id -u)" -ne 0
  test "$(node --version)" = v22.13.1
  export NEXT_PUBLIC_SITE_URL=https://example.invalid
  export NEXT_PUBLIC_API_URL=/api/public
  export NEXT_PUBLIC_LOGO_URL=/logo-finallllll.webp
  export INTERNAL_API_URL=http://127.0.0.1:9/api/public
  export NEXT_PUBLIC_ANALYTICS_ENABLED=false
  export NEXT_PUBLIC_FORCE_HTTPS=true
  export NEXT_PUBLIC_EFI_PIX_ENABLED=false
  export SEARCH_INDEXING_MODE=blocked
  export PUBLIC_API_TIMEOUT_MS=5000
  export NEXT_TELEMETRY_DISABLED=1
  export TOPS_F02_ISOLATED=1
  case "${TOPS_F02_PLAYWRIGHT_PACKAGE:-playwright}" in
    playwright|playwright-core) export TOPS_PLAYWRIGHT_MODULE="/tools/node_modules/${TOPS_F02_PLAYWRIGHT_PACKAGE:-playwright}" ;;
    *) printf 'Unexpected Playwright package\n' >&2; exit 1 ;;
  esac
  export TOPS_UI_EVIDENCE_ROOT=/evidence
  unset TOPS_UI_NEXT_DEV TOPS_UI_BROWSER_CHANNEL NODE_OPTIONS NODE_PATH
  node --input-type=module <<'NODE'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { spawnSync } from 'node:child_process'

assert.equal(process.platform, 'linux')
assert.notEqual(process.getuid(), 0)
assert.equal(process.versions.node, '22.13.1')
assert.ok(Object.values(os.networkInterfaces()).flat().every((entry) => entry.internal), 'F02 requires loopback-only networking')
assert.ok(!fs.readFileSync('/proc/net/route', 'utf8').trim().split('\n').slice(1).some((line) => line.trim().split(/\s+/)[1] === '00000000'), 'F02 forbids an external default route')

const source = '/source', workspace = '/work/frontend'
const workspaceMarker = '/work/.tops-f02-workspace'
if (!fs.existsSync(workspaceMarker)) {
  assert.equal(fs.readdirSync('/work').length, 0, 'F02 refuses a nonempty unowned workspace')
  fs.writeFileSync(workspaceMarker, 'isolated-admin-compliance-hub\n', { flag: 'wx', mode: 0o600 })
}
assert.equal(fs.readFileSync(workspaceMarker, 'utf8'), 'isolated-admin-compliance-hub\n')
const excluded = new Set(['node_modules', '.next', '.git', '.env', '.env.local', '.env.production', '.env.production.local', 'tsconfig.tsbuildinfo'])
const copyFilter = (name) => !path.relative(source, name).split(path.sep).some((part) => excluded.has(part) || part.startsWith('.env.'))
const sha256 = (data) => createHash('sha256').update(data).digest('hex')
const inputHashes = {}
function collect(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    const absolute = path.join(directory, entry.name)
    const relative = path.relative(source, absolute).split(path.sep).join('/')
    if (!copyFilter(absolute) || relative === 'scripts' || relative === '.f02-build.json') continue
    if (entry.isDirectory()) collect(absolute)
    else if (entry.isFile()) inputHashes[relative] = sha256(fs.readFileSync(absolute))
    else assert.fail('Unexpected build-input symlink: ' + relative)
  }
}
collect(source)
const identity = { node: process.versions.node, apiBase: process.env.NEXT_PUBLIC_API_URL, logo: process.env.NEXT_PUBLIC_LOGO_URL, site: process.env.NEXT_PUBLIC_SITE_URL, inputHashes }
const manifestPath = path.join(workspace, '.f02-build.json')
const buildIdPath = path.join(workspace, '.next/BUILD_ID')
let previous
try { previous = JSON.parse(fs.readFileSync(manifestPath, 'utf8')) } catch {}
const previousIdentity = previous && { node: previous.node, apiBase: previous.apiBase, logo: previous.logo, site: previous.site, inputHashes: previous.inputHashes }
const reusable = previous && JSON.stringify(previousIdentity) === JSON.stringify(identity) && fs.existsSync(buildIdPath) && previous.buildId === fs.readFileSync(buildIdPath, 'utf8').trim()
fs.mkdirSync(workspace, { recursive: true })
// Remove only files captured in the previous private build that no longer exist
// in the current input tree. A stale source must not leak into a new build.
for (const oldInput of Object.keys(previous?.inputHashes || {})) {
  if (Object.hasOwn(inputHashes, oldInput)) continue
  const target = path.resolve(workspace, oldInput)
  assert.ok(target.startsWith(workspace + path.sep), 'Invalid previous build input')
  fs.rmSync(target, { force: true })
}
fs.cpSync(source, workspace, { recursive: true, filter: copyFilter })
if (!fs.existsSync(path.join(workspace, 'node_modules'))) fs.symlinkSync('/deps/node_modules', path.join(workspace, 'node_modules'))
if (!reusable) {
  // This directory is exclusively the disposable F02 build, never the CI build.
  fs.rmSync(path.join(workspace, '.next'), { recursive: true, force: true })
  const log = fs.openSync('/evidence/f02-build.log', 'wx')
  let build
  try { build = spawnSync(process.execPath, ['node_modules/next/dist/bin/next', 'build'], { cwd: workspace, env: process.env, stdio: ['ignore', log, log] }) }
  finally { fs.closeSync(log) }
  if (build.error) throw build.error
  if (build.status !== 0) { console.error('F02 build failed; see f02-build.log', build.signal || build.status); process.exit(build.status || 1) }
  const manifest = { ...identity, buildId: fs.readFileSync(buildIdPath, 'utf8').trim() }
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2))
}
fs.copyFileSync(manifestPath, '/evidence/f02-build.json', fs.constants.COPYFILE_EXCL)
console.log(JSON.stringify({ isolated: true, node: process.versions.node, reusedBuild: Boolean(reusable), buildId: JSON.parse(fs.readFileSync(manifestPath, 'utf8')).buildId }))
const result = spawnSync(process.execPath, ['scripts/test-admin-compliance-hub.mjs'], { cwd: workspace, env: process.env, stdio: 'inherit' })
if (result.error) throw result.error
process.exit(result.status || (result.signal ? 1 : 0))
NODE
  exit "$?"
fi

test "$(uname -s)" = Linux
test "$(id -u)" -ne 0
frontend="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
node_binary="$(realpath "${TOPS_F02_NODE_BINARY:-$(command -v node)}")"
test "$("$node_binary" --version)" = v22.13.1
dependencies="$(realpath "${TOPS_F02_NODE_MODULES:-$frontend/node_modules}")"
playwright_module="$(realpath "${TOPS_PLAYWRIGHT_MODULE:?Prepare Playwright 1.62.1 before F02}")"
test "$("$node_binary" -p 'require(process.argv[1]).version' "$playwright_module/package.json")" = 1.62.1
tools_modules="$(dirname "$playwright_module")"
test -d "$dependencies/next"
test -d "$tools_modules/playwright-core"
image=mcr.microsoft.com/playwright@sha256:dcc5531e97840b9b5e794f2814476b21571c5124a3fca2267d73041f56e7580e
image_id="$(docker image inspect --format '{{.Id}}' "$image")"
[[ "$image_id" =~ ^sha256:[a-f0-9]{64}$ ]]
printf 'F02 image: %s (%s)\n' "$image" "$image_id"
evidence_root="${TOPS_UI_EVIDENCE_ROOT:?Set a private evidence directory}"
mkdir -p "$evidence_root"
evidence="$(mktemp -d "$evidence_root/f02-isolated-XXXXXX")"
workspace="${TOPS_F02_WORK_DIR:-$(mktemp -d "${RUNNER_TEMP:-/tmp}/tops-f02-work-XXXXXX")}"
test -d "$workspace"
workspace="$(realpath "$workspace")"
container="tops-f02-$(id -u)-${evidence##*-}"
cleanup() {
  local result=$? owner remaining
  trap - EXIT
  if owner="$(docker inspect --format '{{index .Config.Labels "tops.f02.owner"}}' "$container" 2>/dev/null)"; then
    if [[ "$owner" != "$container" ]] || ! docker rm -f "$container"; then
      printf 'F02 container cleanup failed; original exit=%s\n' "$result" >&2
      [[ "$result" != 0 ]] || result=1
    fi
  fi
  if ! remaining="$(docker container ls -aq --filter "label=tops.f02.owner=$container")" || [[ -n "$remaining" ]]; then
    printf 'F02 cleanup could not prove absence of owned containers; original exit=%s\n' "$result" >&2
    [[ "$result" != 0 ]] || result=1
  fi
  printf 'F02 evidence: %s\nF02 private build preserved: %s\n' "$evidence" "$workspace"
  exit "$result"
}
trap cleanup EXIT
docker run --rm --init --pull never --name "$container" --label "tops.f02.owner=$container" \
  --network none --read-only --cap-drop ALL --security-opt no-new-privileges:true \
  --user "$(id -u):$(id -g)" --shm-size 512m --tmpfs /tmp:rw,exec,size=512m \
  --mount "type=bind,source=$frontend,target=/source,readonly" \
  --mount "type=bind,source=$dependencies,target=/deps/node_modules,readonly" \
  --mount "type=bind,source=$tools_modules,target=/tools/node_modules,readonly" \
  --mount "type=bind,source=$node_binary,target=/opt/f02-node/node,readonly" \
  --mount "type=bind,source=$workspace,target=/work" \
  --mount "type=bind,source=$evidence,target=/evidence" \
  --env PATH=/opt/f02-node:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  --env "TOPS_F02_PLAYWRIGHT_PACKAGE=$(basename "$playwright_module")" \
  --env HOME=/tmp --env PLAYWRIGHT_BROWSERS_PATH=/ms-playwright --env DEBUG=pw:browser \
  "$image_id" bash /source/scripts/run-admin-compliance-hub-isolated.sh --inside
