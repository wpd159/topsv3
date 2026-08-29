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

v2_build_release_artifact() {
  local output_dir="$1" source_sha="$2" archive
  [[ ! -e "${output_dir}" ]] || v2_die "diretorio de artefato ja existe"
  mkdir -m 0700 -p -- "${output_dir}"
  export V2_PROJECT_NAME="topsdojob-v2-build-${source_sha:0:12}-${V2_RUN_ID//[^a-zA-Z0-9_.-]/-}"
  export V2_RUNTIME_DIR="${RUNNER_TEMP:-/tmp}/${V2_PROJECT_NAME}"
  v2_set_synthetic_credentials build
  mkdir -p -- "${V2_RUNTIME_DIR}/certs/minio" "${V2_RUNTIME_DIR}/reports"
  v2_set_release_images "${source_sha}"

  v2_log "BUILD_ONCE sha=${source_sha}"
  v2_compose build --no-cache backend frontend gateway
  docker image inspect "${V2_BACKEND_IMAGE}" "${V2_FRONTEND_IMAGE}" \
    "${V2_GATEWAY_IMAGE}" >/dev/null

  archive="${output_dir}/release-images.tar"
  docker save --output "${archive}" \
    "${V2_BACKEND_IMAGE}" "${V2_FRONTEND_IMAGE}" "${V2_GATEWAY_IMAGE}"
  [[ -s "${archive}" ]] || v2_die "arquivo de imagens vazio"

  v2_node "scripts/deploy/v2/contract.mjs" manifest-create \
    "${output_dir}/release-manifest.json" "${source_sha}" "${archive}" \
    "${V2_BACKEND_IMAGE}" "$(docker image inspect "${V2_BACKEND_IMAGE}" --format '{{.Id}}')" \
    "${V2_FRONTEND_IMAGE}" "$(docker image inspect "${V2_FRONTEND_IMAGE}" --format '{{.Id}}')" \
    "${V2_GATEWAY_IMAGE}" "$(docker image inspect "${V2_GATEWAY_IMAGE}" --format '{{.Id}}')"
  (
    cd -- "${output_dir}"
    sha256sum -- release-manifest.json > release-manifest.sha256
  )
  chmod 0600 "${archive}" "${output_dir}/release-manifest.json" \
    "${output_dir}/release-manifest.sha256"
  v2_node "scripts/deploy/v2/contract.mjs" manifest-verify "${output_dir}"
  v2_log "ARTIFACT_CREATED sha256=$(v2_sha256 "${archive}")"
  rm -rf -- "${V2_RUNTIME_DIR}"
}

v2_load_release_artifact() {
  local bundle_dir="$1" expected_sha="$2" image_name expected_id actual_id
  [[ -d "${bundle_dir}" ]] || v2_die "bundle ausente"
  if find "${bundle_dir}" -type l -print -quit | grep -q .; then
    v2_die "bundle contem symlink"
  fi
  (cd -- "${bundle_dir}" && sha256sum --check --status release-manifest.sha256)
  v2_node "scripts/deploy/v2/contract.mjs" manifest-verify \
    "${bundle_dir}" "${expected_sha}"
  docker load --input "${bundle_dir}/release-images.tar" >/dev/null

  while IFS='|' read -r image_name expected_id; do
    [[ -n "${image_name}" && -n "${expected_id}" ]] || v2_die "imagem invalida no manifesto"
    actual_id="$(docker image inspect "${image_name}" --format '{{.Id}}')"
    [[ "${actual_id}" == "${expected_id}" ]] ||
      v2_die "imagem carregada diverge do manifesto: ${image_name}"
  done < <(v2_node "scripts/deploy/v2/contract.mjs" manifest-images "${bundle_dir}")
  v2_set_release_images "${expected_sha}"
  v2_log "ARTIFACT_VERIFIED sha=$(v2_sha256 "${bundle_dir}/release-images.tar")"
}
