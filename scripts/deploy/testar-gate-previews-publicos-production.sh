#!/usr/bin/env bash
set -euo pipefail

# Only a newly owned, network-isolated PostgreSQL fixture is writable. The actual
# versioned gate is executed unchanged, with READ ONLY/ROLLBACK, for every case.
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd -- "${script_dir}/../.." && pwd)"
gate="${script_dir}/validar-previews-publicos-production.sql"
evidence="$(mktemp -d -t topsv3-preview-gate.XXXXXXXX)"
owner_id="$(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')"
pg="topsv3-preview-gate-${owner_id}-pg17"
flyway="topsv3-preview-gate-${owner_id}-flyway"
network="topsv3-preview-gate-${owner_id}-net"
label_key='topsv3.test-owner'
prefix='hml/gate-fixture/publicas/'
tests=0

fail() { printf 'PUBLIC_PREVIEW_GATE_TESTS_FAIL: %s\n' "$*" >&2; exit 1; }
cleanup() {
  local original_exit=$? cleanup_exit=0 name owner
  trap - EXIT
  for name in "$flyway" "$pg"; do
    if owner="$(docker inspect --format '{{index .Config.Labels "topsv3.test-owner"}}' "$name" 2>/dev/null)"; then
      if [[ "$owner" != "$owner_id" ]]; then
        printf 'CLEANUP_OWNERSHIP_MISMATCH %s\n' "$name" >&2
        cleanup_exit=1
      elif ! docker rm -f "$name" >"${evidence}/cleanup-${name}.log" 2>&1; then
        cleanup_exit=1
      fi
    fi
  done
  if owner="$(docker network inspect --format '{{index .Labels "topsv3.test-owner"}}' "$network" 2>/dev/null)"; then
    if [[ "$owner" != "$owner_id" ]]; then
      cleanup_exit=1
    elif ! docker network rm "$network" >"${evidence}/cleanup-network.log" 2>&1; then
      cleanup_exit=1
    fi
  fi
  printf 'PUBLIC_PREVIEW_GATE_TEST_EVIDENCE=%s cleanup_exit=%s\n' "$evidence" "$cleanup_exit"
  # Never replace an original failure with a cleanup success.
  if (( original_exit != 0 )); then exit "$original_exit"; fi
  exit "$cleanup_exit"
}
trap cleanup EXIT

[[ -f "$gate" ]] || fail 'versioned SQL missing'
for image in postgres:17.10-alpine flyway/flyway:12.10.0; do
  docker image inspect "$image" >"${evidence}/image-${image//[\/:]/_}.json" \
    || fail "required local image absent: ${image}; no pull attempted"
done
gate_hash_before="$(sha256sum "$gate" | awk '{print $1}')"
docker network create --internal --label "${label_key}=${owner_id}" "$network" >"${evidence}/network.log"
# Approved fictitious value: only this fresh internal test network, no published
# ports or production configuration. PostgreSQL authentication remains enabled.
POSTGRES_PASSWORD="CHANGE_ME" PGPASSWORD="CHANGE_ME" docker run --pull=never -d \
  --name "$pg" --label "${label_key}=${owner_id}" --network "$network" \
  -e POSTGRES_DB=topsv3_preview_gate -e POSTGRES_USER=topsv3test \
  -e POSTGRES_PASSWORD -e PGPASSWORD postgres:17.10-alpine >"${evidence}/postgres.log"
bash "${script_dir}/aguardar-postgres-efemero.sh" "$pg" topsv3test topsv3_preview_gate \
  >"${evidence}/readiness.log" 2>&1
migrations="${root}/backend/src/main/resources/db/migration"
if command -v cygpath >/dev/null 2>&1; then migrations="$(cygpath -am "$migrations")"; fi
MSYS_NO_PATHCONV=1 FLYWAY_PASSWORD="CHANGE_ME" timeout --kill-after=5s 180s \
  docker run --pull=never --rm --name "$flyway" --label "${label_key}=${owner_id}" \
  --network "$network" -e FLYWAY_PASSWORD -v "${migrations}:/flyway/sql:ro" \
  flyway/flyway:12.10.0 "-url=jdbc:postgresql://${pg}:5432/topsv3_preview_gate" \
  -user=topsv3test -locations=filesystem:/flyway/sql migrate >"${evidence}/flyway.log" 2>&1

