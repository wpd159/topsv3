#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

V2_PAYLOAD_NAME=candidate-payload.tar
V2_PAYLOAD_ROOT=candidate-root
V2_PAYLOAD_LAYOUT_VERSION=2

v2_transport_log() {
  printf 'PIPELINE_V2_TRANSPORT %s\n' "$*"
}

v2_transport_die() {
  printf 'PIPELINE_V2_TRANSPORT_ERROR %s\n' "$*" >&2
  exit 1
}

v2_transport_sha256() {
  sha256sum -- "$1" | awk '{print $1}'
}

v2_transport_bytes() {
  stat -c %s -- "$1"
}

v2_transport_require_sha() {
  [[ "$1" =~ ^[a-f0-9]{64}$ ]] || v2_transport_die "SHA-256 invalido"
}

v2_transport_require_source_sha() {
  [[ "$1" =~ ^[a-f0-9]{40}$ ]] || v2_transport_die "Git SHA invalido"
}

v2_transport_require_run_id() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]] || v2_transport_die "run ID invalido"
}

v2_transport_safe_relative() {
  local relative="$1" part
  local -a parts=()
  [[ -n "${relative}" && "${relative}" != /* && "${relative}" != *\\* ]] ||
    v2_transport_die "caminho relativo invalido"
  IFS=/ read -r -a parts <<<"${relative}"
  for part in "${parts[@]}"; do
    [[ -n "${part}" && "${part}" != . && "${part}" != .. ]] ||
      v2_transport_die "path traversal no payload"
  done
}

v2_transport_validate_package_file() {
  local package="$1" expected_sha="$2" expected_bytes="$3" mode actual_sha actual_bytes
  [[ "${package}" == /* ]] || v2_transport_die "pacote deve usar caminho absoluto"
  [[ "$(basename -- "${package}")" == "${V2_PAYLOAD_NAME}" ]] ||
    v2_transport_die "nome de pacote inesperado"
  [[ -f "${package}" && ! -L "${package}" ]] ||
    v2_transport_die "pacote ausente, vazio ou symlink"
  [[ "${expected_bytes}" =~ ^[1-9][0-9]*$ ]] || v2_transport_die "tamanho esperado invalido"
  v2_transport_require_sha "${expected_sha}"
  mode="$(stat -c %a -- "${package}")"
  (( (8#${mode} & 8#022) == 0 )) || v2_transport_die "pacote group/world writable"
  actual_bytes="$(v2_transport_bytes "${package}")"
  actual_sha="$(v2_transport_sha256 "${package}")"
  [[ "${actual_bytes}" == "${expected_bytes}" ]] || v2_transport_die "tamanho do pacote divergente"
  [[ "${actual_sha}" == "${expected_sha}" ]] || v2_transport_die "SHA do pacote divergente"
  printf '%s\n' "${actual_sha}"
}

v2_transport_validate_tar_table() {
  local package="$1" entry normalized type_line entry_count=0 manifest_count=0
  declare -A seen=()
  while IFS= read -r entry; do
    [[ -n "${entry}" ]] || v2_transport_die "entrada TAR vazia"
    [[ "${entry}" =~ ^[A-Za-z0-9._/-]+$ ]] ||
      v2_transport_die "entrada TAR possui caracteres proibidos"
    normalized="${entry%/}"
    v2_transport_safe_relative "${normalized}"
    [[ "${normalized}" == "${V2_PAYLOAD_ROOT}" ||
       "${normalized}" == "${V2_PAYLOAD_ROOT}/"* ]] ||
      v2_transport_die "raiz adicional inesperada no TAR"
    [[ -z "${seen[${normalized}]+x}" ]] || v2_transport_die "entrada TAR duplicada"
    seen["${normalized}"]=1
    ((entry_count += 1))
    [[ "${normalized##*/}" != release-manifest.json ]] ||
      ((manifest_count += 1))
  done < <(tar -tf "${package}")
  (( entry_count > 0 )) || v2_transport_die "TAR vazio"
  (( manifest_count == 1 )) || v2_transport_die "quantidade de manifestos no TAR divergente"

  while IFS= read -r type_line; do
    case "${type_line:0:1}" in
      -|d) ;;
      l|h) v2_transport_die "link proibido no TAR" ;;
      *) v2_transport_die "tipo de entrada TAR proibido" ;;
    esac
  done < <(tar -tvf "${package}")
  V2_TRANSPORT_ENTRY_COUNT="${entry_count}"
  V2_TRANSPORT_MANIFEST_COUNT="${manifest_count}"
  export V2_TRANSPORT_ENTRY_COUNT V2_TRANSPORT_MANIFEST_COUNT
}

