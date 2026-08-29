#!/usr/bin/env bash

set -Eeuo pipefail

V2_CONTROLLER_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "${V2_CONTROLLER_DIR}/lib.sh"
# shellcheck source=artifact.sh
source "${V2_CONTROLLER_DIR}/artifact.sh"
# shellcheck source=lab.sh
source "${V2_CONTROLLER_DIR}/lab.sh"

V2_MODE="${1:-}"
V2_CLEANUP_ACTIVE=0

v2_cleanup() {
  local rc=$?
  trap - EXIT INT TERM
  set +e
  if [[ "${V2_CLEANUP_ACTIVE}" -eq 1 && -n "${V2_PROJECT_NAME:-}" ]]; then
    local container
    container="$(v2_compose ps -q postgres 2>/dev/null || true)"
    [[ -z "${container}" ]] || docker unpause "${container}" >/dev/null 2>&1 || true
    v2_compose down --volumes --remove-orphans --timeout 30 >/dev/null 2>&1 || true
    while IFS= read -r container; do
      [[ -n "${container}" ]] || continue
      docker stop --time 30 "${container}" >/dev/null 2>&1 || true
      if [[ "$(docker inspect "${container}" --format '{{.State.Running}}' 2>/dev/null)" == "false" ]]; then
        docker rm "${container}" >/dev/null 2>&1 || true
      fi
    done < <(docker ps -aq --filter "name=${V2_PROJECT_NAME}-" 2>/dev/null || true)
    v2_assert_no_resources >/dev/null 2>&1 || rc=1
  fi
  v2_clean_generated_files >/dev/null 2>&1 || rc=1
  v2_stop_engine "${rc}" || true
  exit "${rc}"
}

trap v2_cleanup EXIT INT TERM

v2_static_contracts() {
  export V2_PROJECT_NAME="${V2_PROJECT_NAME:-topsdojob-v2-contract}"
  export V2_RUNTIME_DIR="${V2_RUNTIME_DIR:-${RUNNER_TEMP:-/tmp}/topsdojob-v2-contract}"
  v2_set_synthetic_credentials contract
  export V2_BACKEND_IMAGE="${V2_BACKEND_IMAGE:-topsdojob-v2-backend:contract}"
  export V2_FRONTEND_IMAGE="${V2_FRONTEND_IMAGE:-topsdojob-v2-frontend:contract}"
  export V2_GATEWAY_IMAGE="${V2_GATEWAY_IMAGE:-topsdojob-v2-gateway:contract}"

  v2_node "scripts/deploy/v2/contract.mjs" contract
  actionlint "${V2_ROOT}/.github/workflows/pipeline-production-v2.yml"
  v2_compose config --quiet
  git -C "${V2_ROOT}" diff --check
  v2_log "STATIC_CONTRACTS=OK"
}

v2_scan_secrets() {
  docker run --rm --network none \
    --volume "${V2_ROOT}:/repo:ro" --workdir /repo \
    "${V2_GITLEAKS_IMAGE}" dir . --redact --no-banner
  v2_log "GITLEAKS=OK"
}

v2_record_versions() {
  local output="$1"
  {
    printf 'docker=%s\n' "$(docker version --format '{{.Server.Version}}')"
    printf 'compose=%s\n' "$(docker-compose-v2 version --short)"
    docker run --rm --network none "${V2_MAVEN_IMAGE}" mvn --version | sed -n '1,2p'
    docker run --rm --network none "${V2_JRE_IMAGE}" java -version 2>&1 | sed -n '1p'
    docker run --rm --network none "${V2_NODE_IMAGE}" node --version
    docker run --rm --network none "${V2_NODE_IMAGE}" npm --version
    docker run --rm --network none "${V2_POSTGRES_IMAGE}" postgres --version
    docker run --rm --network none "${V2_FLYWAY_IMAGE}" -v | sed -n '1p'
    docker run --rm --network none "${V2_NGINX_IMAGE}" nginx -v 2>&1
  } > "${output}"
}

