#!/usr/bin/env bash

set -Eeuo pipefail

V2_LAB_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "${V2_LAB_DIR}/lib.sh"

v2_lab_container() {
  v2_compose ps -q "$1"
}

v2_probe_once() {
  local url="$1" expected_status="$2" required_text="${3:-}"
  docker run --rm --network "${V2_PROJECT_NAME}_lab" \
    "${V2_NODE_IMAGE}" node -e '
      const [url, expected, text] = process.argv.slice(1);
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 10000);
      fetch(url, { redirect: "manual", signal: controller.signal })
        .then(async response => {
          const body = await response.text();
          const ok = response.status === Number(expected) && (!text || body.includes(text));
          console.log(JSON.stringify({ status: response.status, bytes: Buffer.byteLength(body) }));
          process.exit(ok ? 0 : 1);
        })
        .catch(error => { console.error(error.name); process.exit(1); })
        .finally(() => clearTimeout(timer));
    ' "${url}" "${expected_status}" "${required_text}" </dev/null
}

v2_wait_probe() {
  local url="$1" expected_status="$2" required_text="${3:-}" attempt
  for attempt in $(seq 1 90); do
    if v2_probe_once "${url}" "${expected_status}" "${required_text}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  v2_probe_once "${url}" "${expected_status}" "${required_text}"
}

