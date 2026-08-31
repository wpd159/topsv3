#!/usr/bin/env bash

set -Eeuo pipefail

V2_ARTIFACT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "${V2_ARTIFACT_DIR}/lib.sh"

v2_artifact_contract() {
  if [[ "${V2_ARTIFACT_USE_HOST_NODE:-0}" == "1" ]]; then
    node "${V2_ROOT}/scripts/deploy/v2/contract.mjs" "$@" </dev/null
  else
    v2_node "scripts/deploy/v2/contract.mjs" "$@"
  fi
}

v2_artifact_layout_diagnostic() {
  local extraction_dir="$1" error_code="$2" file_count=0 manifest_count=0
  local manifest_depths=none relative
  if [[ -d "${extraction_dir}" && ! -L "${extraction_dir}" ]]; then
    file_count="$(find -P "${extraction_dir}" -type f -printf '.' | wc -c | tr -d ' ')"
    manifest_count="$(find -P "${extraction_dir}" -type f \
      -name release-manifest.json -printf '.' | wc -c | tr -d ' ')"
    manifest_depths="$(find -P "${extraction_dir}" -type f \
      -name release-manifest.json -printf '%P\n' |
      awk -F/ '{print NF}' | sort -nu | paste -sd, -)"
    [[ -n "${manifest_depths}" ]] || manifest_depths=none
  fi
  v2_log "ARTIFACT_DIAGNOSTIC files=${file_count} manifests=${manifest_count} depths=${manifest_depths} artifactId=${V2_ARTIFACT_ID:-unknown} artifactName=${V2_ARTIFACT_NAME:-unknown} runId=${V2_ARTIFACT_RUN_ID:-unknown} expectedSha=${V2_ARTIFACT_EXPECTED_SHA:-unknown} errorCode=${error_code}"
  if [[ -d "${extraction_dir}" && ! -L "${extraction_dir}" ]]; then
    while IFS= read -r relative; do
      [[ -n "${relative}" ]] || continue
      relative="${relative//$'\n'/?}"
      relative="${relative//$'\r'/?}"
      v2_log "ARTIFACT_PATH=${relative}"
    done < <(find -P "${extraction_dir}" -mindepth 1 -maxdepth 3 -printf '%P\n' | sort)
  fi
}

v2_resolve_artifact_root() {
  local extraction_dir="$1" resolved error_file rc=0
  [[ "$#" -eq 1 ]] || v2_die "resolver recebe somente o diretorio de extracao"
  error_file="$(mktemp "${RUNNER_TEMP:-/tmp}/artifact-resolver-error.XXXXXX")"
  if resolved="$(v2_artifact_contract artifact-root-resolve \
      "${extraction_dir}" "${V2_ARTIFACT_EXPECTED_SHA:-}" \
      "${V2_ARTIFACT_RUN_ID:-}" 2>"${error_file}")"; then
    rc=0
  else
    rc=$?
  fi
  if [[ "${rc}" -ne 0 ]]; then
    v2_artifact_layout_diagnostic "${extraction_dir}" "${rc}"
    sed -n '1p' "${error_file}" >&2
    rm -f -- "${error_file}"
    return "${rc}"
  fi
  rm -f -- "${error_file}"
  [[ -n "${resolved}" && -d "${resolved}" && ! -L "${resolved}" ]] ||
    v2_die "resolver retornou raiz invalida"
  V2_RESOLVED_ARTIFACT_ROOT="${resolved}"
  export V2_RESOLVED_ARTIFACT_ROOT
  printf '%s\n' 'ARTIFACT_ROOT_RESOLVED=YES'
}

v2_set_release_images() {
  local source_sha="$1"
  [[ "${source_sha}" =~ ^[a-f0-9]{40}$ ]] || v2_die "SHA de origem invalido"
  export V2_BACKEND_IMAGE="topsdojob-v2-backend:${source_sha}"
  export V2_FRONTEND_IMAGE="topsdojob-v2-frontend:${source_sha}"
  export V2_GATEWAY_IMAGE="topsdojob-v2-gateway:${source_sha}"
}

