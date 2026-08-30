#!/usr/bin/env bash

set -Eeuo pipefail

V2_LIB_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
V2_ROOT="$(cd -- "${V2_LIB_DIR}/../../.." && pwd -P)"
V2_LOCK="${V2_ROOT}/deploy/v2/images.lock.json"
V2_COMPOSE_FILE="${V2_ROOT}/deploy/v2/compose.yml"
V2_PLATFORM="linux/amd64"

v2_log() {
  printf 'PIPELINE_V2 %s\n' "$*"
}

v2_die() {
  printf 'PIPELINE_V2_ERROR %s\n' "$*" >&2
  exit 1
}

v2_require_command() {
  command -v "$1" >/dev/null 2>&1 || v2_die "comando obrigatorio ausente: $1"
}

v2_sha256() {
  sha256sum -- "$1" | awk '{print $1}'
}

v2_lock_value() {
  local section="$1" key="$2" field="$3"
  awk -v section="\"${section}\"" -v key="\"${key}\"" -v field="\"${field}\"" '
    $0 ~ "^  " section ":" { in_section=1; next }
    in_section && $0 ~ "^  },?$" { in_section=0 }
    in_section && $0 ~ "^    " key ":" { in_key=1; next }
    in_key && $0 ~ "^    },?$" { in_key=0 }
    in_key && index($0, field ":") {
      value=$0
      sub("^[^:]+:[[:space:]]*\"", "", value)
      sub("\"[,]?[[:space:]]*$", "", value)
      print value
      exit
    }
  ' "${V2_LOCK}"
}

v2_image_ref() {
  local value
  value="$(v2_lock_value images "$1" reference)"
  [[ "${value}" == *@sha256:* ]] || v2_die "referencia sem digest para $1"
  printf '%s\n' "${value}"
}

v2_tool_value() {
  v2_lock_value tools "$1" "$2"
}

v2_set_synthetic_credentials() {
  local scope="$1" suffix
  [[ "${scope}" =~ ^[a-z0-9-]+$ ]] || v2_die "escopo sintetico invalido"
  suffix="$(printf '%s' "${scope}:${GITHUB_RUN_ID:-local}:${GITHUB_RUN_ATTEMPT:-1}:$$" |
    sha256sum | cut -c1-20)"
  printf -v V2_DB_PASSWORD 'v2db-%s-%s' "${scope}" "${suffix}"
  printf -v V2_MINIO_ACCESS_KEY 'v2access%s' "${suffix}"
  printf -v V2_MINIO_SECRET_KEY 'v2secret-%s-%s' "${scope}" "${suffix}"
  export V2_DB_PASSWORD V2_MINIO_ACCESS_KEY V2_MINIO_SECRET_KEY
}

v2_install_tools() {
  local compose_url compose_sha actionlint_url actionlint_sha archive
  mkdir -p -- "${V2_TOOLS_DIR}/bin"
  compose_url="$(v2_tool_value compose url)"
  compose_sha="$(v2_tool_value compose sha256)"
  actionlint_url="$(v2_tool_value actionlint url)"
  actionlint_sha="$(v2_tool_value actionlint sha256)"

  curl --fail --silent --show-error --location \
    "${compose_url}" --output "${V2_TOOLS_DIR}/bin/docker-compose-v2"
  printf '%s  %s\n' "${compose_sha}" "${V2_TOOLS_DIR}/bin/docker-compose-v2" |
    sha256sum --check --status
  chmod 0500 "${V2_TOOLS_DIR}/bin/docker-compose-v2"

  archive="${V2_TOOLS_DIR}/actionlint.tar.gz"
  curl --fail --silent --show-error --location "${actionlint_url}" --output "${archive}"
  printf '%s  %s\n' "${actionlint_sha}" "${archive}" | sha256sum --check --status
  tar -xzf "${archive}" -C "${V2_TOOLS_DIR}/bin" actionlint
  chmod 0500 "${V2_TOOLS_DIR}/bin/actionlint"
  rm -f -- "${archive}"
}