v2_negative_file_contracts() {
  local bundle="$1" expected_sha="$2" temporary crlf symlink marker failure_marker
  temporary="${V2_RUNTIME_DIR}/negative"
  mkdir -m 0700 -p -- "${temporary}"

  crlf="${temporary}/controller-crlf.sh"
  awk '{printf "%s\r\n", $0}' "${V2_ROOT}/scripts/deploy/v2/controller.sh" > "${crlf}"
  if v2_node "scripts/deploy/v2/contract.mjs" file-policy "${crlf}" >/dev/null 2>&1; then
    v2_die "arquivo CRLF foi aceito"
  fi

  symlink="${temporary}/controller-link.sh"
  ln -s "${V2_ROOT}/scripts/deploy/v2/controller.sh" "${symlink}"
  if v2_node "scripts/deploy/v2/contract.mjs" file-policy "${symlink}" >/dev/null 2>&1; then
    v2_die "symlink foi aceito"
  fi

  mkdir -m 0700 -- "${temporary}/bad-bundle"
  cp -- "${bundle}/release-manifest.json" "${temporary}/bad-bundle/release-manifest.json"
  printf '%s\n' 'tampered' > "${temporary}/bad-bundle/release-images.tar"
  if v2_node "scripts/deploy/v2/contract.mjs" manifest-verify \
    "${temporary}/bad-bundle" "${expected_sha}" >/dev/null 2>&1; then
    v2_die "hash divergente foi aceito"
  fi

  marker="${temporary}/stdin-marker"
  v2_closed_stdin_contract "${marker}"
  [[ "$(cat "${marker}")" == "STDIN_EOF=OK" ]]
  failure_marker="${temporary}/failure-marker"
  if bash -c 'set -e; false; touch "$1"' _ "${failure_marker}" </dev/null; then
    v2_die "falha de filho nao foi propagada"
  fi
  [[ ! -e "${failure_marker}" ]]
  v2_log "CRLF=BLOCKED SYMLINK=BLOCKED HASH_MISMATCH=BLOCKED STDIN_EOF=OK FAILURE_PROPAGATED=OK"
}