v2_build_test_dependency_bundle() {
  local output="$1" dependency_root backend_copy frontend_copy uid_gid
  dependency_root="${V2_RUNTIME_DIR}/test-dependencies"
  backend_copy="${V2_RUNTIME_DIR}/backend-dependencies"
  frontend_copy="${V2_RUNTIME_DIR}/frontend-dependencies"
  uid_gid="$(id -u):$(id -g)"
  mkdir -m 0700 -p -- "${dependency_root}/m2/repository" "${dependency_root}/npm"

  docker run --rm --network bridge --user "${uid_gid}" \
    --env HOME=/tmp/pipeline-v2-maven-home \
    --volume "${V2_ROOT}:${V2_ROOT}:ro" \
    --volume "${V2_RUNTIME_DIR}:${V2_RUNTIME_DIR}" \
    "${V2_MAVEN_IMAGE}" sh -euc '
      cp -a "$1/backend" "$2"
      rm -rf "$2/target"
      mvn --batch-mode --no-transfer-progress \
        -Dmaven.repo.local="$3/m2/repository" -DskipTests -f "$2/pom.xml" verify
      mvn --batch-mode --no-transfer-progress \
        -Dmaven.repo.local="$3/m2/repository" \
        -Dtest=CategoriaAnuncioTest -f "$2/pom.xml" test
      rm -rf "$2/target"
      mvn --offline --batch-mode --no-transfer-progress \
        -Dmaven.repo.local="$3/m2/repository" -DskipTests -f "$2/pom.xml" verify
      mvn --offline --batch-mode --no-transfer-progress \
        -Dmaven.repo.local="$3/m2/repository" \
        -Dtest=CategoriaAnuncioTest -f "$2/pom.xml" test
      rm -rf "$2" /tmp/pipeline-v2-maven-home
    ' _ "${V2_ROOT}" "${backend_copy}" "${dependency_root}" </dev/null

  docker run --rm --network bridge --user "${uid_gid}" \
    --env HOME=/tmp/pipeline-v2-node-home \
    --volume "${V2_ROOT}:${V2_ROOT}:ro" \
    --volume "${V2_RUNTIME_DIR}:${V2_RUNTIME_DIR}" \
    "${V2_NODE_IMAGE}" /bin/sh -euc '
      cp -a "$1/frontend" "$2"
      rm -rf "$2/node_modules" "$2/.next" "$2/tsconfig.tsbuildinfo"
      cd "$2"
      npm ci --cache "$3/npm" --no-audit --no-fund
      rm -rf node_modules
      npm ci --offline --cache "$3/npm" --no-audit --no-fund
      rm -rf node_modules
      cd /
      rm -rf "$2" /tmp/pipeline-v2-node-home
    ' _ "${V2_ROOT}" "${frontend_copy}" "${dependency_root}" </dev/null

  if find "${dependency_root}" -type l -print -quit | grep -q .; then
    v2_die "repositorio de dependencias contem symlink"
  fi
  tar --sort=name --mtime='UTC 2026-08-29' --owner=0 --group=0 --numeric-owner \
    -cf "${output}" -C "${dependency_root}" m2 npm
  [[ -s "${output}" ]] || v2_die "artefato de dependencias vazio"
  v2_log "TEST_DEPENDENCIES=OFFLINE_VALIDATED"
}