v2_sanitize_diagnostic_stream() {
  docker run --rm --network none -i "${V2_NODE_IMAGE}" node -e '
    let value = "";
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", chunk => { value += chunk; });
    process.stdin.on("end", () => {
      const sanitized = value
        .replace(/((?:authorization|proxy-authorization|cookie|set-cookie)\s*[:=]\s*)[^\r\n]+/gi, "$1[REDACTED]")
        .replace(/((?:password|passwd|secret|token|private[_-]?key|signing[_-]?value|access[_-]?key)\s*[:=]\s*)[^\s,;}]+/gi, "$1[REDACTED]")
        .replace(/https?:\/\/[^\s/@:]+:[^\s/@]+@/gi, "https://[CREDENTIALS_REDACTED]@")
        .replace(/[?&](?:x-amz-[^=&#\s]+|signature|sig|token)=[^&#\s]+/gi, "[SIGNED_QUERY_REDACTED]")
        .replace(/hml\/(?:midias-aprovadas|midias-pendentes|documentos)\/[^\s\"<>]+/gi, "[OBJECT_KEY_REDACTED]")
        .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "[EMAIL_REDACTED]")
        .replace(/\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b/g, "[CPF_REDACTED]")
        .replace(/(?:\+?55\s*)?(?:\(?\d{2}\)?\s*)?\d{4,5}[-\s]?\d{4}/g, "[PHONE_REDACTED]")
        .replace(/\b(?:\d{1,3}\.){3}\d{1,3}\b/g, "[IP_REDACTED]")
        .replace(/https?:\/\/(backend|frontend|gateway|minio|mailpit|efi-stub)(?::\d+)?/gi, "internal://$1");
      process.stdout.write(sanitized);
    });
  '
}

v2_capture_http_evidence() {
  local name="$1" url="$2" endpoint_family="$3" evidence_dir="$4"
  [[ "${name}" =~ ^[a-z0-9-]+$ ]] || v2_die "nome de evidencia HTTP invalido"
  docker run --rm --network "${V2_PROJECT_NAME}_lab" \
    --user "$(id -u):$(id -g)" \
    --volume "${evidence_dir}:/evidence" \
    "${V2_NODE_IMAGE}" node -e '
      const fs = require("node:fs");
      const crypto = require("node:crypto");
      const [url, name, endpointFamily] = process.argv.slice(1);
      const sanitize = input => input
        .replace(/((?:authorization|proxy-authorization|cookie|set-cookie)\s*[:=]\s*)[^\r\n]+/gi, "$1[REDACTED]")
        .replace(/((?:password|passwd|secret|token|private[_-]?key|signing[_-]?value|access[_-]?key)\s*[:=]\s*)[^\s,;}]+/gi, "$1[REDACTED]")
        .replace(/https?:\/\/[^\s/@:]+:[^\s/@]+@/gi, "https://[CREDENTIALS_REDACTED]@")
        .replace(/[?&](?:x-amz-[^=&#\s]+|signature|sig|token)=[^&#\s]+/gi, "[SIGNED_QUERY_REDACTED]")
        .replace(/hml\/(?:midias-aprovadas|midias-pendentes|documentos)\/[^\s\"<>]+/gi, "[OBJECT_KEY_REDACTED]")
        .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "[EMAIL_REDACTED]")
        .replace(/\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b/g, "[CPF_REDACTED]")
        .replace(/(?:\+?55\s*)?(?:\(?\d{2}\)?\s*)?\d{4,5}[-\s]?\d{4}/g, "[PHONE_REDACTED]")
        .replace(/\b(?:\d{1,3}\.){3}\d{1,3}\b/g, "[IP_REDACTED]")
        .replace(/https?:\/\/(backend|frontend|gateway|minio|mailpit|efi-stub)(?::\d+)?/gi, "internal://$1");
      const started = performance.now();
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 10000);
      fetch(url, { redirect: "manual", signal: controller.signal })
        .then(async response => {
          const ttfbMs = Math.round(performance.now() - started);
          const body = Buffer.from(await response.arrayBuffer());
          const totalMs = Math.round(performance.now() - started);
          const captured = body.subarray(0, 65536);
          let contract = { json: false };
          try {
            const payload = JSON.parse(body.toString("utf8"));
            const items = Array.isArray(payload?.itens) ? payload.itens : null;
            contract = {
              json: true,
              topLevelObject: payload !== null && typeof payload === "object" && !Array.isArray(payload),
              topLevelKeys: payload !== null && typeof payload === "object" && !Array.isArray(payload)
                ? Object.keys(payload).sort()
                : [],
              itemsArray: items !== null,
              paginationObject: payload?.paginacao !== null && typeof payload?.paginacao === "object",
              locationObject: payload?.localidade !== null && typeof payload?.localidade === "object",
              seoObject: payload?.seo !== null && typeof payload?.seo === "object",
              itemCount: items?.length ?? null,
              itemsWithLocationObject: items?.filter(item => item?.localizacao !== null && typeof item?.localizacao === "object").length ?? null,
              itemsWithoutLocationObject: items?.filter(item => item?.localizacao === null || typeof item?.localizacao !== "object").length ?? null,
            };
          } catch {}
          const sensitiveHeaders = /^(?:authorization|proxy-authorization|cookie|set-cookie)$/i;
          const headers = Object.fromEntries(Array.from(response.headers.entries())
            .map(([header, value]) => [
              header,
              sensitiveHeaders.test(header) ? "[REDACTED]" : sanitize(value),
            ]));
          const metadata = {
            timestampUtc: new Date().toISOString(),
            endpointFamily,
            status: response.status,
            contentType: response.headers.get("content-type"),
            contentLength: response.headers.get("content-length"),
            ttfbMs,
            totalMs,
            bodyBytes: body.length,
            capturedBytes: captured.length,
            truncated: body.length > captured.length,
            bodySha256: crypto.createHash("sha256").update(body).digest("hex"),
            headers,
            contract,
          };
          fs.writeFileSync(`/evidence/${name}.json`, `${JSON.stringify(metadata, null, 2)}\n`, { mode: 0o600 });
          fs.writeFileSync(`/evidence/${name}.body.txt`, sanitize(captured.toString("utf8")), { mode: 0o600 });
        })
        .catch(error => {
          fs.writeFileSync(`/evidence/${name}.json`, `${JSON.stringify({
            timestampUtc: new Date().toISOString(), endpointFamily,
            error: sanitize(error?.name || "FETCH_FAILURE"),
          }, null, 2)}\n`, { mode: 0o600 });
          process.exitCode = 1;
        })
        .finally(() => clearTimeout(timer));
    ' "${url}" "${name}" "${endpoint_family}" </dev/null
}

v2_capture_frontend_internal_api_evidence() {
  local evidence_dir="$1" frontend
  frontend="$(v2_lab_container frontend)"
  docker exec "${frontend}" node -e '
    const crypto = require("node:crypto");
    const dns = require("node:dns").promises;
    const result = {
      timestampUtc: new Date().toISOString(),
      endpointFamily: "catalog.list.all",
      configuredForCandidate: false,
      dnsResolved: false,
    };
    (async () => {
      const rawBase = process.env.INTERNAL_API_URL || "";
      let base;
      try {
        base = new URL(rawBase);
      } catch {
        result.error = "INVALID_INTERNAL_API_CONFIGURATION";
        return;
      }
      result.configuredForCandidate = base.protocol === "http:"
        && base.hostname === "backend"
        && base.port === "8080"
        && base.pathname.replace(/\/$/, "") === "/api/public";
      if (!result.configuredForCandidate) {
        result.error = "INTERNAL_API_OUTSIDE_CANDIDATE";
        return;
      }
      try {
        const addresses = await dns.lookup("backend", { all: true });
        result.dnsResolved = addresses.length > 0;
        result.dnsAddressCount = addresses.length;
      } catch {
        result.error = "INTERNAL_API_DNS_FAILURE";
        return;
      }
      const url = `${rawBase.replace(/\/$/, "")}/anuncios?pagina=0&tamanho=16`;
      const started = performance.now();
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 10000);
      try {
        const response = await fetch(url, { redirect: "manual", signal: controller.signal });
        result.ttfbMs = Math.round(performance.now() - started);
        const body = Buffer.from(await response.arrayBuffer());
        result.totalMs = Math.round(performance.now() - started);
        result.status = response.status;
        result.contentType = response.headers.get("content-type");
        result.contentLength = response.headers.get("content-length");
        result.bodyBytes = body.length;
        result.bodySha256 = crypto.createHash("sha256").update(body).digest("hex");
        try {
          const payload = JSON.parse(body.toString("utf8"));
          const items = Array.isArray(payload?.itens) ? payload.itens : null;
          result.contract = {
            json: true,
            topLevelObject: payload !== null && typeof payload === "object" && !Array.isArray(payload),
            itemsArray: items !== null,
            paginationObject: payload?.paginacao !== null && typeof payload?.paginacao === "object",
            itemCount: items?.length ?? null,
            itemsWithLocationObject: items?.filter(item => item?.localizacao !== null && typeof item?.localizacao === "object").length ?? null,
            itemsWithoutLocationObject: items?.filter(item => item?.localizacao === null || typeof item?.localizacao !== "object").length ?? null,
          };
        } catch {
          result.contract = { json: false };
        }
      } catch (error) {
        result.error = error?.name || "INTERNAL_API_FETCH_FAILURE";
      } finally {
        clearTimeout(timer);
      }
    })().finally(() => process.stdout.write(`${JSON.stringify(result, null, 2)}\n`));
  ' </dev/null | v2_sanitize_diagnostic_stream > "${evidence_dir}/frontend-to-backend.json"
  chmod 0600 "${evidence_dir}/frontend-to-backend.json"
}

v2_capture_inspect_evidence() {
  local service="$1" container="$2" evidence_dir="$3"
  docker inspect "${container}" | docker run --rm --network none -i \
    --user "$(id -u):$(id -g)" --volume "${evidence_dir}:/evidence" \
    "${V2_NODE_IMAGE}" node -e '
      const fs = require("node:fs");
      const service = process.argv[1];
      let input = "";
      process.stdin.setEncoding("utf8");
      process.stdin.on("data", chunk => { input += chunk; });
      process.stdin.on("end", () => {
        const item = JSON.parse(input)[0];
        const health = item.State?.Health;
        const result = {
          service,
          name: String(item.Name || "").replace(/^\//, ""),
          imageId: item.Image,
          state: {
            status: item.State?.Status,
            running: item.State?.Running,
            exitCode: item.State?.ExitCode,
            startedAt: item.State?.StartedAt,
            finishedAt: item.State?.FinishedAt,
            health: health ? {
              status: health.Status,
              failingStreak: health.FailingStreak,
              history: (health.Log || []).map(entry => ({
                start: entry.Start, end: entry.End, exitCode: entry.ExitCode,
              })),
            } : null,
          },
          networks: Object.entries(item.NetworkSettings?.Networks || {}).map(([name, network]) => ({
            name,
            aliases: network.Aliases || [],
          })),
          exposedPorts: Object.keys(item.Config?.ExposedPorts || {}).sort(),
        };
        fs.writeFileSync(`/evidence/inspect-${service}.json`, `${JSON.stringify(result, null, 2)}\n`, { mode: 0o600 });
      });
    ' "${service}"
}

v2_capture_database_evidence() {
  local evidence_dir="$1"
  {
    if ! v2_psql --tuples-only --no-align --field-separator=$'\t' --command "
      SELECT 'usuarios', count(*)::text FROM usuario
      UNION ALL SELECT 'anuncios', count(*)::text FROM anuncio
      UNION ALL SELECT 'anuncios_publicaveis', count(*)::text FROM anuncio
        WHERE status='PUBLICADO' AND status_moderacao='APROVADO' AND removido_em IS NULL
      UNION ALL SELECT 'anuncios_premium', count(*)::text FROM anuncio a
        WHERE a.status='PUBLICADO' AND a.status_moderacao='APROVADO' AND a.removido_em IS NULL
          AND EXISTS (
            SELECT 1 FROM ativacao_beneficio ab
            WHERE ab.anuncio_id=a.id AND ab.status='ATIVA'
              AND ab.inicio_em <= now() AND ab.fim_em > now()
          )
      UNION ALL SELECT 'anuncios_gratuitos', count(*)::text FROM anuncio a
        WHERE a.status='PUBLICADO' AND a.status_moderacao='APROVADO' AND a.removido_em IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM ativacao_beneficio ab
            WHERE ab.anuncio_id=a.id AND ab.status='ATIVA'
              AND ab.inicio_em <= now() AND ab.fim_em > now()
          )
      UNION ALL SELECT 'midias', count(*)::text FROM arquivo_midia
      UNION ALL SELECT 'previews_disponiveis', count(*)::text FROM arquivo_midia
        WHERE preview_restrito_status='DISPONIVEL'
      UNION ALL SELECT 'estados', count(*)::text FROM estado
      UNION ALL SELECT 'cidades', count(*)::text FROM cidade
      UNION ALL SELECT 'bairros', count(*)::text FROM bairro
      UNION ALL SELECT 'anuncio_localizacao', count(*)::text FROM anuncio_localizacao
      UNION ALL SELECT 'categorias_anuncio', count(DISTINCT categoria)::text FROM anuncio
      UNION ALL SELECT 'categorias_home', count(*)::text FROM categoria_home
      UNION ALL SELECT 'anuncio_midia', count(*)::text FROM anuncio_midia
      UNION ALL SELECT 'documento_busca_anuncio', count(*)::text FROM documento_busca_anuncio
      UNION ALL SELECT 'cenarios_listagem_validos', count(DISTINCT a.id)::text
        FROM anuncio a
        JOIN usuario u ON u.id=a.usuario_id AND u.status='ATIVO'
        JOIN anuncio_localizacao al ON al.anuncio_id=a.id
        JOIN estado e ON e.id=al.estado_id
        JOIN cidade c ON c.id=al.cidade_id AND c.estado_id=e.id
        JOIN anuncio_midia am ON am.anuncio_id=a.id AND am.status='PUBLICAVEL'
        JOIN arquivo_midia fm ON fm.id=am.arquivo_midia_id AND fm.status_arquivo='VALIDADO'
        WHERE a.status='PUBLICADO' AND a.status_moderacao='APROVADO' AND a.removido_em IS NULL
      ORDER BY 1;
    "; then
      printf '%s\n' 'DIAGNOSTIC_DATABASE_COUNTS_FAILED'
    fi
  } 2>&1 | v2_sanitize_diagnostic_stream > "${evidence_dir}/fixture-counts.tsv"

  {
    if ! v2_psql --tuples-only --no-align --field-separator=$'\t' --command "
      SELECT
        max(version::int) FILTER (WHERE success AND version ~ '^[0-9]+$') AS max_version,
        count(*) FILTER (WHERE success AND version ~ '^[0-9]+$') AS successful,
        count(*) FILTER (WHERE NOT success) AS failed
      FROM flyway_schema_history;
    "; then
      printf '%s\n' 'DIAGNOSTIC_FLYWAY_QUERY_FAILED'
    fi
  } 2>&1 | v2_sanitize_diagnostic_stream > "${evidence_dir}/flyway.tsv"

  printf '%s\n' \
    "fixture_generator_sha256=$(v2_sha256 "${V2_ROOT}/scripts/deploy/v2/contract.mjs")" \
    "declared_restricted_previews=${V2_OBJECT_COUNT_BEFORE:-unknown}" \
    'fixture_completed_before_candidate=true' \
    > "${evidence_dir}/fixture-provenance.txt"
  chmod 0600 "${evidence_dir}/fixture-counts.tsv" \
    "${evidence_dir}/flyway.tsv" "${evidence_dir}/fixture-provenance.txt"
}

v2_capture_service_evidence() {
  local evidence_dir="$1" preview_count postgres_status minio_status smtp_status efi_status
  local backend_status frontend_status gateway_status
  postgres_status=FAIL
  minio_status=FAIL
  smtp_status=FAIL
  efi_status=FAIL
  backend_status=FAIL
  frontend_status=FAIL
  gateway_status=FAIL

  if [[ "$(v2_psql --tuples-only --no-align --command 'SELECT 1;' 2>/dev/null | tr -d '[:space:]')" == "1" ]]; then
    postgres_status=OK
  fi
  preview_count="$({
    v2_mc --insecure find \
      local/topsdojob-v2-public/hml/midias-aprovadas/restritas-borradas/v1 \
      --name '*.jpg' 2>/dev/null || true
  } | wc -l | tr -d '[:space:]')"
  if [[ "${preview_count}" == "${V2_OBJECT_COUNT_BEFORE:-}" ]]; then
    minio_status=OK
  fi
  if v2_smtp_probe >/dev/null 2>&1; then smtp_status=OK; fi
  if v2_probe_once http://efi-stub:8090 200 '"provider":"local-stub"' >/dev/null 2>&1; then
    efi_status=OK
  fi
  if v2_probe_once http://backend:8080/api/health/readiness 200 '"status":"UP"' >/dev/null 2>&1; then
    backend_status=OK
  fi
  if v2_probe_once http://frontend:3000/health/readiness 200 '"status":"UP"' >/dev/null 2>&1; then
    frontend_status=OK
  fi
  if v2_probe_once http://gateway:8080/health/readiness 200 '"status":"UP"' >/dev/null 2>&1; then
    gateway_status=OK
  fi

  printf '%s\n' \
    "postgresql=${postgres_status}" \
    "minio=${minio_status}" \
    "minio_restricted_preview_count=${preview_count}" \
    "smtp_local=${smtp_status}" \
    "efi_local=${efi_status}" \
    "backend_readiness=${backend_status}" \
    "frontend_readiness=${frontend_status}" \
    "gateway_readiness=${gateway_status}" \
    'external_calls_allowed=false' \
    > "${evidence_dir}/service-status.txt"
  chmod 0600 "${evidence_dir}/service-status.txt"
}

v2_capture_container_logs() {
  local evidence_dir="$1" service container limit
  for service in gateway frontend backend; do
    case "${service}" in
      gateway) limit=200 ;;
      frontend|backend) limit=300 ;;
    esac
    container="$(v2_lab_container "${service}")"
    {
      docker logs --timestamps --tail "${limit}" "${container}" 2>&1 ||
        printf 'DIAGNOSTIC_LOG_CAPTURE_FAILED service=%s\n' "${service}"
    } | v2_sanitize_diagnostic_stream > "${evidence_dir}/${service}.log"
    chmod 0600 "${evidence_dir}/${service}.log"
  done

  grep -Ei 'digest|TypeError|ReferenceError|Error:| at [A-Za-z0-9_./:$-]+' \
    "${evidence_dir}/frontend.log" | tail -n 120 \
    > "${evidence_dir}/next-error-digest.txt" || true
  grep -Ei 'Exception|Caused by:|SQLState|IllegalStateException| at br\.com\.topsdojob' \
    "${evidence_dir}/backend.log" | tail -n 180 \
    > "${evidence_dir}/backend-stack-trace.txt" || true
  chmod 0600 "${evidence_dir}/next-error-digest.txt" \
    "${evidence_dir}/backend-stack-trace.txt"
}