v2_bootstrap_engine() {
  local cli_ref dind_ref cli_container api_port expected_version attempt reference expected repo_digests
  v2_require_command docker
  v2_require_command curl
  v2_require_command sha256sum
  v2_require_command tar
  v2_require_command awk
  [[ -f "${V2_LOCK}" ]] || v2_die "lock de imagens ausente"

  export V2_HOST_DOCKER="$(command -v docker)"
  export V2_RUN_ID="${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}-${V2_RUN_NUMBER:-0}-$$"
  export V2_TOOLS_DIR="${RUNNER_TEMP:-/tmp}/topsdojob-v2-tools-${V2_RUN_ID}"
  export V2_DIND_CONTAINER="topsdojob-v2-dind-${V2_RUN_ID//[^a-zA-Z0-9_.-]/-}"
  mkdir -p -- "${V2_TOOLS_DIR}/bin"

  cli_ref="$(v2_image_ref dockerCli)"
  dind_ref="$(v2_image_ref dockerDind)"
  "${V2_HOST_DOCKER}" pull --platform "${V2_PLATFORM}" "${cli_ref}" >/dev/null
  "${V2_HOST_DOCKER}" pull --platform "${V2_PLATFORM}" "${dind_ref}" >/dev/null
  for reference in "${cli_ref}" "${dind_ref}"; do
    expected="${reference##*@}"
    repo_digests="$("${V2_HOST_DOCKER}" image inspect "${reference}" \
      --format '{{join .RepoDigests "\n"}}')"
    printf '%s\n' "${repo_digests}" | grep -Fq -- "@${expected}" ||
      v2_die "digest de bootstrap divergente: ${reference}"
  done

  cli_container="${V2_DIND_CONTAINER}-cli"
  "${V2_HOST_DOCKER}" create --name "${cli_container}" "${cli_ref}" true >/dev/null
  "${V2_HOST_DOCKER}" cp "${cli_container}:/usr/local/bin/docker" \
    "${V2_TOOLS_DIR}/bin/docker"
  "${V2_HOST_DOCKER}" rm "${cli_container}" >/dev/null
  chmod 0500 "${V2_TOOLS_DIR}/bin/docker"

  "${V2_HOST_DOCKER}" run --detach --privileged \
    --name "${V2_DIND_CONTAINER}" \
    --label topsdojob.pipeline=v2 \
    --env DOCKER_TLS_CERTDIR= \
    --volume "${V2_ROOT}:${V2_ROOT}" \
    --volume "${RUNNER_TEMP:-/tmp}:${RUNNER_TEMP:-/tmp}" \
    --publish 127.0.0.1::2375 \
    "${dind_ref}" \
    --host=tcp://0.0.0.0:2375 --host=unix:///var/run/docker.sock >/dev/null

  for attempt in $(seq 1 90); do
    if "${V2_HOST_DOCKER}" exec "${V2_DIND_CONTAINER}" docker info >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  "${V2_HOST_DOCKER}" exec "${V2_DIND_CONTAINER}" docker info >/dev/null 2>&1 ||
    v2_die "daemon Docker hermetico nao iniciou"

  api_port="$("${V2_HOST_DOCKER}" port "${V2_DIND_CONTAINER}" 2375/tcp | awk -F: 'END {print $NF}')"
  [[ "${api_port}" =~ ^[0-9]+$ ]] || v2_die "porta do daemon Docker invalida"
  export DOCKER_HOST="tcp://127.0.0.1:${api_port}"
  export PATH="${V2_TOOLS_DIR}/bin:${PATH}"

  expected_version="$(v2_tool_value docker version)"
  [[ "$(docker version --format '{{.Client.Version}}')" == "${expected_version}" ]]
  [[ "$(docker version --format '{{.Server.Version}}')" == "${expected_version}" ]]
  v2_install_tools
  [[ "$(docker-compose-v2 version --short)" == "$(v2_tool_value compose version)" ]]
  v2_log "DOCKER_VERSION=${expected_version} COMPOSE_VERSION=$(docker-compose-v2 version --short)"
}