sql() {
  docker exec -i "$pg" psql -X -U topsv3test -d topsv3_preview_gate \
    --set=ON_ERROR_STOP=1 --no-password -A -t -F $'\t' "$@"
}
[[ "$(sql -c 'SHOW server_version_num')" =~ ^17[0-9]+$ ]] || fail 'not PostgreSQL 17'
[[ "$(sql -c 'SELECT count(*) FROM arquivo_midia')" == 0 ]] || fail 'fixture database was not empty'

# Independent fixture helpers only; they never become part of the gate predicate.
sql >"${evidence}/fixture-bootstrap.log" <<'SQL'
INSERT INTO usuario(id,nome,status,tipo_conta,criado_em,atualizado_em,versao)
VALUES ('20000000-0000-0000-0000-000000000001','Pessoa sintetica gate','ATIVO','ANUNCIANTE',now(),now(),0);
INSERT INTO anuncio(id,usuario_id,slug,titulo,status,status_moderacao,categoria,publicado_em,criado_em,atualizado_em,versao)
VALUES ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',
 'preview-gate-sintetico','Anuncio sintetico gate','PUBLICADO','APROVADO','ACOMPANHANTE_FEMININA',now(),now(),now(),0);
CREATE SCHEMA preview_gate_fixture;
CREATE FUNCTION preview_gate_fixture.reset_rows() RETURNS void LANGUAGE plpgsql AS $fixture$
BEGIN
 DELETE FROM ativacao_beneficio WHERE anuncio_id='30000000-0000-0000-0000-000000000001';
 DELETE FROM grupo_ativacao_beneficio WHERE anuncio_id='30000000-0000-0000-0000-000000000001';
 DELETE FROM anuncio_midia WHERE anuncio_id='30000000-0000-0000-0000-000000000001';
 DELETE FROM arquivo_midia WHERE storage_provider='R2' AND bucket='preview-gate-synthetic';
 INSERT INTO arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,
   largura,altura,sha256,status_arquivo,criado_em,
   preview_restrito_tipo,preview_restrito_chave,preview_restrito_pipeline_versao,
   preview_restrito_status,preview_restrito_confirmado_em)
 SELECT ('10000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'R2','preview-gate-synthetic',
   'hml/gate-fixture/privadas/'||i||'.jpg','image/jpeg',128,640,480,repeat('a',64),'VALIDADO',now(),
   'PREVIEW_RESTRITO','hml/gate-fixture/publicas/restritas-borradas/v1/'||
     substring(encode(sha256(convert_to('10000000-0000-0000-0000-'||lpad(i::text,12,'0')||':'||repeat('a',64)||':v1','UTF8')),'hex'),1,32)||'.jpg',
   'v1','DISPONIVEL',now() FROM generate_series(1,11) i;
 INSERT INTO anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,
   visibilidade_midia,criado_em,atualizado_em)
 SELECT ('00000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
   '30000000-0000-0000-0000-000000000001',('10000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
   'FOTO',CASE WHEN i=1 THEN 'CAPA' ELSE 'GALERIA' END,i-1,'PUBLICAVEL','RESTRITA_18',now(),now()
 FROM generate_series(1,11) i;
END;
$fixture$;
CREATE FUNCTION preview_gate_fixture.enable_benefit(code text) RETURNS void LANGUAGE plpgsql AS $fixture$
DECLARE g uuid := gen_random_uuid(); b uuid;
BEGIN
 SELECT id INTO STRICT b FROM beneficio_premium WHERE codigo=code;
 INSERT INTO grupo_ativacao_beneficio(id,tipo,origem,usuario_id,anuncio_id,validade_inicio_em,
   validade_fim_em,status,criado_em,atualizado_em)
 VALUES(g,'PACOTE','ADMIN','20000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',
   now()-interval '1 hour',now()+interval '1 day','ATIVO',now(),now());
 INSERT INTO ativacao_beneficio(id,beneficio_id,usuario_id,anuncio_id,grupo_ativacao_id,origem,
   inicio_em,fim_em,status,custo_creditos_snapshot,criado_em)
 VALUES(gen_random_uuid(),b,'20000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',
   g,'ADMIN',now()-interval '30 minutes',now()+interval '12 hours','ATIVA',0,now());
END;
$fixture$;
SQL

reset_rows() { sql -c 'SELECT preview_gate_fixture.reset_rows()' >/dev/null; }
mutate() { sql -c "$1" >/dev/null; }
metadata_missing() {
  mutate "UPDATE arquivo_midia SET preview_restrito_status='DESCONHECIDO',preview_restrito_tipo=NULL,
    preview_restrito_chave=NULL,preview_restrito_pipeline_versao=NULL,preview_restrito_confirmado_em=NULL
    WHERE id='10000000-0000-0000-0000-$(printf '%012d' "$1")'"
}
fingerprint() {
  sql -c "SELECT encode(sha256(convert_to(coalesce(string_agg(row_value,E'\\n' ORDER BY row_value),''),'UTF8')),'hex')
    FROM (SELECT to_jsonb(a)::text row_value FROM arquivo_midia a UNION ALL SELECT to_jsonb(a)::text FROM anuncio_midia a
      UNION ALL SELECT to_jsonb(a)::text FROM anuncio a UNION ALL SELECT to_jsonb(a)::text FROM usuario a
      UNION ALL SELECT to_jsonb(a)::text FROM ativacao_beneficio a UNION ALL SELECT to_jsonb(a)::text FROM grupo_ativacao_beneficio a) rows"
}
value() {
  local log="$1" scope="$2" section="$3" category="$4"
  awk -F '\t' -v scope="$scope" -v section="$section" -v category="$category" '
    $2==scope && $3==section && $4==category {if (found++) exit 2; value=$7}
    END {if (found>1) exit 2; print value+0}' "$log"
}
run_case() {
  local name="$1" gallery="$2" rejected="$3" card="$4" card_rejected="$5" invalid="${6:-0}"
  local supplied_prefix="${7-$prefix}" base="${8:-true}" before after output
  output="${evidence}/${name}.tsv"
  before="$(fingerprint)"
  sql --set="public_media_prefix=${supplied_prefix}" --set="public_base_present=${base}" \
    --file=- <"$gate" >"$output" 2>"${evidence}/${name}.stderr"
  after="$(fingerprint)"
  [[ "$before" == "$after" ]] || fail "${name}: read-only gate changed fixture rows"
  [[ "$(grep -Fxc 'PUBLIC_PREVIEW_CONTRACT_QUERY_COMPLETE' "$output")" == 1 ]] \
    || fail "${name}: completion sentinel missing/duplicated"
  [[ "$(tail -n 1 "$output")" == ROLLBACK ]] || fail "${name}: transaction did not finish in ROLLBACK"
  [[ "$(value "$output" PUBLICO_GALERIA_DTO_SELECIONADO TOTAL UNIVERSO)" == "$gallery" ]] \
    || fail "${name}: gallery selection differs"
  [[ "$(value "$output" PUBLICO_GALERIA_DTO_SELECIONADO CONTRATO NAO_ACEITA_OU_NAO_COMPROVADO)" == "$rejected" ]] \
    || fail "${name}: gallery rejection count differs"
  [[ "$(value "$output" PUBLICO_CARD_DTO_SELECIONADO TOTAL UNIVERSO)" == "$card" ]] \
    || fail "${name}: card selection differs"
  [[ "$(value "$output" PUBLICO_CARD_DTO_SELECIONADO CONTRATO NAO_ACEITA_OU_NAO_COMPROVADO)" == "$card_rejected" ]] \
    || fail "${name}: card rejection count differs"
  [[ "$(value "$output" PUBLICO_SELECIONADO_ARQUIVO_INVALIDO TOTAL UNIVERSO)" == "$invalid" ]] \
    || fail "${name}: invalid selected-file count differs"
  tests=$((tests + 1))
  printf 'PASS: %s gallery=%s rejected=%s card=%s card_rejected=%s invalid=%s\n' \
    "$name" "$gallery" "$rejected" "$card" "$card_rejected" "$invalid"
}

reset_rows
run_case base_four 4 0 1 0
metadata_missing 5
run_case fifth_missing_not_promoted 4 0 1 0
mutate "UPDATE arquivo_midia SET status_arquivo='REJEITADO' WHERE id='10000000-0000-0000-0000-000000000001'"
run_case invalid_first_does_not_promote_fifth 3 0 1 0 1

reset_rows
metadata_missing 1
run_case selected_metadata_missing 4 1 1 1
reset_rows
mutate "UPDATE arquivo_midia SET preview_restrito_chave='hml/gate-fixture/publicas/restritas-borradas/v1/wrong.jpg' WHERE id='10000000-0000-0000-0000-000000000001'"
run_case selected_wrong_identity 4 1 1 1
reset_rows
mutate "UPDATE arquivo_midia SET preview_restrito_pipeline_versao='v2' WHERE id='10000000-0000-0000-0000-000000000001'"
run_case selected_wrong_pipeline 4 1 1 1
reset_rows
mutate "UPDATE arquivo_midia SET preview_restrito_status='PENDENTE' WHERE id='10000000-0000-0000-0000-000000000001'"
run_case selected_pending 4 1 1 1

reset_rows
metadata_missing 1
mutate "UPDATE anuncio_midia SET visibilidade_midia='LIVRE' WHERE id='00000000-0000-0000-0000-000000000001'"
run_case free_photo_needs_no_restricted_preview_and_wins_card 3 0 0 0

reset_rows
mutate "SELECT preview_gate_fixture.enable_benefit('FOTOS_EXTRA_5')"
metadata_missing 11
run_case extra_ten_eleventh_excluded 10 0 1 0
metadata_missing 10
run_case extra_tenth_required 10 1 1 0
mutate "UPDATE ativacao_beneficio SET revogada_em=now() WHERE anuncio_id='30000000-0000-0000-0000-000000000001'"
run_case revoked_extra_returns_four 4 0 1 0

reset_rows
mutate "SELECT preview_gate_fixture.enable_benefit('CARROSSEL_FOTOS')"
run_case carousel_selects_four 4 0 4 0
metadata_missing 4
run_case carousel_fourth_required 4 1 4 1

reset_rows
# A legitimate tie across purposes preserves the real unique(finalidade,ordem) constraint.
mutate "UPDATE anuncio_midia SET ordem=3,finalidade='CAPA',id='80000000-0000-0000-0000-000000000005' WHERE id='00000000-0000-0000-0000-000000000005'"
metadata_missing 5
run_case tied_signed_most_significant_uuid 4 1 1 0
reset_rows
mutate "UPDATE anuncio_midia SET ordem=3,finalidade='CAPA',id='00000000-0000-0000-8000-000000000005' WHERE id='00000000-0000-0000-0000-000000000005'"
metadata_missing 5
run_case tied_signed_least_significant_uuid 4 1 1 0
reset_rows
# Real migrations forbid NULL order; do not weaken schema just to exercise an impossible row.
if sql -c "UPDATE anuncio_midia SET ordem=NULL WHERE id='00000000-0000-0000-0000-000000000005'" \
    >"${evidence}/schema-null-order.log" 2>&1; then fail 'schema unexpectedly accepted NULL order'; fi
metadata_missing 5
run_case schema_rejects_null_order_without_promoting_fifth 4 0 1 0

reset_rows
mutate "UPDATE arquivo_midia SET sha256=E'\\t AABB \\n',preview_restrito_chave='hml/gate-fixture/publicas/restritas-borradas/v1/'||substring(encode(sha256(convert_to(id::text||':aabb:v1','UTF8')),'hex'),1,32)||'.jpg' WHERE id='10000000-0000-0000-0000-000000000001'"
run_case java_trim_and_ascii_lowercase 4 0 1 0
mutate "UPDATE arquivo_midia SET sha256=chr(233) WHERE id='10000000-0000-0000-0000-000000000001'"
run_case non_ascii_identity_unproven 4 1 1 1
reset_rows
run_case missing_public_base 4 4 1 1 0 "$prefix" false
run_case missing_public_prefix 4 4 1 1 0 '' true

[[ "$(sha256sum "$gate" | awk '{print $1}')" == "$gate_hash_before" ]] || fail 'gate SQL changed during fixture run'
printf 'PUBLIC_PREVIEW_GATE_TESTS=PASS cases=%s sql_sha256=%s\n' "$tests" "$gate_hash_before"