v2_run_backend_tests() {
  local evidence_dir="$1" list_file socket_cli flag class_name v048_class=""
  local fixtures v048_backend endpoint
  local -a env_args=()
  list_file="${evidence_dir}/critical-tests.json"
  fixtures="${V2_RUNTIME_DIR}/test-fixtures"
  v048_backend="${V2_RUNTIME_DIR}/v048-backend"
  endpoint="${V2_LOCAL_MINIO_ENDPOINT:?endpoint MinIO local ausente}"
  [[ -s "${fixtures}/source-snapshot.dump" ]] || v2_die "snapshot sintetico ausente"
  v2_node "scripts/deploy/v2/contract.mjs" critical-list "${list_file}" > \
    "${evidence_dir}/critical-tests.txt"
  while IFS='|' read -r flag class_name; do
    [[ -n "${flag}" && -n "${class_name}" ]] || continue
    if [[ "${flag}" == "V048_POSTGRES17_ENABLED" ]]; then
      v048_class="${class_name}"
      env_args+=(--env "${flag}=false")
    else
      env_args+=(--env "${flag}=true")
    fi
  done < "${evidence_dir}/critical-tests.txt"
  [[ "${#env_args[@]}" -gt 0 && -n "${v048_class}" ]] ||
    v2_die "lista de testes criticos incompleta"

  env_args+=(
    --env "JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=${V2_RUNTIME_DIR}/certs/truststore.p12 -Djavax.net.ssl.trustStorePassword=pipeline-v2-trust -Djavax.net.ssl.trustStoreType=PKCS12"
    --env "R2_ENDPOINT=${endpoint}"
    --env R2_REGION=us-east-1
    --env "R2_ACCESS_KEY=${V2_MINIO_ACCESS_KEY}"
    --env "R2_SIGNING_VALUE=${V2_MINIO_SECRET_KEY}"
    --env R2_PUBLIC_MEDIA_BUCKET=topsdojob-v2-public
    --env R2_PRIVATE_MEDIA_BUCKET=topsdojob-v2-private
    --env R2_DOCUMENT_BUCKET=topsdojob-v2-documents
    --env R2_PUBLIC_MEDIA_PREFIX=hml/midias-aprovadas/
    --env R2_PRIVATE_MEDIA_PREFIX=hml/midias-pendentes/
    --env R2_DOCUMENT_PREFIX=hml/documentos/
    --env "R2_PUBLIC_BASE_URL=${endpoint}/topsdojob-v2-public"
    --env "R2_PUBLIC_MEDIA_DRY_RUN_INPUT=${fixtures}/r2-public-input.tsv"
    --env "R2_PUBLIC_MEDIA_DRY_RUN_OUTPUT=${fixtures}/r2-public-output.tsv"
    --env "R2_PRIVATE_MEDIA_PLAN_INPUT=${fixtures}/r2-private-input.tsv"
    --env "R2_PRIVATE_MEDIA_PLAN_OUTPUT=${fixtures}/r2-private-output.tsv"
    --env "R2_KYC_DRY_RUN_INPUT=${fixtures}/r2-kyc-input.tsv"
    --env "R2_KYC_DRY_RUN_OUTPUT=${fixtures}/r2-kyc-output.tsv"
    --env "R2_KYC_DRY_RUN_CACHE_DIR=${fixtures}/kyc-cache"
    --env R2_KYC_DESTINATION_SCOPE=pipeline-v2
    --env "R2_IMPORT_SOURCE_ENDPOINT=${endpoint}"
    --env R2_IMPORT_SOURCE_REGION=us-east-1
    --env "R2_IMPORT_SOURCE_ACCESS_KEY=${V2_MINIO_ACCESS_KEY}"
    --env "R2_IMPORT_SOURCE_SIGNING_VALUE=${V2_MINIO_SECRET_KEY}"
    --env R2_IMPORT_SOURCE_PUBLIC_BUCKET=topsdojob-v2-public
    --env R2_IMPORT_SOURCE_PRIVATE_BUCKET=topsdojob-v2-private
    --env R2_IMPORT_SOURCE_DOCUMENT_BUCKET=topsdojob-v2-documents
    --env R2_IMPORT_SOURCE_PUBLIC_PREFIX=hml/midias-aprovadas/
    --env R2_IMPORT_SOURCE_PRIVATE_PREFIX=hml/midias-pendentes/
    --env R2_IMPORT_SOURCE_DOCUMENT_PREFIX=hml/documentos/
    --env "IMPORTADOR_BASE_SNAPSHOT_DUMP=${fixtures}/source-snapshot.dump"
    --env "IMPORTADOR_BASE_PRIVATE_MEDIA_TSV=${fixtures}/import-private-media.tsv"
    --env "IMPORTADOR_BASE_KYC_TSV=${fixtures}/import-kyc.tsv"
    --env IMPORTADOR_BASE_SNAPSHOT_AT=2026-08-29T12:00:00Z
    --env IMPORTADOR_BASE_SNAPSHOT_ID=pipeline-v2-synthetic
    --env IMPORTADOR_BASE_SNAPSHOT_FINGERPRINT=4a66718f2c112a85fdf8c40737e88b7b376c29625f8ad6639fbc98a7e253a9dd
    --env IMPORTADOR_BASE_R2_PUBLIC_MEDIA_BUCKET=topsdojob-v2-public
    --env IMPORTADOR_BASE_R2_PUBLIC_MEDIA_PREFIX=hml/midias-aprovadas/
    --env IMPORTADOR_BASE_R2_PRIVATE_MEDIA_BUCKET=topsdojob-v2-private
    --env IMPORTADOR_BASE_R2_PRIVATE_MEDIA_PREFIX=hml/midias-pendentes/
    --env IMPORTADOR_BASE_R2_PRESERVED_PUBLIC_BUCKET=topsdojob-v2-public
    --env "IMPORTADOR_BASE_R2_PRESERVED_PUBLIC_BASE_URL=${endpoint}/topsdojob-v2-public"
    --env IMPORTADOR_BASE_R2_PRESERVED_PUBLIC_PREFIX=hml/midias-aprovadas/
    --env IMPORTADOR_BASE_R2_DOCUMENT_BUCKET=topsdojob-v2-documents
    --env IMPORTADOR_BASE_R2_DOCUMENT_PREFIX=hml/documentos/
  )

  socket_cli="${V2_TOOLS_DIR}/bin/docker"
  mkdir -m 0700 -p -- "${V2_RUNTIME_DIR}/m2"
  docker run --rm --network none \
    --volume "${V2_ROOT}:${V2_ROOT}:ro" \
    --volume "${V2_RUNTIME_DIR}:${V2_RUNTIME_DIR}" \
    "${V2_MAVEN_IMAGE}" sh -euc '
      cp -a "$1/backend" "$2"
      rm -rf "$2/target"
      rm -f "$2"/src/main/resources/db/migration/V05[1-3]__*.sql
      test "$(find "$2/src/main/resources/db/migration" -maxdepth 1 -name "V*.sql" | wc -l)" -eq 50
    ' _ "${V2_ROOT}" "${v048_backend}" </dev/null
  docker run --rm --network host \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    --volume "${socket_cli}:/usr/local/bin/docker:ro" \
    --volume "${V2_ROOT}:${V2_ROOT}" \
    --volume "${V2_RUNTIME_DIR}:${V2_RUNTIME_DIR}" \
    --volume "${V2_RUNTIME_DIR}/m2:/root/.m2" \
    --workdir "${V2_ROOT}/backend" \
    "${env_args[@]}" \
    "${V2_MAVEN_IMAGE}" \
    mvn --batch-mode --no-transfer-progress verify </dev/null

  docker run --rm --network host \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    --volume "${socket_cli}:/usr/local/bin/docker:ro" \
    --volume "${V2_RUNTIME_DIR}:${V2_RUNTIME_DIR}" \
    --volume "${V2_RUNTIME_DIR}/m2:/root/.m2" \
    --workdir "${v048_backend}" \
    --env V048_POSTGRES17_ENABLED=true \
    "${V2_MAVEN_IMAGE}" \
    mvn --batch-mode --no-transfer-progress \
      "-Dtest=${v048_class##*.}" test </dev/null
  v2_node "scripts/deploy/v2/contract.mjs" critical-report \
    "${list_file}" "${evidence_dir}/critical-results.json" \
    "${V2_ROOT}/backend/target" "${v048_backend}/target"
  v2_log "BACKEND_VERIFY=OK CRITICAL_SKIPPED=0"
}

