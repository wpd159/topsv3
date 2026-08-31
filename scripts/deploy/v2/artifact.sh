#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

V2_ARTIFACT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "${V2_ARTIFACT_DIR}/lib.sh"

V2_PAYLOAD_NAME=candidate-payload.tar
V2_PAYLOAD_ROOT_NAME=candidate-root
V2_PAYLOAD_LAYOUT_VERSION=2

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
  local extraction_dir="$1" resolved root_file absolute_extraction
  [[ "$#" -eq 1 ]] || v2_die "resolver recebe somente o diretorio de extracao"
  [[ -n "${V2_TRANSPORT_JQ_SHA256:-}" ]] || v2_die "hash do jq de transporte ausente"
  absolute_extraction="$(cd -- "${extraction_dir}" && pwd -P)"
  root_file="$(mktemp "${RUNNER_TEMP:-/tmp}/artifact-root.XXXXXX")"
  rm -f -- "${root_file}"
  if ! bash "${V2_ROOT}/scripts/deploy/v2/transport.sh" resolve \
      "${absolute_extraction}" "${V2_ARTIFACT_EXPECTED_SHA:-}" \
      "${V2_ARTIFACT_RUN_ID:-}" "${V2_TRANSPORT_JQ_SHA256}" "${root_file}"; then
    v2_artifact_layout_diagnostic "${extraction_dir}" 1
    rm -f -- "${root_file}"
    return 1
  fi
  resolved="$(cat -- "${root_file}")"
  rm -f -- "${root_file}"
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

v2_payload_context_value() {
  local file="$1" key="$2" value
  value="$(sed -n "s/^${key}=//p" "${file}")"
  [[ -n "${value}" ]] || v2_die "contexto do payload incompleto: ${key}"
  printf '%s\n' "${value}"
}