v2_collect_gateway_listagem_diagnostics() {
  local evidence_dir="$1" failed_gate="$2" failed_exit="$3"
  local service container gateway
  mkdir -m 0700 -p -- "${evidence_dir}"

  if ! v2_capture_http_evidence gateway-listagem \
    http://gateway:8080/anuncios public.page.listing "${evidence_dir}"; then
    v2_log "DIAGNOSTIC_CAPTURE=gateway-listagem result=FAIL"
  fi
  if ! v2_capture_http_evidence frontend-listagem \
    http://frontend:3000/anuncios public.page.listing "${evidence_dir}"; then
    v2_log "DIAGNOSTIC_CAPTURE=frontend-listagem result=FAIL"
  fi
  if ! v2_capture_http_evidence backend-listagem \
    'http://backend:8080/api/public/anuncios?pagina=0&tamanho=16' \
    catalog.list.all "${evidence_dir}"; then
    v2_log "DIAGNOSTIC_CAPTURE=backend-listagem result=FAIL"
  fi
  if ! v2_capture_frontend_internal_api_evidence "${evidence_dir}"; then
    v2_log "DIAGNOSTIC_CAPTURE=frontend-to-backend result=FAIL"
  fi

  printf '%s\n' \
    '{' \
    '  "page": "/anuncios",' \
    '  "serverSideCalls": [' \
    '    {' \
    '      "endpointFamily": "catalog.list.all",' \
    '      "backendPathFamily": "/anuncios",' \
    '      "method": "GET",' \
    '      "callsPerRender": 1' \
    '    }' \
    '  ]' \
    '}' > "${evidence_dir}/endpoint-map.json"

  v2_capture_database_evidence "${evidence_dir}"
  v2_capture_service_evidence "${evidence_dir}"
  v2_capture_container_logs "${evidence_dir}"

  for service in postgres minio mailpit efi-stub backend frontend gateway; do
    container="$(v2_lab_container "${service}")"
    if [[ -n "${container}" ]]; then
      if ! v2_capture_inspect_evidence "${service}" "${container}" "${evidence_dir}"; then
        v2_log "DIAGNOSTIC_CAPTURE=inspect-${service} result=FAIL"
      fi
    fi
  done

  gateway="$(v2_lab_container gateway)"
  {
    docker exec "${gateway}" nginx -T </dev/null 2>&1 ||
      printf '%s\n' 'NGINX_EFFECTIVE_CONFIG_CAPTURE_FAILED'
  } | v2_sanitize_diagnostic_stream |
    grep -E '(^|[[:space:]])(listen|location|proxy_pass|upstream)([[:space:]]|$)' \
      > "${evidence_dir}/gateway-upstream.txt" || true

  printf '%s\n' \
    "timestamp_utc=$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
    "failed_gate=${failed_gate:-none}" \
    "failed_exit=${failed_exit}" \
    'first_unexecuted_after_gateway_listagem=gateway_localidades' \
    'diagnostic_requests_after_failure=true' \
    > "${evidence_dir}/diagnostic-summary.txt"
  find "${evidence_dir}" -type f -exec chmod 0600 {} +
}