v2_build_release_artifact() {
  local output_dir="$1" source_sha="$2" versions_file="$3" archive dependency_archive
  [[ ! -e "${output_dir}" ]] || v2_die "diretorio de artefato ja existe"
  [[ -s "${versions_file}" && ! -L "${versions_file}" ]] ||
    v2_die "metadados de ferramentas ausentes"
  mkdir -m 0700 -p -- "${output_dir}"
  export V2_PROJECT_NAME="topsdojob-v2-build-${source_sha:0:12}-${V2_RUN_ID//[^a-zA-Z0-9_.-]/-}"
  export V2_RUNTIME_DIR="${RUNNER_TEMP:-/tmp}/${V2_PROJECT_NAME}"
  v2_set_synthetic_credentials build
  mkdir -p -- "${V2_RUNTIME_DIR}/certs/minio" "${V2_RUNTIME_DIR}/reports"
  v2_set_release_images "${source_sha}"

  v2_log "BUILD_ONCE sha=${source_sha}"
  dependency_archive="${output_dir}/test-dependencies.tar"
  v2_build_test_dependency_bundle "${dependency_archive}"
  v2_compose build --no-cache backend frontend gateway
  docker image inspect "${V2_BACKEND_IMAGE}" "${V2_FRONTEND_IMAGE}" \
    "${V2_GATEWAY_IMAGE}" >/dev/null

  archive="${output_dir}/release-images.tar"
  docker save --output "${archive}" \
    "${V2_BACKEND_IMAGE}" "${V2_FRONTEND_IMAGE}" "${V2_GATEWAY_IMAGE}"
  [[ -s "${archive}" ]] || v2_die "arquivo de imagens vazio"
  cp -- "${versions_file}" "${output_dir}/tool-versions.txt"
  chmod 0600 "${output_dir}/tool-versions.txt"

  v2_artifact_contract manifest-create \
    "${output_dir}/release-manifest.json" "${source_sha}" "${archive}" \
    "${dependency_archive}" "${output_dir}/tool-versions.txt" \
    "${CERTIFICATION_RUN_ID:?run de certificacao obrigatorio}" \
    "${CERTIFICATION_RUN_ATTEMPT:?attempt de certificacao obrigatorio}" \
    "${CERTIFICATION_ARTIFACT_NAME:?nome do artefato obrigatorio}" \
    "${V2_BACKEND_IMAGE}" "$(docker image inspect "${V2_BACKEND_IMAGE}" --format '{{.Id}}')" \
    "${V2_FRONTEND_IMAGE}" "$(docker image inspect "${V2_FRONTEND_IMAGE}" --format '{{.Id}}')" \
    "${V2_GATEWAY_IMAGE}" "$(docker image inspect "${V2_GATEWAY_IMAGE}" --format '{{.Id}}')"
  (
    cd -- "${output_dir}"
    sha256sum -- release-manifest.json > release-manifest.sha256
  )
  chmod 0600 "${archive}" "${dependency_archive}" "${output_dir}/release-manifest.json" \
    "${output_dir}/release-manifest.sha256" "${output_dir}/tool-versions.txt"
  v2_artifact_contract manifest-verify "${output_dir}"
  v2_log "ARTIFACT_CREATED imagesSha256=$(v2_sha256 "${archive}") dependenciesSha256=$(v2_sha256 "${dependency_archive}")"
  rm -rf -- "${V2_RUNTIME_DIR}"
}