v2_run_frontend_tests() {
  docker run --rm \
    --volume "${V2_ROOT}:${V2_ROOT}" \
    --workdir "${V2_ROOT}/frontend" \
    --env HOME=/tmp/pipeline-v2-home \
    --env NEXT_TELEMETRY_DISABLED=1 \
    "${V2_NODE_IMAGE}" /bin/sh -ec '
      npm ci --cache /tmp/npm-cache
      npm run test:json-ld-security
      npm run test:public-ordering
      npm run test:public-http-states
      npm run test:public-data-access
      npm run test:public-search
      npm run test:public-auth-security
      npm run test:health-readiness
      npm run test:sharp-security
      npm run test:wizard-cache
      npm run test:search-indexing
      npm run test:seo-ai
      node scripts/test-public-seo-critical.mjs
      node scripts/test-public-listing-video.mjs
      node scripts/test-public-listing-video-poster.mjs
      node scripts/test-public-media-gallery.mjs
      node scripts/test-public-video-agegate.mjs
      node scripts/test-story-video-audio.mjs
      ./node_modules/.bin/tsc --noEmit
      npm run lint
      rm -rf node_modules /tmp/npm-cache /tmp/pipeline-v2-home
    ' </dev/null
  v2_log "FRONTEND_TESTS=OK REBUILD=0"
}

v2_clean_generated_files() {
  if command -v docker >/dev/null 2>&1 \
      && [[ -n "${V2_MAVEN_IMAGE:-}" ]] \
      && docker image inspect "${V2_MAVEN_IMAGE}" >/dev/null 2>&1; then
    docker run --rm --network none \
      --volume "${V2_ROOT}:${V2_ROOT}" \
      --volume "${RUNNER_TEMP:-/tmp}:${RUNNER_TEMP:-/tmp}" \
      "${V2_MAVEN_IMAGE}" sh -euc '
        rm -rf "$1/backend/target" "$1/frontend/node_modules" "$1/frontend/.next"
        rm -f "$1/frontend/tsconfig.tsbuildinfo"
        if [ -n "$2" ] && [ -e "$2" ]; then rm -rf "$2"; fi
      ' _ "${V2_ROOT}" "${V2_RUNTIME_DIR:-}" </dev/null || return 1
  fi
  if [[ -n "${V2_RUNTIME_DIR:-}" && -e "${V2_RUNTIME_DIR}" ]]; then
    rm -rf -- "${V2_RUNTIME_DIR}" || return 1
  fi
}