v2_transport_verify_file_hashes() {
  local jq_bin="$1" manifest="$2" root="$3" relative expected actual
  while IFS=$'\t' read -r relative expected; do
    [[ -n "${relative}" && -n "${expected}" ]] ||
      v2_transport_die "hash estrutural incompleto"
    v2_transport_safe_relative "${relative}"
    [[ "${expected}" =~ ^[a-f0-9]{64}$ ]] || v2_transport_die "hash estrutural invalido"
    [[ -f "${root}/${relative}" && ! -L "${root}/${relative}" ]] ||
      v2_transport_die "arquivo estrutural ausente"
    actual="$(v2_transport_sha256 "${root}/${relative}")"
    [[ "${actual}" == "${expected}" ]] || v2_transport_die "hash estrutural divergente"
  done < <("${jq_bin}" -r '(.sourceFiles, .migrations) | to_entries[] | [.key, .value] | @tsv' "${manifest}")
}

v2_transport_verify_manifest() {
  local root="$1" expected_source_sha="$2" expected_run_id="$3" expected_jq_sha="$4"
  local jq_bin manifest checksum archive dependencies versions lock expected actual
  local attempt artifact_name expected_name relative index
  local -a expected_files=() actual_files=()
  v2_transport_require_source_sha "${expected_source_sha}"
  v2_transport_require_run_id "${expected_run_id}"
  v2_transport_require_sha "${expected_jq_sha}"
  [[ "$(basename -- "${root}")" == "${V2_PAYLOAD_ROOT}" ]] ||
    v2_transport_die "raiz canonica divergente"
  jq_bin="${root}/tools/jq"
  manifest="${root}/release-manifest.json"
  checksum="${root}/release-manifest.sha256"
  lock="${root}/deploy/v2/images.lock.json"
  [[ -f "${jq_bin}" && ! -L "${jq_bin}" ]] || v2_transport_die "jq hermetico ausente"
  [[ "$(v2_transport_sha256 "${jq_bin}")" == "${expected_jq_sha}" ]] ||
    v2_transport_die "jq hermetico divergente"
  chmod 0500 -- "${jq_bin}"
  [[ "$("${jq_bin}" --version)" == jq-1.7.1 ]] || v2_transport_die "versao jq divergente"
  [[ -f "${manifest}" && ! -L "${manifest}" && -s "${manifest}" ]] ||
    v2_transport_die "manifesto ausente ou inseguro"
  [[ -f "${checksum}" && ! -L "${checksum}" ]] || v2_transport_die "checksum ausente"

  # shellcheck disable=SC2016
  "${jq_bin}" -e --arg sha "${expected_source_sha}" --arg run "${expected_run_id}" '
    .schemaVersion == 2 and
    .artifactLayoutVersion == 2 and
    .gitSha == $sha and
    .certification.runId == $run and
    (.certification.runAttempt | type == "string" and test("^[1-9][0-9]*$")) and
    .artifactLayout.packageRoot == "candidate-root" and
    .artifactLayout.packageName == "candidate-payload.tar" and
    .artifactLayout.manifest == "release-manifest.json" and
    .artifactLayout.checksum == "release-manifest.sha256" and
    .artifactLayout.unexpectedFilesAllowed == false and
    .rebuildAllowedInVerify == false and
    .rebuildAllowedOnCandidate == false and
    (.images | type == "array" and length == 3) and
    (all(.images[]; (.name | type == "string") and (.id | test("^sha256:[a-f0-9]{64}$"))))
  ' "${manifest}" >/dev/null || v2_transport_die "schema do manifesto divergente"

  attempt="$("${jq_bin}" -r '.certification.runAttempt' "${manifest}")"
  artifact_name="$("${jq_bin}" -r '.certification.artifactName' "${manifest}")"
  expected_name="topsdojob-v2-release-${expected_source_sha}-${expected_run_id}-${attempt}"
  [[ "${artifact_name}" == "${expected_name}" ]] || v2_transport_die "identidade do artifact divergente"
  expected="$(v2_transport_sha256 "${manifest}")  release-manifest.json"
  actual="$(tr -d '\r\n' < "${checksum}")"
  [[ "${actual}" == "${expected}" ]] || v2_transport_die "checksum do manifesto divergente"

  mapfile -t expected_files < <(
    "${jq_bin}" -r '.artifactLayout.packageFiles[]' "${manifest}" | sort -u
  )
  while IFS= read -r relative; do
    v2_transport_safe_relative "${relative}"
  done < <(printf '%s\n' "${expected_files[@]}")
  find -P "${root}" -type l -print -quit | grep -q . &&
    v2_transport_die "symlink encontrado na arvore extraida"
  mapfile -t actual_files < <(find -P "${root}" -type f -printf '%P\n' | sort -u)
  (( ${#expected_files[@]} == ${#actual_files[@]} )) ||
    v2_transport_die "arquivos extraidos divergem do manifesto"
  for ((index = 0; index < ${#expected_files[@]}; index += 1)); do
    [[ "${expected_files[index]}" == "${actual_files[index]}" ]] ||
      v2_transport_die "arquivos extraidos divergem do manifesto"
  done

  archive="$("${jq_bin}" -r '.archive.name' "${manifest}")"
  dependencies="$("${jq_bin}" -r '.testDependencies.name' "${manifest}")"
  versions="$("${jq_bin}" -r '.artifactLayout.metadata.toolVersions.name' "${manifest}")"
  for relative in "${archive}" "${dependencies}" "${versions}"; do
    v2_transport_safe_relative "${relative}"
    [[ -f "${root}/${relative}" && ! -L "${root}/${relative}" ]] ||
      v2_transport_die "payload declarado ausente"
  done
  [[ "$(v2_transport_sha256 "${root}/${archive}")" == \
      "$("${jq_bin}" -r '.archive.sha256' "${manifest}")" ]] ||
    v2_transport_die "hash das imagens divergente"
  [[ "$(v2_transport_bytes "${root}/${archive}")" == \
      "$("${jq_bin}" -r '.archive.bytes' "${manifest}")" ]] ||
    v2_transport_die "tamanho das imagens divergente"
  [[ "$(v2_transport_sha256 "${root}/${dependencies}")" == \
      "$("${jq_bin}" -r '.testDependencies.sha256' "${manifest}")" ]] ||
    v2_transport_die "hash das dependencias divergente"
  [[ "$(v2_transport_bytes "${root}/${dependencies}")" == \
      "$("${jq_bin}" -r '.testDependencies.bytes' "${manifest}")" ]] ||
    v2_transport_die "tamanho das dependencias divergente"
  [[ "$(v2_transport_sha256 "${root}/${versions}")" == \
      "$("${jq_bin}" -r '.artifactLayout.metadata.toolVersions.sha256' "${manifest}")" ]] ||
    v2_transport_die "hash das ferramentas divergente"
  [[ "$(v2_transport_bytes "${root}/${versions}")" == \
      "$("${jq_bin}" -r '.artifactLayout.metadata.toolVersions.bytes' "${manifest}")" ]] ||
    v2_transport_die "tamanho das ferramentas divergente"
  [[ "$(v2_transport_sha256 "${lock}")" == \
      "$("${jq_bin}" -r '.lockedImagesSha256' "${manifest}")" ]] ||
    v2_transport_die "lock de imagens divergente"
  [[ "$("${jq_bin}" -S -c '.tools' "${manifest}")" == \
      "$("${jq_bin}" -S -c '.tools' "${lock}")" ]] ||
    v2_transport_die "ferramentas do manifesto divergem do lock"
  v2_transport_verify_file_hashes "${jq_bin}" "${manifest}" "${root}"
  v2_transport_log "MANIFEST_VERIFY=PASS layoutVersion=${V2_PAYLOAD_LAYOUT_VERSION} manifests=1"
}

v2_transport_resolve() {
  local extraction="$1" expected_source_sha="$2" expected_run_id="$3"
  local expected_jq_sha="$4" root_output="$5" manifest root
  local -a manifests=()
  [[ "${extraction}" == /* && -d "${extraction}" && ! -L "${extraction}" ]] ||
    v2_transport_die "diretorio de extracao invalido"
  [[ ! -e "${root_output}" ]] || v2_transport_die "saida de raiz preexistente"
  while IFS= read -r -d '' manifest; do
    manifests+=("${manifest}")
  done < <(find -P "${extraction}" -type f -name release-manifest.json -print0)
  (( ${#manifests[@]} == 1 )) || v2_transport_die "quantidade de manifestos extraidos divergente"
  root="$(cd -- "$(dirname -- "${manifests[0]}")" && pwd -P)"
  [[ "${root}" == "${extraction}/"* ]] || v2_transport_die "artifact root fora da extracao"
  v2_transport_verify_manifest "${root}" "${expected_source_sha}" "${expected_run_id}" \
    "${expected_jq_sha}"
  printf '%s\n' "${root}" > "${root_output}"
  chmod 0600 -- "${root_output}"
  v2_transport_log "ARTIFACT_ROOT_RESOLVED=YES root=${V2_PAYLOAD_ROOT}"
}

v2_transport_extract() {
  local package="$1" expected_sha="$2" expected_bytes="$3" expected_transport_sha="$4"
  local expected_jq_sha="$5" extraction="$6" expected_source_sha="$7"
  local expected_run_id="$8" root_output="$9" actual_sha
  [[ "$#" -eq 9 ]] || v2_transport_die "uso invalido do extrator"
  v2_transport_require_sha "${expected_transport_sha}"
  [[ "$(v2_transport_sha256 "${BASH_SOURCE[0]}")" == "${expected_transport_sha}" ]] ||
    v2_transport_die "bootstrap de transporte divergente"
  actual_sha="$(v2_transport_validate_package_file "${package}" "${expected_sha}" "${expected_bytes}")"
  v2_transport_validate_tar_table "${package}"
  [[ "${extraction}" == /* && ! -e "${extraction}" ]] ||
    v2_transport_die "extracao deve usar diretorio absoluto novo"
  mkdir -p -- "${extraction}"
  tar -xf "${package}" -C "${extraction}" -o --no-same-permissions
  find -P "${extraction}" -type l -print -quit | grep -q . &&
    v2_transport_die "symlink encontrado apos extracao"
  v2_transport_resolve "${extraction}" "${expected_source_sha}" "${expected_run_id}" \
    "${expected_jq_sha}" "${root_output}"
  v2_transport_log "PAYLOAD_VALIDATION=PASS package=${V2_PAYLOAD_NAME} bytes=${expected_bytes} sha=${actual_sha:0:12} entries=${V2_TRANSPORT_ENTRY_COUNT} roots=1 manifests=${V2_TRANSPORT_MANIFEST_COUNT}"
}

v2_transport_handoff() {
  local host="${V2_TRANSPORT_SSH_HOST:?}" port="${V2_TRANSPORT_SSH_PORT:?}"
  local user="${V2_TRANSPORT_SSH_USER:?}" key="${V2_TRANSPORT_SSH_KEY:?}"
  local known_hosts="${V2_TRANSPORT_KNOWN_HOSTS:?}" package="${V2_TRANSPORT_PACKAGE:?}"
  local package_sha="${V2_TRANSPORT_PACKAGE_SHA:?}" package_bytes="${V2_TRANSPORT_PACKAGE_BYTES:?}"
  local transport_sha="${V2_TRANSPORT_BOOTSTRAP_SHA:?}" jq_sha="${V2_TRANSPORT_JQ_SHA:?}"
  local source_sha="${V2_TRANSPORT_SOURCE_SHA:?}" certification_run="${V2_TRANSPORT_CERTIFICATION_RUN_ID:?}"
  local artifact_id="${V2_TRANSPORT_ARTIFACT_ID:?}" artifact_name="${V2_TRANSPORT_ARTIFACT_NAME:?}"
  local execution="${V2_TRANSPORT_EXECUTION:?}"
  local execution_run="${V2_TRANSPORT_EXECUTION_RUN_ID:?}" stage_id="${V2_TRANSPORT_STAGE_ID:?}"
  local stage_namespace="${V2_TRANSPORT_STAGE_NAMESPACE:?}" evidence_tar="${V2_TRANSPORT_EVIDENCE_TAR:?}"
  local diagnostic_log="${V2_TRANSPORT_DIAGNOSTIC_LOG:?}" remote_stage create_stage remote_run cleanup_stage
  local remote_stage_q source_sha_q certification_run_q artifact_id_q execution_run_q
  local package_sha_q package_bytes_q transport_sha_q jq_sha_q execution_q candidate_rc=0 cleanup_rc=0
  local transfer_rc=0 evidence_rc=0 had_errexit=0
  local -a ssh_options
  [[ $- == *e* ]] && had_errexit=1
  [[ "${port}" =~ ^[1-9][0-9]{0,4}$ && "${user}" =~ ^[a-z_][a-z0-9_-]*$ ]] ||
    v2_transport_die "destino SSH invalido"
  [[ "${stage_id}" =~ ^[A-Za-z0-9._-]+$ && "${stage_namespace}" =~ ^[a-z0-9-]+$ ]] ||
    v2_transport_die "staging remoto invalido"
  [[ "${execution}" == candidate || "${execution}" == contract ]] ||
    v2_transport_die "modo remoto invalido"
  v2_transport_require_run_id "${artifact_id}"
  v2_transport_require_run_id "${execution_run}"
  v2_transport_require_source_sha "${source_sha}"
  v2_transport_require_run_id "${certification_run}"
  [[ "${artifact_name}" =~ ^topsdojob-v2-release-${source_sha}-${certification_run}-[1-9][0-9]*$ ]] ||
    v2_transport_die "nome do artifact invalido"
  v2_transport_validate_package_file "${package}" "${package_sha}" "${package_bytes}" >/dev/null
  [[ -f "${key}" && -f "${known_hosts}" ]] || v2_transport_die "material SSH ausente"
  mkdir -p -- "$(dirname -- "${diagnostic_log}")" "$(dirname -- "${evidence_tar}")"
  : > "${diagnostic_log}"
  chmod 0600 -- "${diagnostic_log}"
  v2_transport_log "HANDOFF_START artifactId=${artifact_id} artifactName=${artifact_name} gitSha=${source_sha} layoutVersion=${V2_PAYLOAD_LAYOUT_VERSION} package=${V2_PAYLOAD_NAME} localBytes=${package_bytes} localSha=${package_sha:0:12}" \
    | tee -a "${diagnostic_log}"
  ssh_options=(-4 -p "${port}" -i "${key}" -o BatchMode=yes -o IdentitiesOnly=yes
    -o StrictHostKeyChecking=yes -o UserKnownHostsFile="${known_hosts}" -o ConnectTimeout=10)
  printf -v remote_stage_q '%q' "${stage_id}"
  create_stage="set -eu; umask 077; id=${remote_stage_q}; case \"\${HOME}\" in /*) ;; *) exit 73 ;; esac; test -d \"\${HOME}\"; test ! -L \"\${HOME}\"; test ! -L \"\${HOME}/.cache\"; base=\"\${HOME}/.cache/${stage_namespace}\"; dir=\"\${base}/\${id}\"; test ! -e \"\${dir}\"; test ! -L \"\${base}\"; mkdir -p \"\${base}\"; test \"\$(stat -c %u \"\${base}\")\" = \"\$(id -u)\"; chmod 0700 \"\${base}\"; mkdir -m 0700 \"\${dir}\"; test \"\$(stat -c %u \"\${dir}\")\" = \"\$(id -u)\"; test \"\$(stat -c %a \"\${dir}\")\" = 700; printf '%s' \"\${dir}\""
  # shellcheck disable=SC2029
  remote_stage="$(ssh "${ssh_options[@]}" "${user}@${host}" "${create_stage}" </dev/null)"
  [[ -n "${remote_stage}" ]] || v2_transport_die "staging remoto vazio"
  set +e
  scp -4 -P "${port}" -i "${key}" -o BatchMode=yes -o IdentitiesOnly=yes \
    -o StrictHostKeyChecking=yes -o UserKnownHostsFile="${known_hosts}" \
    "${package}" "${user}@${host}:${remote_stage}/${V2_PAYLOAD_NAME}"
  transfer_rc=$?
  if [[ "${had_errexit}" -eq 1 ]]; then set -e; else set +e; fi
  if [[ "${transfer_rc}" -ne 0 ]]; then
    printf 'PIPELINE_V2_TRANSPORT_ERROR phase=scp code=%s\n' "${transfer_rc}" \
      | tee -a "${diagnostic_log}" >&2
    candidate_rc="${transfer_rc}"
  fi

  if [[ "${candidate_rc}" -eq 0 && "${V2_TRANSPORT_TEST_CORRUPT_AFTER_UPLOAD:-0}" == 1 ]]; then
    [[ "${execution}" == contract ]] || v2_transport_die "corrupcao de teste proibida fora do laboratorio"
    printf -v remote_stage_q '%q' "${remote_stage}"
    set +e
    # shellcheck disable=SC2029
    ssh "${ssh_options[@]}" "${user}@${host}" \
      "printf x >> ${remote_stage_q}/${V2_PAYLOAD_NAME}" </dev/null
    transfer_rc=$?
    if [[ "${had_errexit}" -eq 1 ]]; then set -e; else set +e; fi
    [[ "${transfer_rc}" -eq 0 ]] || candidate_rc="${transfer_rc}"
  fi

  printf -v remote_stage_q '%q' "${remote_stage}"
  printf -v source_sha_q '%q' "${source_sha}"
  printf -v certification_run_q '%q' "${certification_run}"
  printf -v artifact_id_q '%q' "${artifact_id}"
  printf -v execution_run_q '%q' "${execution_run}"
  printf -v package_sha_q '%q' "${package_sha}"
  printf -v package_bytes_q '%q' "${package_bytes}"
  printf -v transport_sha_q '%q' "${transport_sha}"
  printf -v jq_sha_q '%q' "${jq_sha}"
  printf -v execution_q '%q' "${execution}"
  remote_run="set -Eeuo pipefail; umask 077; phase=bootstrap; trap 'rc=\$?; if test \"\${rc}\" -ne 0; then printf \"PIPELINE_V2_TRANSPORT_ERROR phase=%s code=%s\\n\" \"\${phase}\" \"\${rc}\"; fi' EXIT; stage=${remote_stage_q}; package=\"\${stage}/${V2_PAYLOAD_NAME}\"; phase=package-identity; test -f \"\${package}\"; test ! -L \"\${package}\"; test \"\$(stat -c %u \"\${package}\")\" = \"\$(id -u)\"; chmod 0600 \"\${package}\"; remote_bytes=\$(stat -c %s \"\${package}\"); remote_sha=\$(sha256sum \"\${package}\" | awk '{print \$1}'); test \"\${remote_bytes}\" = ${package_bytes_q}; test \"\${remote_sha}\" = ${package_sha_q}; printf 'PIPELINE_V2_TRANSPORT PAYLOAD_BYTE_IDENTICAL=YES localBytes=%s remoteBytes=%s localSha=%s remoteSha=%s\\n' ${package_bytes_q} \"\${remote_bytes}\" \"${package_sha:0:12}\" \"\${remote_sha:0:12}\"; phase=tar-table; tar -tf \"\${package}\" > \"\${stage}/tar-table\"; test -s \"\${stage}/tar-table\"; entry_count=\$(wc -l < \"\${stage}/tar-table\" | tr -d ' '); root_count=\$(awk -F/ 'NF {print \$1}' \"\${stage}/tar-table\" | sort -u | wc -l | tr -d ' '); manifest_count=\$(grep -Ec '(^|/)release-manifest\\.json\$' \"\${stage}/tar-table\" || true); printf 'PIPELINE_V2_TRANSPORT TAR_TABLE entries=%s roots=%s manifests=%s\\n' \"\${entry_count}\" \"\${root_count}\" \"\${manifest_count}\"; phase=tar-manifest; test \"\${manifest_count}\" = 1; test \"\$(grep -Ec '^${V2_PAYLOAD_ROOT}/release-manifest\\.json\$' \"\${stage}/tar-table\")\" = 1; phase=tar-bootstrap; test \"\$(grep -Ec '^${V2_PAYLOAD_ROOT}/scripts/deploy/v2/transport\\.sh\$' \"\${stage}/tar-table\")\" = 1; phase=tar-characters; ! grep -Evq '^[A-Za-z0-9._/-]+/?\$' \"\${stage}/tar-table\"; phase=tar-traversal; ! grep -Eq '(^/|(^|/)\\.\\.?(/|\$))' \"\${stage}/tar-table\"; phase=tar-root; ! grep -Evq '^${V2_PAYLOAD_ROOT}(/|\$)' \"\${stage}/tar-table\"; phase=tar-types; ! tar -tvf \"\${package}\" | cut -c1 | grep -Evq '^[-d]\$'; phase=bootstrap-hash; tar -xOf \"\${package}\" ${V2_PAYLOAD_ROOT}/scripts/deploy/v2/transport.sh > \"\${stage}/transport.sh\"; chmod 0500 \"\${stage}/transport.sh\"; test \"\$(sha256sum \"\${stage}/transport.sh\" | awk '{print \$1}')\" = ${transport_sha_q}; phase=extract-verify; bash \"\${stage}/transport.sh\" extract \"\${package}\" ${package_sha_q} ${package_bytes_q} ${transport_sha_q} ${jq_sha_q} \"\${stage}/extracted\" ${source_sha_q} ${certification_run_q} \"\${stage}/artifact-root.path\" </dev/null; artifact_root=\$(cat \"\${stage}/artifact-root.path\"); phase=controller; if test ${execution_q} = contract; then mkdir -m 0700 \"\${stage}/evidence\"; printf '%s\\n' 'REMOTE_TRANSPORT_CONTRACT=PASS' 'MANIFEST_VERIFY=PASS' > \"\${stage}/evidence/transport-result.txt\"; tar -cf \"\${stage}/candidate-evidence.tar\" -C \"\${stage}/evidence\" .; else bash \"\${artifact_root}/scripts/deploy/v2/controller.sh\" candidate-remote \"\${stage}/extracted\" ${source_sha_q} ${certification_run_q} ${artifact_id_q} ${execution_run_q} ${jq_sha_q} \"\${stage}/evidence\" \"\${stage}/candidate-evidence.tar\" </dev/null; fi; phase=complete"
  if [[ "${candidate_rc}" -eq 0 ]]; then
    set +e
    # shellcheck disable=SC2029
    ssh "${ssh_options[@]}" "${user}@${host}" "${remote_run}" </dev/null 2>&1 |
      tee -a "${diagnostic_log}"
    candidate_rc=${PIPESTATUS[0]}
    if [[ "${had_errexit}" -eq 1 ]]; then set -e; else set +e; fi
  fi
  # shellcheck disable=SC2029
  if ssh "${ssh_options[@]}" "${user}@${host}" \
      "test -s ${remote_stage_q}/candidate-evidence.tar" </dev/null; then
    set +e
    scp -4 -P "${port}" -i "${key}" -o BatchMode=yes -o IdentitiesOnly=yes \
      -o StrictHostKeyChecking=yes -o UserKnownHostsFile="${known_hosts}" \
      "${user}@${host}:${remote_stage}/candidate-evidence.tar" "${evidence_tar}"
    evidence_rc=$?
    if [[ "${had_errexit}" -eq 1 ]]; then set -e; else set +e; fi
    if [[ "${evidence_rc}" -eq 0 ]]; then
      chmod 0600 -- "${evidence_tar}"
    elif [[ "${candidate_rc}" -eq 0 ]]; then
      candidate_rc="${evidence_rc}"
    fi
  fi
  cleanup_stage="set -eu; stage=${remote_stage_q}; case \"\${stage}\" in \"\${HOME}/.cache/${stage_namespace}/\"*) ;; *) exit 70 ;; esac; test ! -L \"\${stage}\"; rm -rf -- \"\${stage}\""
  set +e
  # shellcheck disable=SC2029
  ssh "${ssh_options[@]}" "${user}@${host}" "${cleanup_stage}" </dev/null
  cleanup_rc=$?
  if [[ "${had_errexit}" -eq 1 ]]; then set -e; else set +e; fi
  [[ "${cleanup_rc}" -eq 0 ]] || v2_transport_die "cleanup do staging remoto falhou"
  return "${candidate_rc}"
}

v2_transport_ssh_lab_cleanup() {
  local container="$1"
  if [[ "$(docker inspect --format '{{.State.Running}}' "${container}" 2>/dev/null)" == true ]]; then
    docker stop --time 30 "${container}" >/dev/null || return 1
  fi
  if docker inspect "${container}" >/dev/null 2>&1; then
    docker rm --volumes "${container}" >/dev/null || return 1
  fi
}

v2_transport_ssh_lab() {
  local package="$1" package_sha="$2" package_bytes="$3" transport_sha="$4" jq_sha="$5"
  local source_sha="$6" run_id="$7" artifact_id="$8" iterations="$9" evidence_dir="${10}"
  local image="${V2_TRANSPORT_SSH_IMAGE:?}" test_id container key_dir port attempt=0
  local cleanup_command
  local known_hosts public_key evidence log
  [[ "${iterations}" =~ ^(1|10)$ ]] || v2_transport_die "iteracoes do laboratorio invalidas"
  test_id="${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}-$$"
  container="topsdojob-v2-ssh-${test_id//[^A-Za-z0-9_.-]/-}"
  key_dir="${evidence_dir}/ssh-material"
  mkdir -p -- "${key_dir}" "${evidence_dir}"
  ssh-keygen -q -t ed25519 -N '' -f "${key_dir}/id"
  public_key="$(cat "${key_dir}/id.pub")"
  docker pull --platform linux/amd64 "${image}" >/dev/null
  docker run --detach --name "${container}" --publish 127.0.0.1::2222 \
    --env PUID=1000 --env PGID=1000 --env TZ=Etc/UTC --env USER_NAME=pipeline \
    --env PASSWORD_ACCESS=false --env SUDO_ACCESS=false --env LOG_STDOUT=true \
    --env "PUBLIC_KEY=${public_key}" "${image}" >/dev/null
  printf -v cleanup_command 'v2_transport_ssh_lab_cleanup %q >/dev/null 2>&1 || true' \
    "${container}"
  # The container name is validated/generated here and intentionally expanded now.
  # shellcheck disable=SC2064
  trap "${cleanup_command}" EXIT INT TERM
  port="$(docker port "${container}" 2222/tcp | sed -n 's/^127\.0\.0\.1:\([0-9][0-9]*\)$/\1/p')"
  [[ "${port}" =~ ^[1-9][0-9]*$ ]] || v2_transport_die "porta SSH local ausente"
  known_hosts="${key_dir}/known_hosts"
  for _ in $(seq 1 30); do
    if ssh-keyscan -4 -T 2 -p "${port}" 127.0.0.1 > "${known_hosts}" 2>/dev/null &&
        [[ -s "${known_hosts}" ]]; then
      break
    fi
    sleep 1
  done
  [[ -s "${known_hosts}" ]] || v2_transport_die "sshd local nao iniciou"
  chmod 0600 -- "${key_dir}/id" "${known_hosts}"
  for attempt in $(seq 1 "${iterations}"); do
    evidence="${evidence_dir}/candidate-evidence-${attempt}.tar"
    log="${evidence_dir}/transport-${attempt}.log"
    V2_TRANSPORT_SSH_HOST=127.0.0.1 \
    V2_TRANSPORT_SSH_PORT="${port}" \
    V2_TRANSPORT_SSH_USER=pipeline \
    V2_TRANSPORT_SSH_KEY="${key_dir}/id" \
    V2_TRANSPORT_KNOWN_HOSTS="${known_hosts}" \
    V2_TRANSPORT_PACKAGE="${package}" \
    V2_TRANSPORT_PACKAGE_SHA="${package_sha}" \
    V2_TRANSPORT_PACKAGE_BYTES="${package_bytes}" \
    V2_TRANSPORT_BOOTSTRAP_SHA="${transport_sha}" \
    V2_TRANSPORT_JQ_SHA="${jq_sha}" \
    V2_TRANSPORT_SOURCE_SHA="${source_sha}" \
    V2_TRANSPORT_CERTIFICATION_RUN_ID="${run_id}" \
    V2_TRANSPORT_ARTIFACT_ID="${artifact_id}" \
    V2_TRANSPORT_ARTIFACT_NAME="topsdojob-v2-release-${source_sha}-${run_id}-1" \
    V2_TRANSPORT_EXECUTION=contract \
    V2_TRANSPORT_EXECUTION_RUN_ID="${attempt}" \
    V2_TRANSPORT_STAGE_ID="transport-${test_id}-${attempt}" \
    V2_TRANSPORT_STAGE_NAMESPACE=topsdojob-transport-test \
    V2_TRANSPORT_EVIDENCE_TAR="${evidence}" \
    V2_TRANSPORT_DIAGNOSTIC_LOG="${log}" \
      v2_transport_handoff
    [[ -s "${evidence}" && -s "${log}" ]] || v2_transport_die "evidencia SSH ausente"
  done
  if [[ "${iterations}" -eq 10 ]]; then
    evidence="${evidence_dir}/candidate-evidence-failure.tar"
    log="${evidence_dir}/transport-failure.log"
    set +e
    V2_TRANSPORT_SSH_HOST=127.0.0.1 \
    V2_TRANSPORT_SSH_PORT="${port}" \
    V2_TRANSPORT_SSH_USER=pipeline \
    V2_TRANSPORT_SSH_KEY="${key_dir}/id" \
    V2_TRANSPORT_KNOWN_HOSTS="${known_hosts}" \
    V2_TRANSPORT_PACKAGE="${package}" \
    V2_TRANSPORT_PACKAGE_SHA="${package_sha}" \
    V2_TRANSPORT_PACKAGE_BYTES="${package_bytes}" \
    V2_TRANSPORT_BOOTSTRAP_SHA="${transport_sha}" \
    V2_TRANSPORT_JQ_SHA="${jq_sha}" \
    V2_TRANSPORT_SOURCE_SHA="${source_sha}" \
    V2_TRANSPORT_CERTIFICATION_RUN_ID="${run_id}" \
    V2_TRANSPORT_ARTIFACT_ID="${artifact_id}" \
    V2_TRANSPORT_ARTIFACT_NAME="topsdojob-v2-release-${source_sha}-${run_id}-1" \
    V2_TRANSPORT_EXECUTION=contract \
    V2_TRANSPORT_EXECUTION_RUN_ID=99 \
    V2_TRANSPORT_STAGE_ID="transport-${test_id}-failure" \
    V2_TRANSPORT_STAGE_NAMESPACE=topsdojob-transport-test \
    V2_TRANSPORT_EVIDENCE_TAR="${evidence}" \
    V2_TRANSPORT_DIAGNOSTIC_LOG="${log}" \
    V2_TRANSPORT_TEST_CORRUPT_AFTER_UPLOAD=1 \
      v2_transport_handoff
    failure_rc=$?
    set -e
    [[ "${failure_rc}" -ne 0 && -s "${log}" ]] ||
      v2_transport_die "falha SSH nao preservou diagnostico"
    v2_transport_log "DIAGNOSTIC_BEFORE_CLEANUP=PASS FAILURE_PROPAGATED=PASS"
  fi
  rm -rf -- "${key_dir}"
  v2_transport_ssh_lab_cleanup "${container}"
  trap - EXIT INT TERM
  v2_transport_log "TRANSPORT_SUCCESS=${iterations}/${iterations} LOCAL_REMOTE_SHA_MATCH=${iterations}/${iterations} MANIFEST_VERIFY=${iterations}/${iterations} RESIDUES=0"
}

case "${1:-}" in
  extract)
    shift
    v2_transport_extract "$@"
    ;;
  resolve)
    shift
    [[ "$#" -eq 5 ]] || v2_transport_die "uso invalido do resolver"
    v2_transport_resolve "$@"
    ;;
  handoff)
    [[ "$#" -eq 1 ]] || v2_transport_die "handoff nao aceita argumentos"
    v2_transport_handoff
    ;;
  ssh-lab)
    shift
    [[ "$#" -eq 10 ]] || v2_transport_die "uso invalido do laboratorio SSH"
    v2_transport_ssh_lab "$@"
    ;;
  *)
    v2_transport_die "modo permitido: extract, resolve, handoff ou ssh-lab"
    ;;
esac