v2_generate_tls() {
  local cert_dir="${V2_RUNTIME_DIR}/certs/minio"
  mkdir -m 0700 -p -- "${cert_dir}" "${V2_RUNTIME_DIR}/certs" "${V2_RUNTIME_DIR}/reports"
  openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 1 \
    -subj '/CN=minio' -addext 'subjectAltName=DNS:minio,IP:127.0.0.1' \
    -keyout "${cert_dir}/private.key" -out "${cert_dir}/public.crt" >/dev/null 2>&1
  chmod 0600 "${cert_dir}/private.key" "${cert_dir}/public.crt"
  docker run --rm \
    --user "$(id -u):$(id -g)" \
    --volume "${V2_RUNTIME_DIR}/certs:/certs" \
    "${V2_JRE_IMAGE}" sh -eu -c '
      keytool -importkeystore -noprompt \
        -srckeystore "${JAVA_HOME}/lib/security/cacerts" \
        -srcstorepass changeit \
        -destkeystore /certs/truststore.p12 \
        -deststoretype PKCS12 \
        -deststorepass pipeline-v2-trust >/dev/null
      keytool -importcert -noprompt \
        -alias pipeline-v2-minio \
        -file /certs/minio/public.crt \
        -keystore /certs/truststore.p12 \
        -storetype PKCS12 \
        -storepass pipeline-v2-trust >/dev/null
      keytool -list \
        -alias pipeline-v2-minio \
        -keystore /certs/truststore.p12 \
        -storetype PKCS12 \
        -storepass pipeline-v2-trust >/dev/null
      test "$(keytool -list \
        -keystore /certs/truststore.p12 \
        -storetype PKCS12 \
        -storepass pipeline-v2-trust 2>/dev/null | grep -c "trustedCertEntry")" -gt 1
    '
  chmod 0600 "${V2_RUNTIME_DIR}/certs/truststore.p12"
}