v2_build_mode() {
  local output_dir="$2" source_sha="$3"
  [[ "$#" -eq 3 ]] || v2_die "uso: controller.sh build OUTPUT SHA"
  [[ "$(git -C "${V2_ROOT}" rev-parse HEAD)" == "${source_sha}" ]]
  [[ -z "$(git -C "${V2_ROOT}" status --short)" ]] || v2_die "checkout de build sujo"
  export V2_RUN_NUMBER=0
  v2_bootstrap_engine
  V2_CLEANUP_ACTIVE=1
  v2_export_images
  v2_pull_locked_images
  v2_static_contracts
  v2_scan_secrets
  v2_record_versions "${RUNNER_TEMP:-/tmp}/pipeline-v2-build-versions.txt"
  v2_build_release_artifact "${output_dir}" "${source_sha}"
  cp -- "${RUNNER_TEMP:-/tmp}/pipeline-v2-build-versions.txt" "${output_dir}/tool-versions.txt"
  chmod 0600 "${output_dir}/tool-versions.txt"
  v2_log "BUILD_RESULT=PASS ARTIFACT_UNIQUE=backend,frontend,gateway"
}

v2_verify_mode() {
  local bundle="$2" source_sha="$3" run_number="$4" evidence_dir="$5"
  [[ "$#" -eq 5 ]] || v2_die "uso: controller.sh verify BUNDLE SHA RUN EVIDENCE"
  [[ "${run_number}" =~ ^[123]$ ]] || v2_die "runner deve ser 1, 2 ou 3"
  [[ "$(git -C "${V2_ROOT}" rev-parse HEAD)" == "${source_sha}" ]]
  export V2_RUN_NUMBER="${run_number}"
  v2_bootstrap_engine
  V2_CLEANUP_ACTIVE=1
  v2_export_images
  v2_pull_locked_images
  v2_tag_test_images
  v2_set_release_images "${source_sha}"
  export V2_PROJECT_NAME="topsdojob-v2-run-${run_number}-${V2_RUN_ID//[^a-zA-Z0-9_.-]/-}"
  export V2_RUNTIME_DIR="${RUNNER_TEMP:-/tmp}/${V2_PROJECT_NAME}"
  v2_set_synthetic_credentials "run-${run_number}"
  mkdir -m 0700 -p -- "${V2_RUNTIME_DIR}" "${evidence_dir}"

  v2_static_contracts
  v2_scan_secrets
  v2_load_release_artifact "${bundle}" "${source_sha}"
  v2_negative_file_contracts "${bundle}" "${source_sha}"
  v2_record_versions "${evidence_dir}/tool-versions.txt"
  v2_prepare_lab "${evidence_dir}"
  v2_run_backend_tests "${evidence_dir}"
  v2_run_frontend_tests
  v2_run_lab "${evidence_dir}"
  v2_compose down --volumes --remove-orphans --timeout 30
  v2_assert_no_resources
  v2_clean_generated_files
  [[ ! -e "${V2_RUNTIME_DIR}" ]]
  git -C "${V2_ROOT}" diff --check
  printf 'RUN_%s=PASS\nCRITICAL_SKIPPED=0\nPRODUCTION_ACCESS=0\n' \
    "${run_number}" > "${evidence_dir}/run-result.txt"
  v2_log "RUN_${run_number}=PASS CRITICAL_SKIPPED=0 RESIDUALS=0 PRODUCTION_ACCESS=0"
}

case "${V2_MODE}" in
  build) v2_build_mode "$@" ;;
  verify) v2_verify_mode "$@" ;;
  *) v2_die "modo permitido nesta fase: build interno ou verify" ;;
esac