v2_copy_payload_sources() {
  local payload_root="$1" relative source destination
  while IFS= read -r relative; do
    [[ -n "${relative}" && "${relative}" != /* && "${relative}" != *..* ]] ||
      v2_die "fonte invalida no pacote"
    source="${V2_ROOT}/${relative}"
    destination="${payload_root}/${relative}"
    [[ -f "${source}" && ! -L "${source}" ]] || v2_die "fonte do pacote ausente"
    mkdir -p -- "$(dirname -- "${destination}")"
    cp -- "${source}" "${destination}"
    chmod 0600 -- "${destination}"
  done < <(v2_artifact_contract package-source-files)
  chmod 0500 "${payload_root}"/scripts/deploy/v2/*.sh
}

v2_extract_static_jq() {
  local output="$1" container jq_image="${V2_JQ_IMAGE:?imagem jq ausente}"
  mkdir -p -- "$(dirname -- "${output}")"
  docker pull --platform "${V2_PLATFORM}" "${jq_image}" >/dev/null
  container="$(docker create "${jq_image}")"
  if ! docker cp "${container}:/jq" "${output}"; then
    docker rm "${container}" >/dev/null 2>&1 || true
    v2_die "falha ao extrair jq hermetico"
  fi
  docker rm "${container}" >/dev/null
  chmod 0500 -- "${output}"
  [[ "$("${output}" --version)" == jq-1.7.1 ]] || v2_die "jq hermetico invalido"
}

v2_prepare_payload_root() {
  local payload_root="$1"
  [[ ! -e "${payload_root}" ]] || v2_die "raiz do payload ja existe"
  mkdir -p -- "${payload_root}"
  v2_copy_payload_sources "${payload_root}"
  v2_extract_static_jq "${payload_root}/tools/jq"
}

v2_finalize_canonical_payload() {
  local payload_root="$1" output_dir="$2" source_sha="$3" run_id="$4"
  local run_attempt="$5" artifact_name="$6" package checksum certification
  local validation_dir root_file package_sha package_bytes transport_sha jq_sha
  [[ "$(basename -- "${payload_root}")" == "${V2_PAYLOAD_ROOT_NAME}" ]] ||
    v2_die "nome da raiz canonica invalido"
  [[ ! -e "${output_dir}" ]] || v2_die "envelope do payload ja existe"
  mkdir -p -- "${output_dir}"
  v2_artifact_contract manifest-verify "${payload_root}" "${source_sha}" "${run_id}"
  package="${output_dir}/${V2_PAYLOAD_NAME}"
  checksum="${output_dir}/candidate-payload.sha256"
  certification="${output_dir}/candidate-payload-certification.json"
  tar --sort=name --mtime='UTC 2026-08-31' --owner=0 --group=0 --numeric-owner \
    -cf "${package}" -C "$(dirname -- "${payload_root}")" "${V2_PAYLOAD_ROOT_NAME}"
  package_sha="$(v2_sha256 "${package}")"
  package_bytes="$(stat -c %s -- "${package}")"
  printf '%s  %s\n' "${package_sha}" "${V2_PAYLOAD_NAME}" > "${checksum}"
  v2_artifact_contract payload-certification-create \
    "${certification}" "${package}" "${payload_root}" "${source_sha}" \
    "${run_id}" "${run_attempt}" "${artifact_name}"
  chmod 0600 -- "${package}" "${checksum}" "${certification}"
  transport_sha="$(v2_sha256 "${payload_root}/scripts/deploy/v2/transport.sh")"
  jq_sha="$(v2_sha256 "${payload_root}/tools/jq")"
  validation_dir="$(dirname -- "${payload_root}")/payload-validation"
  root_file="$(dirname -- "${payload_root}")/payload-validation-root"
  bash "${V2_ROOT}/scripts/deploy/v2/transport.sh" extract \
    "$(cd -- "${output_dir}" && pwd -P)/${V2_PAYLOAD_NAME}" \
    "${package_sha}" "${package_bytes}" "${transport_sha}" "${jq_sha}" \
    "$(cd -- "$(dirname -- "${payload_root}")" && pwd -P)/$(basename -- "${validation_dir}")" \
    "${source_sha}" "${run_id}" \
    "$(cd -- "$(dirname -- "${payload_root}")" && pwd -P)/$(basename -- "${root_file}")"
  rm -rf -- "${validation_dir}" "${root_file}"
  v2_log "CANONICAL_PAYLOAD=CREATED layoutVersion=${V2_PAYLOAD_LAYOUT_VERSION} bytes=${package_bytes} sha=${package_sha:0:12} package=${V2_PAYLOAD_NAME}"
}

v2_payload_envelope_context() {
  local download_root="$1" source_sha="$2" run_id="$3" artifact_name="$4" output="$5"
  v2_artifact_contract payload-envelope-verify \
    "${download_root}" "${source_sha}" "${run_id}" "${artifact_name}" "${output}"
}

v2_extract_downloaded_payload() {
  local download_root="$1" source_sha="$2" run_id="$3" artifact_name="$4"
  local context="$5" extraction="$6" root_file package package_sha package_bytes
  local transport_sha jq_sha
  v2_payload_envelope_context "${download_root}" "${source_sha}" "${run_id}" \
    "${artifact_name}" "${context}"
  package="$(v2_payload_context_value "${context}" PAYLOAD_PATH)"
  package_sha="$(v2_payload_context_value "${context}" PAYLOAD_SHA256)"
  package_bytes="$(v2_payload_context_value "${context}" PAYLOAD_BYTES)"
  transport_sha="$(v2_payload_context_value "${context}" TRANSPORT_SHA256)"
  jq_sha="$(v2_payload_context_value "${context}" TRANSPORT_JQ_SHA256)"
  root_file="${context}.root"
  export V2_TRANSPORT_JQ_SHA256="${jq_sha}"
  bash "${V2_ROOT}/scripts/deploy/v2/transport.sh" extract \
    "${package}" "${package_sha}" "${package_bytes}" "${transport_sha}" "${jq_sha}" \
    "${extraction}" "${source_sha}" "${run_id}" "${root_file}"
  V2_RESOLVED_ARTIFACT_ROOT="$(cat -- "${root_file}")"
  export V2_RESOLVED_ARTIFACT_ROOT
  rm -f -- "${root_file}"
}

v2_build_test_dependency_bundle() {
  local output="$1" dependency_root backend_copy frontend_copy uid_gid
  dependency_root="${V2_RUNTIME_DIR}/test-dependencies"
  backend_copy="${V2_RUNTIME_DIR}/backend-dependencies"
  frontend_copy="${V2_RUNTIME_DIR}/frontend-dependencies"
  uid_gid="$(id -u):$(id -g)"
  mkdir -p -- "${dependency_root}/m2/repository" "${dependency_root}/npm"

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
  local payload_root
  [[ ! -e "${output_dir}" ]] || v2_die "diretorio de artefato ja existe"
  [[ -s "${versions_file}" && ! -L "${versions_file}" ]] ||
    v2_die "metadados de ferramentas ausentes"
  export V2_PROJECT_NAME="topsdojob-v2-build-${source_sha:0:12}-${V2_RUN_ID//[^a-zA-Z0-9_.-]/-}"
  export V2_RUNTIME_DIR="${RUNNER_TEMP:-/tmp}/${V2_PROJECT_NAME}"
  v2_set_synthetic_credentials build
  mkdir -p -- "${V2_RUNTIME_DIR}/certs/minio" "${V2_RUNTIME_DIR}/reports"
  v2_set_release_images "${source_sha}"
  payload_root="${V2_RUNTIME_DIR}/${V2_PAYLOAD_ROOT_NAME}"
  v2_prepare_payload_root "${payload_root}"

  v2_log "BUILD_ONCE sha=${source_sha}"
  dependency_archive="${payload_root}/test-dependencies.tar"
  v2_build_test_dependency_bundle "${dependency_archive}"
  v2_compose build --no-cache backend frontend gateway
  docker image inspect "${V2_BACKEND_IMAGE}" "${V2_FRONTEND_IMAGE}" \
    "${V2_GATEWAY_IMAGE}" >/dev/null

  archive="${payload_root}/release-images.tar"
  docker save --output "${archive}" \
    "${V2_BACKEND_IMAGE}" "${V2_FRONTEND_IMAGE}" "${V2_GATEWAY_IMAGE}"
  [[ -s "${archive}" ]] || v2_die "arquivo de imagens vazio"
  cp -- "${versions_file}" "${payload_root}/tool-versions.txt"
  chmod 0600 "${payload_root}/tool-versions.txt"

  v2_artifact_contract manifest-create \
    "${payload_root}/release-manifest.json" "${source_sha}" "${archive}" \
    "${dependency_archive}" "${payload_root}/tool-versions.txt" \
    "${payload_root}/tools/jq" \
    "${CERTIFICATION_RUN_ID:?run de certificacao obrigatorio}" \
    "${CERTIFICATION_RUN_ATTEMPT:?attempt de certificacao obrigatorio}" \
    "${CERTIFICATION_ARTIFACT_NAME:?nome do artefato obrigatorio}" \
    "${V2_BACKEND_IMAGE}" "$(docker image inspect "${V2_BACKEND_IMAGE}" --format '{{.Id}}')" \
    "${V2_FRONTEND_IMAGE}" "$(docker image inspect "${V2_FRONTEND_IMAGE}" --format '{{.Id}}')" \
    "${V2_GATEWAY_IMAGE}" "$(docker image inspect "${V2_GATEWAY_IMAGE}" --format '{{.Id}}')"
  (
    cd -- "${payload_root}"
    sha256sum -- release-manifest.json > release-manifest.sha256
  )
  chmod 0600 "${archive}" "${dependency_archive}" "${payload_root}/release-manifest.json" \
    "${payload_root}/release-manifest.sha256" "${payload_root}/tool-versions.txt"
  v2_artifact_contract manifest-verify "${payload_root}" "${source_sha}" \
    "${CERTIFICATION_RUN_ID}"
  v2_finalize_canonical_payload "${payload_root}" "${output_dir}" "${source_sha}" \
    "${CERTIFICATION_RUN_ID}" "${CERTIFICATION_RUN_ATTEMPT}" \
    "${CERTIFICATION_ARTIFACT_NAME}"
  v2_log "ARTIFACT_CREATED imagesSha256=$(v2_sha256 "${archive}") dependenciesSha256=$(v2_sha256 "${dependency_archive}")"
  rm -rf -- "${V2_RUNTIME_DIR}"
}

v2_load_release_artifact() {
  local download_root="$1" expected_sha="$2" expected_run_id="$3" artifact_name="$4"
  local bundle_dir image_name expected_id actual_id context extraction
  local dependency_archive entry entry_type
  [[ "$#" -eq 4 ]] || v2_die "uso invalido ao carregar artifact"
  export V2_ARTIFACT_EXPECTED_SHA="${expected_sha}"
  export V2_ARTIFACT_RUN_ID="${expected_run_id}"
  context="${V2_RUNTIME_DIR}/payload-context.env"
  extraction="${V2_RUNTIME_DIR}/certified-payload"
  v2_extract_downloaded_payload "${download_root}" "${expected_sha}" "${expected_run_id}" \
    "${artifact_name}" "${context}" "${extraction}"
  bundle_dir="${V2_RESOLVED_ARTIFACT_ROOT}"
  (cd -- "${bundle_dir}" && sha256sum --check --status release-manifest.sha256)
  v2_artifact_contract manifest-verify \
    "${bundle_dir}" "${expected_sha}" "${expected_run_id}"
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
  mkdir -p -- "${V2_RUNTIME_DIR}/dependencies"
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

v2_validate_candidate_metadata() {
  local extraction_dir="$1" expected_sha="$2" expected_run_id="$3" artifact_name="$4"
  local artifact_id="$5" artifact_digest="$6" run_file="$7" artifacts_file="$8"
  local context_file="$9" package package_sha package_bytes transport_sha jq_sha
  local validation root_file
  v2_artifact_contract candidate-metadata-verify \
    "${extraction_dir}" "${expected_sha}" "${expected_run_id}" "${artifact_name}" \
    "${artifact_id}" "${artifact_digest}" "${run_file}" "${artifacts_file}" \
    "${context_file}"
  package="$(v2_payload_context_value "${context_file}" PAYLOAD_PATH)"
  package_sha="$(v2_payload_context_value "${context_file}" PAYLOAD_SHA256)"
  package_bytes="$(v2_payload_context_value "${context_file}" PAYLOAD_BYTES)"
  transport_sha="$(v2_payload_context_value "${context_file}" TRANSPORT_SHA256)"
  jq_sha="$(v2_payload_context_value "${context_file}" TRANSPORT_JQ_SHA256)"
  validation="${context_file}.validation"
  root_file="${context_file}.root"
  bash "${V2_ROOT}/scripts/deploy/v2/transport.sh" extract \
    "${package}" "${package_sha}" "${package_bytes}" "${transport_sha}" "${jq_sha}" \
    "${validation}" "${expected_sha}" "${expected_run_id}" "${root_file}"
  [[ "$(basename -- "$(cat -- "${root_file}")")" == "${V2_PAYLOAD_ROOT_NAME}" ]] ||
    v2_die "raiz local do payload divergente"
  rm -rf -- "${validation}" "${root_file}"
  v2_log "CANDIDATE_PAYLOAD=VERIFIED artifact=${artifact_name} layoutVersion=${V2_PAYLOAD_LAYOUT_VERSION} manifest=verified rebuild=0 repackage=0"
}

v2_create_artifact_contract_fixture() {
  local output_dir="$1" source_sha="$2" run_id="$3" run_attempt="$4"
  local artifact_name="$5" temporary payload_root
  temporary="${RUNNER_TEMP:-/tmp}/topsdojob-v2-fixture-${run_id}-${run_attempt}-$$"
  payload_root="${temporary}/${V2_PAYLOAD_ROOT_NAME}"
  [[ ! -e "${temporary}" ]] || v2_die "fixture temporaria preexistente"
  mkdir -p -- "${temporary}"
  v2_prepare_payload_root "${payload_root}"
  v2_artifact_contract artifact-fixture-create \
    "${payload_root}" "${source_sha}" "${run_id}" "${run_attempt}" "${artifact_name}"
  v2_finalize_canonical_payload "${payload_root}" "${output_dir}" "${source_sha}" \
    "${run_id}" "${run_attempt}" "${artifact_name}"
  rm -rf -- "${temporary}"
}

v2_verify_artifact_roundtrip() {
  local extraction_dir="$1" source_sha="$2" run_id="$3" artifact_id="$4"
  local artifact_name="$5" artifact_digest="$6" manifest_name context payload_extraction
  local package package_sha package_bytes transport_sha jq_sha evidence_dir
  [[ "${artifact_id}" =~ ^[1-9][0-9]*$ ]] || v2_die "ID do round-trip invalido"
  [[ "${artifact_digest}" =~ ^sha256:[a-f0-9]{64}$ ]] ||
    v2_die "digest do round-trip invalido"
  export V2_ARTIFACT_EXPECTED_SHA="${source_sha}"
  export V2_ARTIFACT_RUN_ID="${run_id}"
  export V2_ARTIFACT_ID="${artifact_id}"
  export V2_ARTIFACT_NAME="${artifact_name}"
  context="${RUNNER_TEMP:-/tmp}/topsdojob-v2-roundtrip-context-${run_id}"
  payload_extraction="${RUNNER_TEMP:-/tmp}/topsdojob-v2-roundtrip-extract-${run_id}"
  v2_extract_downloaded_payload "${extraction_dir}" "${source_sha}" "${run_id}" \
    "${artifact_name}" "${context}" "${payload_extraction}"
  manifest_name="$(v2_artifact_contract manifest-artifact-name \
    "${V2_RESOLVED_ARTIFACT_ROOT}" "${source_sha}" "${run_id}")"
  [[ "${manifest_name}" == "${artifact_name}" ]] ||
    v2_die "nome do artifact diverge no round-trip"
  package="$(v2_payload_context_value "${context}" PAYLOAD_PATH)"
  package_sha="$(v2_payload_context_value "${context}" PAYLOAD_SHA256)"
  package_bytes="$(v2_payload_context_value "${context}" PAYLOAD_BYTES)"
  transport_sha="$(v2_payload_context_value "${context}" TRANSPORT_SHA256)"
  jq_sha="$(v2_payload_context_value "${context}" TRANSPORT_JQ_SHA256)"
  evidence_dir="${RUNNER_TEMP:-/tmp}/topsdojob-v2-roundtrip-ssh-${run_id}"
  bash "${V2_ROOT}/scripts/deploy/v2/transport.sh" ssh-lab \
    "${package}" "${package_sha}" "${package_bytes}" "${transport_sha}" "${jq_sha}" \
    "${source_sha}" "${run_id}" "${artifact_id}" 1 "${evidence_dir}"
  rm -rf -- "${payload_extraction}" "${context}" "${evidence_dir}"
  v2_log "ARTIFACT_ROUND_TRIP=PASS REMOTE_TRANSPORT_CONTRACT=PASS manifest=verified payload=verified residues=0"
}

v2_expect_transport_failure() {
  local name="$1" package="$2" expected_sha="$3" expected_bytes="$4"
  local transport_sha="$5" jq_sha="$6" source_sha="$7" run_id="$8" test_root="$9"
  local extraction="${test_root}/extract-${name}" root_file="${test_root}/root-${name}"
  local diagnostic="${test_root}/diagnostic-${name}.log" rc
  set +e
  bash "${V2_ROOT}/scripts/deploy/v2/transport.sh" extract \
    "${package}" "${expected_sha}" "${expected_bytes}" "${transport_sha}" "${jq_sha}" \
    "${extraction}" "${source_sha}" "${run_id}" "${root_file}" \
    >"${diagnostic}" 2>&1
  rc=$?
  set -e
  [[ "${rc}" -ne 0 && -s "${diagnostic}" ]] ||
    v2_die "teste negativo do transporte nao falhou: ${name}"
  rm -rf -- "${extraction}" "${root_file}"
  v2_log "TRANSPORT_NEGATIVE=${name}:BLOCKED"
}

v2_transport_negative_tests() {
  local envelope="$1" source_sha="$2" run_id="$3" artifact_name="$4" test_root="$5"
  local context package package_sha package_bytes transport_sha jq_sha baseline root_file root
  local case_dir case_root mutated actual_sha actual_bytes
  [[ ! -e "${test_root}" ]] || v2_die "diretorio de negativos preexistente"
  mkdir -p -- "${test_root}"
  context="${test_root}/payload.env"
  v2_payload_envelope_context "${envelope}" "${source_sha}" "${run_id}" \
    "${artifact_name}" "${context}"
  package="$(v2_payload_context_value "${context}" PAYLOAD_PATH)"
  package_sha="$(v2_payload_context_value "${context}" PAYLOAD_SHA256)"
  package_bytes="$(v2_payload_context_value "${context}" PAYLOAD_BYTES)"
  transport_sha="$(v2_payload_context_value "${context}" TRANSPORT_SHA256)"
  jq_sha="$(v2_payload_context_value "${context}" TRANSPORT_JQ_SHA256)"
  baseline="${test_root}/baseline"
  root_file="${test_root}/baseline.root"
  bash "${V2_ROOT}/scripts/deploy/v2/transport.sh" extract \
    "${package}" "${package_sha}" "${package_bytes}" "${transport_sha}" "${jq_sha}" \
    "${baseline}" "${source_sha}" "${run_id}" "${root_file}"
  root="$(cat -- "${root_file}")"

  case_dir="${test_root}/byte-altered"
  mkdir -p -- "${case_dir}"
  mutated="${case_dir}/${V2_PAYLOAD_NAME}"
  cp -- "${package}" "${mutated}"
  printf x >> "${mutated}"
  chmod 0600 -- "${mutated}"
  v2_expect_transport_failure byte-altered "${mutated}" "${package_sha}" "${package_bytes}" \
    "${transport_sha}" "${jq_sha}" "${source_sha}" "${run_id}" "${test_root}"

  for mutation in manifest-missing manifest-duplicate extra-root symlink payload-missing hash-internal; do
    case_dir="${test_root}/${mutation}"
    case_root="${case_dir}/${V2_PAYLOAD_ROOT_NAME}"
    mkdir -p -- "${case_dir}"
    cp -a -- "${root}" "${case_root}"
    case "${mutation}" in
      manifest-missing) rm -- "${case_root}/release-manifest.json" ;;
      manifest-duplicate)
        mkdir -p -- "${case_root}/duplicate"
        cp -- "${case_root}/release-manifest.json" \
          "${case_root}/duplicate/release-manifest.json"
        ;;
      extra-root)
        mkdir -p -- "${case_dir}/unexpected-root"
        printf '%s\n' unexpected > "${case_dir}/unexpected-root/file.txt"
        ;;
      symlink) ln -s /tmp/outside "${case_root}/outside-link" ;;
      payload-missing) rm -- "${case_root}/release-images.tar" ;;
      hash-internal) printf x >> "${case_root}/release-images.tar" ;;
    esac
    mutated="${case_dir}/${V2_PAYLOAD_NAME}"
    if [[ "${mutation}" == extra-root ]]; then
      tar -cf "${mutated}" -C "${case_dir}" "${V2_PAYLOAD_ROOT_NAME}" unexpected-root
    else
      tar -cf "${mutated}" -C "${case_dir}" "${V2_PAYLOAD_ROOT_NAME}"
    fi
    chmod 0600 -- "${mutated}"
    actual_sha="$(v2_sha256 "${mutated}")"
    actual_bytes="$(stat -c %s -- "${mutated}")"
    v2_expect_transport_failure "${mutation}" "${mutated}" "${actual_sha}" "${actual_bytes}" \
      "${transport_sha}" "${jq_sha}" "${source_sha}" "${run_id}" "${test_root}"
  done

  case_dir="${test_root}/path-traversal"
  mkdir -p -- "${case_dir}"
  mutated="${case_dir}/${V2_PAYLOAD_NAME}"
  tar -cf "${mutated}" --transform='s#^candidate-root#../candidate-root#' \
    -C "$(dirname -- "${root}")" "${V2_PAYLOAD_ROOT_NAME}"
  chmod 0600 -- "${mutated}"
  actual_sha="$(v2_sha256 "${mutated}")"
  actual_bytes="$(stat -c %s -- "${mutated}")"
  v2_expect_transport_failure path-traversal "${mutated}" "${actual_sha}" "${actual_bytes}" \
    "${transport_sha}" "${jq_sha}" "${source_sha}" "${run_id}" "${test_root}"

  v2_artifact_contract payload-envelope-tests "${envelope}" "${source_sha}" \
    "${run_id}" "${artifact_name}" \
    "${RUNNER_TEMP:-/tmp}/topsdojob-v2-envelope-tests-${run_id}-$$"
  rm -rf -- "${test_root}"
  [[ ! -e "${test_root}" ]] || v2_die "residuos nos testes negativos"
  v2_log "TRANSPORT_NEGATIVE_TESTS=15/15 DIAGNOSTIC_BEFORE_CLEANUP=PASS RESIDUES=0"
}
