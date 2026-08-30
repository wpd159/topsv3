#!/usr/bin/env bash

set -Eeuo pipefail

V2_ARTIFACT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "${V2_ARTIFACT_DIR}/lib.sh"

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
  local output_dir="$1" source_sha="$2" archive dependency_archive
  [[ ! -e "${output_dir}" ]] || v2_die "diretorio de artefato ja existe"
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

  v2_node "scripts/deploy/v2/contract.mjs" manifest-create \
    "${output_dir}/release-manifest.json" "${source_sha}" "${archive}" \
    "${dependency_archive}" \
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
    "${output_dir}/release-manifest.sha256"
  v2_node "scripts/deploy/v2/contract.mjs" manifest-verify "${output_dir}"
  v2_log "ARTIFACT_CREATED imagesSha256=$(v2_sha256 "${archive}") dependenciesSha256=$(v2_sha256 "${dependency_archive}")"
  rm -rf -- "${V2_RUNTIME_DIR}"
}

v2_load_release_artifact() {
  local bundle_dir="$1" expected_sha="$2" image_name expected_id actual_id
  local dependency_archive entry entry_type
  [[ -d "${bundle_dir}" ]] || v2_die "bundle ausente"
  if find "${bundle_dir}" -type l -print -quit | grep -q .; then
    v2_die "bundle contem symlink"
  fi
  (cd -- "${bundle_dir}" && sha256sum --check --status release-manifest.sha256)
  v2_node "scripts/deploy/v2/contract.mjs" manifest-verify \
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
  done < <(v2_node "scripts/deploy/v2/contract.mjs" manifest-images "${bundle_dir}")
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
  v2_node "scripts/deploy/v2/contract.mjs" manifest-runtime-verify \
    "${bundle_dir}" "${expected_sha}" "${expected_run_id}"
  docker load --input "${bundle_dir}/release-images.tar" >/dev/null
  while IFS='|' read -r image_name expected_id; do
    [[ -n "${image_name}" && -n "${expected_id}" ]] || v2_die "imagem candidata invalida"
    actual_id="$(docker image inspect "${image_name}" --format '{{.Id}}')"
    [[ "${actual_id}" == "${expected_id}" ]] ||
      v2_die "imagem candidata diverge do manifesto: ${image_name}"
  done < <(v2_node "scripts/deploy/v2/contract.mjs" manifest-images "${bundle_dir}")
  v2_set_release_images "${expected_sha}"
  v2_log "CANDIDATE_ARTIFACT=VERIFIED sha=${expected_sha} run=${expected_run_id} rebuild=0"
}

v2_create_candidate_payload() {
  local bundle_dir="$1" expected_sha="$2" expected_run_id="$3" artifact_name="$4"
  local run_file="$5" artifacts_file="$6" output_dir="$7" context_file
  [[ ! -e "${output_dir}" ]] || v2_die "diretorio do payload ja existe"
  mkdir -m 0700 -p -- \
    "${output_dir}/.github/workflows" \
    "${output_dir}/deploy/v2" \
    "${output_dir}/scripts/deploy/v2" \
    "${output_dir}/backend/src/main/resources/db/migration" \
    "${output_dir}/artifact"
  context_file="${output_dir}/candidate-context.env"
  v2_node "scripts/deploy/v2/contract.mjs" candidate-metadata-verify \
    "${bundle_dir}" "${expected_sha}" "${expected_run_id}" "${artifact_name}" \
    "${run_file}" "${artifacts_file}" "${context_file}"

  cp -- "${V2_ROOT}/.github/workflows/pipeline-production-v2.yml" \
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
    "${output_dir}/artifact/"
  if [[ -f "${bundle_dir}/tool-versions.txt" ]]; then
    cp -- "${bundle_dir}/tool-versions.txt" "${output_dir}/artifact/"
  fi
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