v2_stop_engine() {
  local exit_code="${1:-0}"
  if [[ -n "${V2_HOST_DOCKER:-}" && -n "${V2_DIND_CONTAINER:-}" ]]; then
    "${V2_HOST_DOCKER}" rm -f "${V2_DIND_CONTAINER}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${V2_TOOLS_DIR:-}" && -d "${V2_TOOLS_DIR}" ]]; then
    rm -rf -- "${V2_TOOLS_DIR}"
  fi
  return "${exit_code}"
}

v2_pull_locked_images() {
  local reference expected repo_digests
  while IFS= read -r reference; do
    [[ "${reference}" == *@sha256:* ]] || v2_die "imagem sem digest: ${reference}"
    docker pull --platform "${V2_PLATFORM}" "${reference}" >/dev/null
    expected="${reference##*@}"
    repo_digests="$(docker image inspect "${reference}" --format '{{join .RepoDigests "\n"}}')"
    grep -Fq -- "@${expected}" <<<"${repo_digests}" ||
      v2_die "digest local divergente: ${reference}"
  done < <(sed -n 's/^[[:space:]]*"reference": "\([^"]*\)"[,]\{0,1\}$/\1/p' "${V2_LOCK}")
}

v2_export_images() {
  export V2_POSTGRES_IMAGE="$(v2_image_ref postgres)"
  export V2_FLYWAY_IMAGE="$(v2_image_ref flyway)"
  export V2_MAVEN_IMAGE="$(v2_image_ref maven)"
  export V2_JRE_IMAGE="$(v2_image_ref jre)"
  export V2_NODE_IMAGE="$(v2_image_ref node)"
  export V2_NGINX_IMAGE="$(v2_image_ref nginx)"
  export V2_MINIO_IMAGE="$(v2_image_ref minio)"
  export V2_MINIO_CLIENT_IMAGE="$(v2_image_ref minioClient)"
  export V2_MAILPIT_IMAGE="$(v2_image_ref mailpit)"
  export V2_GITLEAKS_IMAGE="$(v2_image_ref gitleaks)"
}

v2_tag_test_images() {
  docker tag "${V2_POSTGRES_IMAGE}" postgres:17-alpine
  docker tag "${V2_POSTGRES_IMAGE}" postgres:17.10-alpine
  docker tag "${V2_FLYWAY_IMAGE}" flyway/flyway:12.10.0
  docker tag "${V2_FLYWAY_IMAGE}" flyway/flyway:12.10.0-alpine
}

v2_compose() {
  docker-compose-v2 --file "${V2_COMPOSE_FILE}" --project-name "${V2_PROJECT_NAME}" "$@"
}

v2_node() {
  docker run --rm --network none \
    --user "$(id -u):$(id -g)" \
    --volume "${V2_ROOT}:${V2_ROOT}" \
    --volume "${RUNNER_TEMP:-/tmp}:${RUNNER_TEMP:-/tmp}" \
    --workdir "${V2_ROOT}" \
    "${V2_NODE_IMAGE}" node "$@" </dev/null
}

v2_assert_no_resources() {
  local containers networks volumes
  containers="$(docker ps -aq --filter "label=com.docker.compose.project=${V2_PROJECT_NAME}")"
  networks="$(docker network ls -q --filter "label=com.docker.compose.project=${V2_PROJECT_NAME}")"
  volumes="$(docker volume ls -q --filter "label=com.docker.compose.project=${V2_PROJECT_NAME}")"
  [[ -z "${containers}" ]] || v2_die "containers residuais: ${containers}"
  [[ -z "${networks}" ]] || v2_die "redes residuais: ${networks}"
  [[ -z "${volumes}" ]] || v2_die "volumes residuais: ${volumes}"
}

v2_closed_stdin_contract() {
  local marker="$1"
  sh -c 'if IFS= read -r _; then exit 91; fi' </dev/null
  printf '%s\n' 'STDIN_EOF=OK' > "${marker}"
}