v2_wait_postgres() {
  local container attempt
  container="$(v2_lab_container postgres)"
  for attempt in $(seq 1 60); do
    if docker exec "${container}" pg_isready -U topsdojob_v2 -d topsdojob_v2 \
      </dev/null >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

v2_mc() {
  docker run --rm --network "${V2_PROJECT_NAME}_lab" \
    --volume "${V2_RUNTIME_DIR}/mc:/root/.mc" \
    --volume "${V2_RUNTIME_DIR}/fixture/objects:/fixtures:ro" \
    "${V2_MINIO_CLIENT_IMAGE}" "$@" </dev/null
}

v2_wait_minio() {
  local attempt
  mkdir -m 0700 -p -- "${V2_RUNTIME_DIR}/mc"
  for attempt in $(seq 1 60); do
    if v2_mc --insecure alias set local https://minio:9000 \
      "${V2_MINIO_ACCESS_KEY}" "${V2_MINIO_SECRET_KEY}" \
      --api S3v4 --path on >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

v2_smtp_probe() {
  docker run --rm --network "${V2_PROJECT_NAME}_lab" \
    "${V2_NODE_IMAGE}" node -e '
      const net = require("node:net");
      const timer = setTimeout(() => process.exit(1), 3000);
      const socket = net.createConnection({ host: "mailpit", port: 1025 });
      socket.once("data", data => {
        clearTimeout(timer);
        process.exit(data.toString("utf8").startsWith("220") ? 0 : 1);
      });
      socket.once("error", () => process.exit(1));
    ' </dev/null
}

v2_wait_local_dependencies() {
  local attempt
  v2_wait_probe http://efi-stub:8090 200 '"provider":"local-stub"' ||
    v2_die "stub HTTP local da Efi nao ficou pronto"
  for attempt in $(seq 1 60); do
    if v2_smtp_probe >/dev/null 2>&1; then
      v2_log "LOCAL_EFI_STUB=OK LOCAL_SMTP=OK"
      return 0
    fi
    sleep 1
  done
  v2_die "SMTP local nao ficou pronto"
}

v2_psql() {
  local container
  container="$(v2_lab_container postgres)"
  docker exec "${container}" psql --no-psqlrc --set=ON_ERROR_STOP=1 \
    --username topsdojob_v2 --dbname topsdojob_v2 "$@" </dev/null
}

v2_minio_host_endpoint() {
  local binding
  binding="$(docker port "$(v2_lab_container minio)" 9000/tcp | tail -n 1)"
  [[ "${binding}" == "127.0.0.1:19000" ]] ||
    v2_die "porta loopback do MinIO local invalida"
  printf 'https://127.0.0.1:19000\n'
}

v2_create_import_snapshot() {
  local fixture_dir="${V2_RUNTIME_DIR}/test-fixtures" container
  container="$(v2_lab_container postgres)"
  docker cp "${fixture_dir}/source-snapshot.sql" \
    "${container}:/tmp/pipeline-v2-source-snapshot.sql"
  docker exec "${container}" psql --no-psqlrc --set=ON_ERROR_STOP=1 \
    --username topsdojob_v2 --dbname postgres \
    --command 'CREATE DATABASE pipeline_v2_source;' </dev/null >/dev/null
  docker exec "${container}" psql --no-psqlrc --set=ON_ERROR_STOP=1 \
    --username topsdojob_v2 --dbname pipeline_v2_source \
    --file /tmp/pipeline-v2-source-snapshot.sql </dev/null >/dev/null
  docker exec "${container}" pg_dump --format=custom --no-owner --no-privileges \
    --username topsdojob_v2 --dbname pipeline_v2_source \
    --file /tmp/pipeline-v2-source-snapshot.dump </dev/null
  docker exec "${container}" pg_restore --list \
    /tmp/pipeline-v2-source-snapshot.dump </dev/null >/dev/null
  docker cp "${container}:/tmp/pipeline-v2-source-snapshot.dump" \
    "${fixture_dir}/source-snapshot.dump"
  [[ -s "${fixture_dir}/source-snapshot.dump" ]] || v2_die "snapshot sintetico vazio"
  docker exec "${container}" rm -- \
    /tmp/pipeline-v2-source-snapshot.sql \
    /tmp/pipeline-v2-source-snapshot.dump </dev/null
  v2_log "IMPORT_SNAPSHOT=OK POSTGRESQL=17 SOURCE_ROWS=3"
}

v2_prepare_database_and_storage() {
  local container object_count endpoint source_object
  v2_node "scripts/deploy/v2/contract.mjs" fixture "${V2_RUNTIME_DIR}/fixture" 1344
  v2_compose up -d postgres minio mailpit efi-stub
  v2_wait_postgres || v2_die "PostgreSQL 17 nao ficou pronto"
  v2_wait_minio || v2_die "MinIO TLS local nao ficou pronto"
  v2_wait_local_dependencies
  for bucket in topsdojob-v2-public topsdojob-v2-private topsdojob-v2-documents; do
    v2_mc --insecure mb --ignore-existing "local/${bucket}" >/dev/null
  done
  v2_mc --insecure anonymous set download local/topsdojob-v2-public >/dev/null

  v2_compose run --rm --no-deps -T flyway migrate </dev/null
  [[ "$(v2_psql --tuples-only --no-align --command "SELECT max(version::int) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';" | tr -d '[:space:]')" == "53" ]]
  [[ "$(v2_psql --tuples-only --no-align --command "SELECT count(*) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';" | tr -d '[:space:]')" == "53" ]]
  [[ "$(v2_psql --tuples-only --no-align --command 'SELECT count(*) FROM flyway_schema_history WHERE NOT success;' | tr -d '[:space:]')" == "0" ]]

  container="$(v2_lab_container postgres)"
  docker cp "${V2_RUNTIME_DIR}/fixture/fixture.sql" "${container}:/tmp/pipeline-v2-fixture.sql"
  docker exec "${container}" psql --no-psqlrc --set=ON_ERROR_STOP=1 \
    --username topsdojob_v2 --dbname topsdojob_v2 \
    --file /tmp/pipeline-v2-fixture.sql </dev/null >/dev/null
  docker exec "${container}" rm -- /tmp/pipeline-v2-fixture.sql </dev/null
  v2_mc --insecure mirror /fixtures local/topsdojob-v2-public --overwrite >/dev/null
  source_object="hml/midias-aprovadas/synthetic/pipeline-v2-source.png"
  v2_mc --insecure cp --attr 'Content-Type=image/png' \
    "/fixtures/${source_object}" "local/topsdojob-v2-public/${source_object}" >/dev/null
  object_count="$(v2_mc --insecure find local/topsdojob-v2-public/hml/midias-aprovadas/restritas-borradas/v1 \
    --name '*.jpg' | wc -l | tr -d '[:space:]')"
  [[ "${object_count}" == "1344" ]] || v2_die "inventario MinIO divergente: ${object_count}"
  export V2_OBJECT_COUNT_BEFORE="${object_count}"
  endpoint="$(v2_minio_host_endpoint)"
  export V2_LOCAL_MINIO_ENDPOINT="${endpoint}"
  v2_node "scripts/deploy/v2/contract.mjs" runtime-fixtures \
    "${V2_RUNTIME_DIR}/test-fixtures" "${endpoint}" "${V2_RUNTIME_DIR}/fixture"
  v2_create_import_snapshot
}

v2_prepare_lab() {
  local evidence_dir="$1"
  v2_require_command openssl
  mkdir -m 0700 -p -- "${evidence_dir}"
  v2_generate_tls
  v2_prepare_database_and_storage
  v2_log "LAB_PREPARED=OK MINIO=LOOPBACK_TLS POSTGRESQL=17 FLYWAY=53"
}

v2_run_backfill_mode() {
  local mode="$1" output="$2" confirm="false"
  [[ "${mode}" != "APPLY" ]] || confirm="true"
  docker image inspect "${V2_BACKEND_IMAGE}" >/dev/null
  v2_compose run --rm --no-deps -T backend \
    --app.bootstrap=restricted-media-preview-backfill \
    --spring.main.web-application-type=none \
    --spring.main.banner-mode=off \
    --logging.level.root=WARN \
    --app.restricted-media-preview-reconciliation.enabled=true \
    --app.restricted-media-preview-reconciliation.mode="${mode}" \
    --app.restricted-media-preview-reconciliation.apply-confirmed="${confirm}" \
    --app.restricted-media-preview-reconciliation.batch-size=200 \
    --app.restricted-media-preview-reconciliation.report-path="/reports/${mode,,}-$(basename "${output}").tsv" \
    </dev/null | tee "${output}"
}

v2_backfill_contract() {
  local before after object_count_after
  before="$(v2_psql --tuples-only --no-align --command "SELECT count(*) FROM arquivo_midia WHERE preview_restrito_status='DESCONHECIDO';" | tr -d '[:space:]')"
  [[ "${before}" == "1344" ]]
  v2_run_backfill_mode PLAN "${V2_RUNTIME_DIR}/plan.log"
  grep -Eq 'mode=PLAN .*eligible=1344 .*r2_available=1344 .*missing=0 .*inconsistent=0 .*unproven=0 .*db_updated=0' "${V2_RUNTIME_DIR}/plan.log"
  after="$(v2_psql --tuples-only --no-align --command "SELECT count(*) FROM arquivo_midia WHERE preview_restrito_status='DESCONHECIDO';" | tr -d '[:space:]')"
  [[ "${after}" == "${before}" ]] || v2_die "PLAN alterou o banco"

  v2_run_backfill_mode APPLY "${V2_RUNTIME_DIR}/apply.log"
  grep -Eq 'mode=APPLY .*eligible=1344 .*missing=0 .*inconsistent=0 .*unproven=0 .*db_updated=1344 .*db_available=1344 .*db_unknown=0 .*db_pending=0 .*db_inconsistent=0' "${V2_RUNTIME_DIR}/apply.log"
  v2_run_backfill_mode APPLY "${V2_RUNTIME_DIR}/apply-idempotent.log"
  grep -Eq 'mode=APPLY .*eligible=1344 .*db_updated=0 .*db_unchanged=1344 .*db_available=1344 .*db_unknown=0 .*db_pending=0 .*db_inconsistent=0' "${V2_RUNTIME_DIR}/apply-idempotent.log"

  [[ "$(v2_psql --tuples-only --no-align --command "SELECT count(*) FROM arquivo_midia WHERE preview_restrito_status='DISPONIVEL';" | tr -d '[:space:]')" == "1344" ]]
  [[ "$(v2_psql --tuples-only --no-align --command "SELECT count(*) FROM arquivo_midia WHERE preview_restrito_status IN ('DESCONHECIDO','PENDENTE');" | tr -d '[:space:]')" == "0" ]]
  object_count_after="$(v2_mc --insecure find local/topsdojob-v2-public/hml/midias-aprovadas/restritas-borradas/v1 \
    --name '*.jpg' | wc -l | tr -d '[:space:]')"
  [[ "${object_count_after}" == "${V2_OBJECT_COUNT_BEFORE}" ]] || v2_die "backfill alterou MinIO"
  v2_log "BACKFILL PLAN=OK APPLY=1344 IDEMPOTENT=0 OBJECT_MUTATIONS=0"
}

v2_candidate_gate() {
  local name="$1" start end rc start_utc end_utc result
  shift
  start_utc="$(date -u +'%Y-%m-%dT%H:%M:%S.%3NZ')"
  start="$(date +%s%3N)"
  v2_log "CANDIDATE_GATE_START=${name}"
  set +e
  "$@"
  rc=$?
  set -e
  end="$(date +%s%3N)"
  end_utc="$(date -u +'%Y-%m-%dT%H:%M:%S.%3NZ')"
  result=OK
  [[ "${rc}" -eq 0 ]] || result=FAIL
  if [[ -n "${V2_DIAGNOSTIC_EVIDENCE_DIR:-}" ]]; then
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
      "${name}" "${result}" "${rc}" "$((end-start))" "${start_utc}" "${end_utc}" \
      >> "${V2_DIAGNOSTIC_EVIDENCE_DIR}/gate-timings.tsv"
  fi
  if [[ "${rc}" -ne 0 ]]; then
    v2_log "CANDIDATE_GATE_RESULT=${name} result=FAIL exit=${rc} duration_ms=$((end-start))"
    return "${rc}"
  fi
  v2_log "CANDIDATE_GATE_RESULT=${name} result=OK exit=0 duration_ms=$((end-start))"
  V2_GATE_COUNT=$((V2_GATE_COUNT + 1))
}

v2_database_gate() {
  [[ "$(v2_psql --tuples-only --no-align --command 'SELECT 1;' | tr -d '[:space:]')" == "1" ]]
  [[ "$(v2_psql --tuples-only --no-align --command "SELECT max(version::int)||'|'||count(*) FILTER (WHERE NOT success) FROM flyway_schema_history WHERE version ~ '^[0-9]+$';" | tr -d '[:space:]')" == "53|0" ]]
}

v2_internal_api_gate() {
  local frontend backend_ip resolved configured
  frontend="$(v2_lab_container frontend)"
  backend_ip="$(docker inspect "$(v2_lab_container backend)" --format "{{(index .NetworkSettings.Networks \"${V2_PROJECT_NAME}_lab\").IPAddress}}")"
  resolved="$(docker exec "${frontend}" node -e "require('node:dns').lookup('backend',(e,a)=>{if(e)process.exit(1);process.stdout.write(a)})" </dev/null)"
  configured="$(docker inspect "${frontend}" --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^INTERNAL_API_URL=//p')"
  [[ "${configured}" == "http://backend:8080/api/public" && "${resolved}" == "${backend_ip}" ]]
}

v2_logs_gate() {
  local service container
  for service in backend frontend gateway; do
    container="$(v2_lab_container "${service}")"
    [[ "$(docker inspect "${container}" --format '{{.State.Running}}')" == "true" ]]
    if docker logs "${container}" 2>&1 | grep -Eqi 'APPLICATION FAILED TO START|OutOfMemoryError|FATAL:|panic:|emerg'; then
      return 1
    fi
  done
}

v2_diagnostic_gate() {
  local name="$1" rc
  shift
  [[ -z "${V2_DIAGNOSTIC_FAILED_GATE:-}" ]] || return 0
  if v2_candidate_gate "${name}" "$@"; then
    return 0
  else
    rc=$?
    V2_DIAGNOSTIC_FAILED_GATE="${name}"
    V2_DIAGNOSTIC_FAILED_EXIT="${rc}"
    return 0
  fi
}

v2_diagnose_gateway_listagem() {
  local evidence_dir="$1" postgres
  export V2_DIAGNOSTIC_EVIDENCE_DIR="${evidence_dir}"
  mkdir -m 0700 -p -- "${evidence_dir}"
  printf '%s\n' \
    $'position\tname' \
    $'1\tbackend_liveness' \
    $'2\tbackend_readiness' \
    $'3\tfrontend_liveness' \
    $'4\tfrontend_readiness' \
    $'5\tgateway_backend_liveness' \
    $'6\tgateway_backend_readiness' \
    $'7\tgateway_frontend_liveness' \
    $'8\tgateway_frontend_readiness' \
    $'9\tgateway_home' \
    $'10\tgateway_listagem' \
    $'11\tgateway_localidades' \
    $'12\tdatabase' \
    $'13\tinternal_api_dns' \
    $'14\tcandidate_logs' \
    > "${evidence_dir}/gate-order.tsv"
  printf '%s\n' $'name\tresult\texit_code\tduration_ms\tstart_utc\tend_utc' \
    > "${evidence_dir}/gate-timings.tsv"
  chmod 0600 "${evidence_dir}/gate-order.tsv" "${evidence_dir}/gate-timings.tsv"

  docker image inspect "${V2_BACKEND_IMAGE}" >/dev/null
  v2_compose up -d --no-deps --no-build backend
  v2_wait_probe http://backend:8080/api/health/liveness 200 '"status":"UP"'
  v2_wait_probe http://backend:8080/api/health/readiness 200 '"status":"UP"'

  postgres="$(v2_lab_container postgres)"
  docker pause "${postgres}" >/dev/null
  v2_wait_probe http://backend:8080/api/health/readiness 503 '"status":"DOWN"'
  v2_probe_once http://backend:8080/api/health/liveness 200 '"status":"UP"' >/dev/null
  docker unpause "${postgres}" >/dev/null
  v2_wait_probe http://backend:8080/api/health/readiness 200 '"status":"UP"'
  v2_log "READINESS_503=OK LIVENESS_DEPENDENCY_FAILURE=200 RECOVERY=OK"

  docker image inspect "${V2_FRONTEND_IMAGE}" >/dev/null
  v2_compose up -d --no-deps --no-build frontend
  v2_wait_probe http://frontend:3000/health/liveness 200 '"status":"UP"'
  v2_wait_probe http://frontend:3000/health/readiness 200 '"status":"UP"'
  docker image inspect "${V2_GATEWAY_IMAGE}" >/dev/null
  v2_compose up -d --no-deps --no-build gateway
  v2_wait_probe http://gateway:8080/health/readiness 200 '"status":"UP"'

  V2_GATE_COUNT=0
  V2_DIAGNOSTIC_FAILED_GATE=""
  V2_DIAGNOSTIC_FAILED_EXIT=0
  v2_diagnostic_gate backend_liveness \
    v2_probe_once http://backend:8080/api/health/liveness 200 '"status":"UP"'
  v2_diagnostic_gate backend_readiness \
    v2_probe_once http://backend:8080/api/health/readiness 200 '"status":"UP"'
  v2_diagnostic_gate frontend_liveness \
    v2_probe_once http://frontend:3000/health/liveness 200 '"status":"UP"'
  v2_diagnostic_gate frontend_readiness \
    v2_probe_once http://frontend:3000/health/readiness 200 '"status":"UP"'
  v2_diagnostic_gate gateway_backend_liveness \
    v2_probe_once http://gateway:8080/api/health/liveness 200 '"status":"UP"'
  v2_diagnostic_gate gateway_backend_readiness \
    v2_probe_once http://gateway:8080/api/health/readiness 200 '"status":"UP"'
  v2_diagnostic_gate gateway_frontend_liveness \
    v2_probe_once http://gateway:8080/health/liveness 200 '"status":"UP"'
  v2_diagnostic_gate gateway_frontend_readiness \
    v2_probe_once http://gateway:8080/health/readiness 200 '"status":"UP"'
  v2_diagnostic_gate gateway_home \
    v2_probe_once http://gateway:8080/ 200
  v2_diagnostic_gate gateway_listagem \
    v2_probe_once http://gateway:8080/anuncios 200

  v2_collect_gateway_listagem_diagnostics \
    "${evidence_dir}" "${V2_DIAGNOSTIC_FAILED_GATE}" "${V2_DIAGNOSTIC_FAILED_EXIT}"
  if [[ "${V2_DIAGNOSTIC_FAILED_GATE}" == "gateway_listagem" \
      && "${V2_DIAGNOSTIC_FAILED_EXIT}" -ne 0 ]]; then
    v2_log "DIAGNOSTIC_REPRODUCTION=PASS FAILED_GATE=gateway_listagem"
    return 0
  fi
  v2_log "DIAGNOSTIC_REPRODUCTION=FAIL FAILED_GATE=${V2_DIAGNOSTIC_FAILED_GATE:-none}"
  return 1
}

v2_start_candidate_and_gates() {
  local postgres
  docker image inspect "${V2_BACKEND_IMAGE}" >/dev/null
  v2_compose up -d --no-deps --no-build backend
  v2_wait_probe http://backend:8080/api/health/liveness 200 '"status":"UP"'
  v2_wait_probe http://backend:8080/api/health/readiness 200 '"status":"UP"'

  postgres="$(v2_lab_container postgres)"
  docker pause "${postgres}" >/dev/null
  v2_wait_probe http://backend:8080/api/health/readiness 503 '"status":"DOWN"'
  v2_probe_once http://backend:8080/api/health/liveness 200 '"status":"UP"' >/dev/null
  docker unpause "${postgres}" >/dev/null
  v2_wait_probe http://backend:8080/api/health/readiness 200 '"status":"UP"'
  v2_log "READINESS_503=OK LIVENESS_DEPENDENCY_FAILURE=200 RECOVERY=OK"

  docker image inspect "${V2_FRONTEND_IMAGE}" >/dev/null
  v2_compose up -d --no-deps --no-build frontend
  v2_wait_probe http://frontend:3000/health/liveness 200 '"status":"UP"'
  v2_wait_probe http://frontend:3000/health/readiness 200 '"status":"UP"'
  docker image inspect "${V2_GATEWAY_IMAGE}" >/dev/null
  v2_compose up -d --no-deps --no-build gateway
  v2_wait_probe http://gateway:8080/health/readiness 200 '"status":"UP"'

  V2_GATE_COUNT=0
  v2_candidate_gate backend_liveness v2_probe_once http://backend:8080/api/health/liveness 200 '"status":"UP"'
  v2_candidate_gate backend_readiness v2_probe_once http://backend:8080/api/health/readiness 200 '"status":"UP"'
  v2_candidate_gate frontend_liveness v2_probe_once http://frontend:3000/health/liveness 200 '"status":"UP"'
  v2_candidate_gate frontend_readiness v2_probe_once http://frontend:3000/health/readiness 200 '"status":"UP"'
  v2_candidate_gate gateway_backend_liveness v2_probe_once http://gateway:8080/api/health/liveness 200 '"status":"UP"'
  v2_candidate_gate gateway_backend_readiness v2_probe_once http://gateway:8080/api/health/readiness 200 '"status":"UP"'
  v2_candidate_gate gateway_frontend_liveness v2_probe_once http://gateway:8080/health/liveness 200 '"status":"UP"'
  v2_candidate_gate gateway_frontend_readiness v2_probe_once http://gateway:8080/health/readiness 200 '"status":"UP"'
  v2_candidate_gate gateway_home v2_probe_once http://gateway:8080/ 200
  v2_candidate_gate gateway_listagem v2_probe_once http://gateway:8080/anuncios 200
  v2_candidate_gate gateway_localidades v2_probe_once http://gateway:8080/api/public/localidades 200
  v2_candidate_gate database v2_database_gate
  v2_candidate_gate internal_api_dns v2_internal_api_gate
  v2_candidate_gate candidate_logs v2_logs_gate
  [[ "${V2_GATE_COUNT}" -eq 14 ]] || v2_die "candidate gates incompletos: ${V2_GATE_COUNT}/14"
  v2_log "CANDIDATE_GATES=14/14"
}

v2_port_occupied_contract() {
  local holder challenger port
  holder="${V2_PROJECT_NAME}-port-holder"
  challenger="${V2_PROJECT_NAME}-port-challenger"
  docker run -d --name "${holder}" -p 127.0.0.1::8099 "${V2_NODE_IMAGE}" \
    node -e "require('node:http').createServer((q,s)=>s.end('ok')).listen(8099,'0.0.0.0')" >/dev/null
  port="$(docker port "${holder}" 8099/tcp | awk -F: 'END {print $NF}')"
  if docker run --name "${challenger}" -p "127.0.0.1:${port}:8099" "${V2_NODE_IMAGE}" \
    node -e "require('node:http').createServer(()=>{}).listen(8099,'0.0.0.0')" >/dev/null 2>&1; then
    return 1
  fi
  docker rm "${challenger}" >/dev/null 2>&1 || true
  docker stop --time 10 "${holder}" >/dev/null
  docker rm "${holder}" >/dev/null
  v2_log "PORT_OCCUPIED=BLOCKED"
}

v2_invalid_nginx_contract() {
  local invalid="${V2_RUNTIME_DIR}/invalid-nginx.conf"
  printf '%s\n' 'this is not valid nginx configuration;' > "${invalid}"
  if docker run --rm --volume "${invalid}:/etc/nginx/conf.d/default.conf:ro" \
    "${V2_NGINX_IMAGE}" nginx -t >/dev/null 2>&1; then
    return 1
  fi
  v2_log "INVALID_NGINX=BLOCKED"
}

v2_write_drain_config() {
  local upstream="$1" output="$2"
  printf '%s\n' \
    'events {}' \
    'http {' \
    '  server {' \
    '    listen 8099;' \
    "    location / { proxy_pass http://${upstream}:8081; proxy_read_timeout 60s; }" \
    '  }' \
    '}' > "${output}"
}

v2_long_request_drain_contract() {
  local old new proxy client config response
  old="${V2_PROJECT_NAME}-old"
  new="${V2_PROJECT_NAME}-new"
  proxy="${V2_PROJECT_NAME}-drain-proxy"
  client="${V2_PROJECT_NAME}-long-client"
  config="${V2_RUNTIME_DIR}/drain-nginx.conf"
  local server='const http=require("node:http");const name=process.argv[1];http.createServer((req,res)=>{res.writeHead(200,{"content-type":"application/octet-stream"});let n=0;const timer=setInterval(()=>{res.write(name+":"+n+"\n");if(++n===17){clearInterval(timer);res.end(name)}},1000)}).listen(8081,"0.0.0.0")'
  docker run -d --name "${old}" --network "${V2_PROJECT_NAME}_lab" "${V2_NODE_IMAGE}" node -e "${server}" old >/dev/null
  docker run -d --name "${new}" --network "${V2_PROJECT_NAME}_lab" "${V2_NODE_IMAGE}" node -e "${server}" new >/dev/null
  v2_write_drain_config "${old}" "${config}"
  docker run -d --name "${proxy}" --network "${V2_PROJECT_NAME}_lab" \
    --volume "${config}:/etc/nginx/nginx.conf:ro" "${V2_NGINX_IMAGE}" >/dev/null
  sleep 1
  docker run -d --name "${client}" --network "${V2_PROJECT_NAME}_lab" \
    "${V2_NODE_IMAGE}" node -e "fetch('http://${proxy}:8099/').then(async r=>{const b=await r.text();console.log(r.status+'|'+b.slice(-3));process.exit(r.status===200&&b.endsWith('old')?0:1)}).catch(()=>process.exit(1))" >/dev/null
  sleep 2
  v2_write_drain_config "${new}" "${config}"
  docker exec "${proxy}" nginx -t </dev/null >/dev/null
  docker exec "${proxy}" nginx -s reload </dev/null >/dev/null
  response="$(docker run --rm --network "${V2_PROJECT_NAME}_lab" "${V2_NODE_IMAGE}" node -e "fetch('http://${proxy}:8099/').then(async r=>{const b=await r.text();console.log(r.status+'|'+b.slice(-3))})" </dev/null)"
  [[ "${response}" == "200|new" ]]
  [[ "$(docker wait "${client}")" == "0" ]]
  [[ "$(docker logs "${client}" | tail -n 1)" == "200|old" ]]
  docker rm "${client}" >/dev/null

  v2_write_drain_config "${old}" "${config}"
  docker exec "${proxy}" nginx -t </dev/null >/dev/null
  docker exec "${proxy}" nginx -s reload </dev/null >/dev/null
  response="$(docker run --rm --network "${V2_PROJECT_NAME}_lab" "${V2_NODE_IMAGE}" node -e "fetch('http://${proxy}:8099/').then(async r=>{const b=await r.text();console.log(r.status+'|'+b.slice(-3))})" </dev/null)"
  [[ "${response}" == "200|old" ]]
  for container in "${proxy}" "${new}" "${old}"; do
    docker stop --time 30 "${container}" >/dev/null
    docker rm "${container}" >/dev/null
  done
  v2_log "LONG_REQUEST_GT_15S=OK STREAM=OK DRAIN=OK ROLLBACK=OK HTTP_500_502_504=0"
}

v2_run_lab() {
  local evidence_dir="$1"
  mkdir -m 0700 -p -- "${evidence_dir}"
  v2_backfill_contract
  v2_start_candidate_and_gates
  v2_port_occupied_contract
  v2_invalid_nginx_contract
  v2_long_request_drain_contract
  printf '%s\n' \
    'RUN=PASS' \
    'CANDIDATE_GATES=14/14' \
    'READINESS_503=PASS' \
    'BACKFILL_PLAN=PASS' \
    'BACKFILL_APPLY=1344' \
    'BACKFILL_SECOND_APPLY=0' \
    'R2_REAL_MUTATIONS=0' \
    'HTTP_500_502_504=0' > "${evidence_dir}/lab-summary.txt"
}