v2_load_release_artifact() {
  local extraction_dir="$1" expected_sha="$2" bundle_dir image_name expected_id actual_id
  local dependency_archive entry entry_type
  export V2_ARTIFACT_EXPECTED_SHA="${expected_sha}"
  export V2_ARTIFACT_RUN_ID="${V2_ARTIFACT_RUN_ID:-}"
  v2_resolve_artifact_root "${extraction_dir}" || v2_die "raiz do artifact rejeitada"
  bundle_dir="${V2_RESOLVED_ARTIFACT_ROOT}"
  (cd -- "${bundle_dir}" && sha256sum --check --status release-manifest.sha256)
  v2_artifact_contract manifest-verify \
    "${bundle_dir}" "${expected_sha}"
  dependency_archive="${bundle_dir}/test-dependencies.tar"
  while IFS= read -r entry; do
    case "${entry}" in
      m2|m2/|m2/*|npm|npm/|npm/*) ;;
      *) v2_die "entrada invalida no artefato de dependencias" ;;
    esac
  done < <(tar -tf "${dependency_archive}")
  while IFS= read -r entry_type; do
    [[ "${entry_type}" != l && "${entry_type}" != h ]] ||
      v2_die "link proibido no artefato de dependencias"
  done < <(tar -tvf "${dependency_archive}" | cut -c1)
  mkdir -m 0700 -p -- "${V2_RUNTIME_DIR}/dependencies"
  tar --extract --file "${dependency_archive}" \
    --directory "${V2_RUNTIME_DIR}/dependencies" \
    --no-same-owner --no-same-permissions
  export V2_TEST_M2_DIR="${V2_RUNTIME_DIR}/dependencies/m2"
  export V2_TEST_NPM_CACHE="${V2_RUNTIME_DIR}/dependencies/npm"
  [[ -d "${V2_TEST_M2_DIR}/repository" && -d "${V2_TEST_NPM_CACHE}/_cacache" ]] ||
    v2_die "repositorios offline incompletos"
  docker load --input "${bundle_dir}/release-images.tar" >/dev/null

  while IFS='|' read -r image_name expected_id; do
    [[ -n "${image_name}" && -n "${expected_id}" ]] || v2_die "imagem invalida no manifesto"
    actual_id="$(docker image inspect "${image_name}" --format '{{.Id}}')"
    [[ "${actual_id}" == "${expected_id}" ]] ||
      v2_die "imagem carregada diverge do manifesto: ${image_name}"
  done < <(v2_artifact_contract manifest-images "${bundle_dir}")
  v2_set_release_images "${expected_sha}"
  v2_log "ARTIFACT_VERIFIED imagesSha=$(v2_sha256 "${bundle_dir}/release-images.tar") dependenciesSha=$(v2_sha256 "${dependency_archive}")"
}

v2_load_candidate_artifact() {
  local bundle_dir="$1" expected_sha="$2" expected_run_id="$3"
  local image_name expected_id actual_id
  [[ -d "${bundle_dir}" ]] || v2_die "bundle de candidata ausente"
  if find "${bundle_dir}" -type l -print -quit | grep -q .; then
    v2_die "bundle de candidata contem symlink"
  fi
  (cd -- "${bundle_dir}" && sha256sum --check --status release-manifest.sha256)
  v2_artifact_contract manifest-runtime-verify \
    "${bundle_dir}" "${expected_sha}" "${expected_run_id}"
  docker load --input "${bundle_dir}/release-images.tar" >/dev/null
  while IFS='|' read -r image_name expected_id; do
    [[ -n "${image_name}" && -n "${expected_id}" ]] || v2_die "imagem candidata invalida"
    actual_id="$(docker image inspect "${image_name}" --format '{{.Id}}')"
    [[ "${actual_id}" == "${expected_id}" ]] ||
      v2_die "imagem candidata diverge do manifesto: ${image_name}"
  done < <(v2_artifact_contract manifest-images "${bundle_dir}")
  v2_set_release_images "${expected_sha}"
  v2_log "CANDIDATE_ARTIFACT=VERIFIED sha=${expected_sha} run=${expected_run_id} rebuild=0"
}

v2_create_candidate_payload() {
  local extraction_dir="$1" expected_sha="$2" expected_run_id="$3" artifact_name="$4"
  local artifact_id="$5" artifact_digest="$6" run_file="$7" artifacts_file="$8"
  local output_dir="$9" context_file bundle_dir
  export V2_ARTIFACT_EXPECTED_SHA="${expected_sha}"
  export V2_ARTIFACT_RUN_ID="${expected_run_id}"
  export V2_ARTIFACT_ID="${artifact_id}"
  export V2_ARTIFACT_NAME="${artifact_name}"
  v2_resolve_artifact_root "${extraction_dir}" || v2_die "raiz do artifact rejeitada"
  bundle_dir="${V2_RESOLVED_ARTIFACT_ROOT}"
  [[ ! -e "${output_dir}" ]] || v2_die "diretorio do payload ja existe"
  mkdir -m 0700 -p -- \
    "${output_dir}/.github/workflows" \
    "${output_dir}/deploy/v2" \
    "${output_dir}/scripts/deploy/v2" \
    "${output_dir}/backend/src/main/resources/db/migration" \
    "${output_dir}/artifact"
  context_file="${output_dir}/candidate-context.env"
  v2_artifact_contract candidate-metadata-verify \
    "${bundle_dir}" "${expected_sha}" "${expected_run_id}" "${artifact_name}" \
    "${artifact_id}" "${artifact_digest}" "${run_file}" "${artifacts_file}" \
    "${context_file}"

  cp -- "${V2_ROOT}/.github/workflows/pipeline-production-v2.yml" \
    "${V2_ROOT}/.github/workflows/pipeline-v2-artifact-roundtrip.yml" \
    "${output_dir}/.github/workflows/"
  cp -- "${V2_ROOT}/deploy/v2/compose.yml" "${V2_ROOT}/deploy/v2/images.lock.json" \
    "${output_dir}/deploy/v2/"
  cp -- "${V2_ROOT}"/scripts/deploy/v2/*.sh "${V2_ROOT}"/scripts/deploy/v2/*.mjs \
    "${output_dir}/scripts/deploy/v2/"
  cp -- "${V2_ROOT}"/backend/src/main/resources/db/migration/V*.sql \
    "${output_dir}/backend/src/main/resources/db/migration/"
  cp -- "${bundle_dir}/release-images.tar" \
    "${bundle_dir}/release-manifest.json" \
    "${bundle_dir}/release-manifest.sha256" \
    "${bundle_dir}/tool-versions.txt" \
    "${output_dir}/artifact/"
  find "${output_dir}" -type d -exec chmod 0700 {} +
  find "${output_dir}" -type f -exec chmod 0600 {} +
  chmod 0500 "${output_dir}"/scripts/deploy/v2/*.sh

  docker run --rm --network none \
    --user "$(id -u):$(id -g)" \
    --volume "${output_dir}:${output_dir}:ro" \
    --workdir "${output_dir}" \
    "${V2_NODE_IMAGE}" node scripts/deploy/v2/contract.mjs \
    manifest-runtime-verify "${output_dir}/artifact" \
    "${expected_sha}" "${expected_run_id}" </dev/null
  v2_log "CANDIDATE_PAYLOAD=READY artifact=${artifact_name} rebuild=0"
}

v2_create_artifact_contract_fixture() {
  local output_dir="$1" source_sha="$2" run_id="$3" run_attempt="$4"
  local artifact_name="$5"
  v2_artifact_contract artifact-fixture-create \
    "${output_dir}" "${source_sha}" "${run_id}" "${run_attempt}" "${artifact_name}"
}

v2_verify_artifact_roundtrip() {
  local extraction_dir="$1" source_sha="$2" run_id="$3" artifact_id="$4"
  local artifact_name="$5" artifact_digest="$6" manifest_name
  [[ "${artifact_id}" =~ ^[1-9][0-9]*$ ]] || v2_die "ID do round-trip invalido"
  [[ "${artifact_digest}" =~ ^sha256:[a-f0-9]{64}$ ]] ||
    v2_die "digest do round-trip invalido"
  export V2_ARTIFACT_EXPECTED_SHA="${source_sha}"
  export V2_ARTIFACT_RUN_ID="${run_id}"
  export V2_ARTIFACT_ID="${artifact_id}"
  export V2_ARTIFACT_NAME="${artifact_name}"
  v2_resolve_artifact_root "${extraction_dir}"
  manifest_name="$(v2_artifact_contract manifest-artifact-name \
    "${V2_RESOLVED_ARTIFACT_ROOT}" "${source_sha}" "${run_id}")"
  [[ "${manifest_name}" == "${artifact_name}" ]] ||
    v2_die "nome do artifact diverge no round-trip"
  v2_log "ARTIFACT_ROUNDTRIP=PASS manifest=verified payload=verified residues=0"
}
