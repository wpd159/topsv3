-- Full persisted/public selection contract audited against tree 0858206cb3e219e99507afea02f978b226a6ec81.
-- Read-only transition gate; intentionally preserves selection before file/preview filtering.
-- Required psql variables: public_media_prefix (exact effective value, no logging),
-- public_base_present (true iff effective publicBaseUrl is nonblank).
-- No storage I/O, no IDs/checksums/URLs/keys in output. Original SQL preserved.
-- A non-ASCII checksum is classified as identity UNPROVEN, never silently approximated.
-- Java trim() strips <= U+0020, not only ordinary spaces; PG cannot store U+0000.
-- Java UUID.compareTo sorts signed most-significant then signed least-significant longs.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL lock_timeout = '1s';
WITH
p AS MATERIALIZED (
  SELECT :'public_media_prefix'::text AS prefixo,
         :'public_base_present'::boolean AS base_presente,
         'v1'::text AS pipeline,
         transaction_timestamp() AS observado_em,
         (SELECT string_agg(chr(n), '' ORDER BY n) FROM generate_series(1,32) n) AS java_trim_chars,
         (SELECT string_agg(chr(n),'') FROM unnest(ARRAY[9,10,11,12,13,28,29,30,31,32,5760,8192,8193,8194,8195,8196,8197,8198,8200,8201,8202,8232,8233,8287,12288]) n) AS java_blank_chars
),
beneficios_validos AS MATERIALIZED (
  SELECT ab.anuncio_id, bp.codigo
  FROM ativacao_beneficio ab
  JOIN beneficio_premium bp ON bp.id = ab.beneficio_id
  JOIN grupo_ativacao_beneficio gb ON gb.id = ab.grupo_ativacao_id
  CROSS JOIN p
  WHERE bp.codigo IN ('FOTOS_EXTRA_5','CARROSSEL_FOTOS','VIDEO_1') AND bp.ativo IS TRUE
    AND ab.status = 'ATIVA' AND ab.revogada_em IS NULL AND ab.origem IS NOT NULL
    AND ab.inicio_em IS NOT NULL AND ab.fim_em > ab.inicio_em
    AND ab.inicio_em <= p.observado_em AND ab.fim_em > p.observado_em
    AND gb.status = 'ATIVO' AND gb.origem = ab.origem
    AND gb.usuario_id IS NOT DISTINCT FROM ab.usuario_id
    AND gb.anuncio_id IS NOT DISTINCT FROM ab.anuncio_id
    AND gb.validade_inicio_em IS NOT NULL AND gb.validade_fim_em > gb.validade_inicio_em
    AND gb.validade_inicio_em <= p.observado_em AND gb.validade_fim_em > p.observado_em
    AND ab.inicio_em >= gb.validade_inicio_em AND ab.fim_em <= gb.validade_fim_em
),
flags AS MATERIALIZED (
  SELECT anuncio_id, bool_or(codigo = 'FOTOS_EXTRA_5') AS extras,
         bool_or(codigo = 'CARROSSEL_FOTOS') AS carrossel
  FROM beneficios_validos GROUP BY anuncio_id
),
anuncios AS MATERIALIZED (
  SELECT a.id, coalesce(a.status = 'PUBLICADO' AND a.status_moderacao = 'APROVADO'
      AND a.removido_em IS NULL AND u.status = 'ATIVO' AND u.tipo_conta = 'ANUNCIANTE'
      AND u.desativado_em IS NULL AND u.excluido_em IS NULL, false) AS publico,
      CASE WHEN coalesce(f.extras,false) THEN 10 ELSE 4 END AS limite_fotos,
      coalesce(f.carrossel,false) AS carrossel
  FROM anuncio a LEFT JOIN usuario u ON u.id = a.usuario_id
  LEFT JOIN flags f ON f.anuncio_id = a.id
),
posicoes AS MATERIALIZED (
  SELECT am.id,
    row_number() OVER (PARTITION BY am.anuncio_id ORDER BY am.ordem NULLS LAST,
      CASE WHEN get_byte(uuid_send(am.id),0) >= 128 THEN 0 ELSE 1 END,
      substring(am.id::text,1,18),
      CASE WHEN get_byte(uuid_send(am.id),8) >= 128 THEN 0 ELSE 1 END,
      substring(am.id::text,20,17)) AS posicao_foto
  FROM anuncio_midia am
  WHERE am.tipo = 'FOTO' AND am.status = 'PUBLICAVEL'
    AND am.finalidade IS DISTINCT FROM 'STORY' AND am.visibilidade_midia IS NOT NULL
),
galeria_fotos_validas AS MATERIALIZED (
  SELECT am.id, am.anuncio_id, am.visibilidade_midia,
    row_number() OVER (PARTITION BY am.anuncio_id ORDER BY
      CASE WHEN am.visibilidade_midia = 'LIVRE' THEN 0 ELSE 1 END,
      am.ordem NULLS LAST,
      CASE WHEN get_byte(uuid_send(am.id),0) >= 128 THEN 0 ELSE 1 END,
      substring(am.id::text,1,18),
      CASE WHEN get_byte(uuid_send(am.id),8) >= 128 THEN 0 ELSE 1 END,
      substring(am.id::text,20,17)) AS posicao_foto_card
  FROM anuncio_midia am JOIN posicoes pf ON pf.id = am.id
  JOIN anuncios a ON a.id = am.anuncio_id
  JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id AND ar.status_arquivo = 'VALIDADO'
  WHERE pf.posicao_foto <= a.limite_fotos
),
arquivos_pertinentes AS MATERIALIZED (
  SELECT ar.*, p.prefixo, p.pipeline, p.base_presente, p.java_blank_chars,
    (ar.sha256 IS NULL OR octet_length(ar.sha256) = char_length(ar.sha256)) AS checksum_ascii,
    CASE WHEN ar.sha256 IS NULL THEN 'sem-checksum'
      ELSE translate(btrim(ar.sha256,p.java_trim_chars),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')
    END AS checksum_normalizado
  FROM arquivo_midia ar CROSS JOIN p
  WHERE EXISTS (SELECT 1 FROM anuncio_midia am WHERE am.arquivo_midia_id = ar.id
    AND am.tipo = 'FOTO' AND am.visibilidade_midia = 'RESTRITA_18'
    AND am.finalidade IS DISTINCT FROM 'STORY')
),
identidades AS MATERIALIZED (
  SELECT ar.*,
    CASE WHEN checksum_ascii AND prefixo IS NOT NULL AND length(translate(prefixo,java_blank_chars,'')) > 0
      THEN prefixo || 'restritas-borradas/' || pipeline || '/' ||
        substring(encode(sha256(convert_to(id::text || ':' || checksum_normalizado || ':' || pipeline,'UTF8')),'hex'),1,32) || '.jpg'
      ELSE NULL END AS chave_esperada
  FROM arquivos_pertinentes ar
),
diagnosticos AS MATERIALIZED (
  SELECT ar.*,
    coalesce(ar.preview_restrito_status = 'DISPONIVEL'
      AND ar.preview_restrito_tipo = 'PREVIEW_RESTRITO'
      AND ar.preview_restrito_pipeline_versao = ar.pipeline
      AND ar.preview_restrito_chave = ar.chave_esperada
      AND ar.preview_restrito_confirmado_em IS NOT NULL
      AND ar.checksum_ascii AND ar.base_presente,false) AS aceita_contrato,
    array_remove(ARRAY[
      CASE WHEN ar.preview_restrito_status IS DISTINCT FROM 'DISPONIVEL' THEN 'STATUS_NAO_DISPONIVEL' END,
      CASE WHEN ar.preview_restrito_tipo IS DISTINCT FROM 'PREVIEW_RESTRITO' THEN 'TIPO_INCOMPATIVEL' END,
      CASE WHEN ar.preview_restrito_chave IS NULL OR length(translate(ar.preview_restrito_chave,ar.java_blank_chars,''))=0 THEN 'CHAVE_AUSENTE_OU_BLANK' END,
      CASE WHEN ar.preview_restrito_pipeline_versao IS DISTINCT FROM ar.pipeline THEN 'PIPELINE_INCOMPATIVEL' END,
      CASE WHEN ar.preview_restrito_confirmado_em IS NULL THEN 'CONFIRMACAO_AUSENTE' END,
      CASE WHEN ar.checksum_ascii AND ar.chave_esperada IS NOT NULL
        AND ar.preview_restrito_chave IS DISTINCT FROM ar.chave_esperada THEN 'IDENTIDADE_INCOMPATIVEL' END,
      CASE WHEN NOT ar.checksum_ascii THEN 'IDENTIDADE_NAO_COMPROVADA_CHECKSUM_NAO_ASCII' END,
      CASE WHEN ar.chave_esperada IS NULL AND ar.checksum_ascii THEN 'PREFIXO_NAO_CONFIGURADO' END,
      CASE WHEN NOT ar.base_presente THEN 'BASE_PUBLICA_NAO_CONFIGURADA' END
    ],NULL) AS motivos
  FROM identidades ar
),
universo AS MATERIALIZED (
  SELECT am.id AS vinculo_id, am.anuncio_id, am.arquivo_midia_id,
    coalesce(a.publico,false) AS publico, coalesce(a.limite_fotos,4) AS limite_fotos,
    coalesce(a.carrossel,false) AS carrossel,
    am.status AS status_vinculo, ar.status_arquivo,
    ar.id IS NOT NULL AS arquivo_existe, coalesce(ar.aceita_contrato,false) AS aceita_contrato,
    CASE WHEN ar.id IS NULL THEN 'ARQUIVO_AUSENTE'
      WHEN ar.preview_restrito_status IS NULL THEN 'AUSENTE_NULL'
      WHEN ar.preview_restrito_status IN ('DESCONHECIDO','PENDENTE','DISPONIVEL','FALHA','REMOVIDO')
        THEN ar.preview_restrito_status ELSE 'OUTRO_NAO_RECONHECIDO' END AS estado,
    CASE WHEN ar.id IS NULL THEN ARRAY['ARQUIVO_AUSENTE']::text[] ELSE ar.motivos END AS motivos,
    coalesce(pf.posicao_foto <= a.limite_fotos,false) AS selecionada_antes_filtro,
    gf.id IS NOT NULL AS galeria,
    gf.id IS NOT NULL AND (a.carrossel OR gf.posicao_foto_card = 1) AS card
  FROM anuncio_midia am
  LEFT JOIN anuncios a ON a.id = am.anuncio_id
  LEFT JOIN diagnosticos ar ON ar.id = am.arquivo_midia_id
  LEFT JOIN posicoes pf ON pf.id = am.id
  LEFT JOIN galeria_fotos_validas gf ON gf.id = am.id
  WHERE am.tipo = 'FOTO' AND am.visibilidade_midia = 'RESTRITA_18'
    AND am.finalidade IS DISTINCT FROM 'STORY'
),
escopos AS MATERIALIZED (
 SELECT u.*, s.escopo FROM universo u CROSS JOIN LATERAL (VALUES
  ('HISTORICO_RESTRITO_TODOS',true),
  ('HISTORICO_VINCULO_PUBLICAVEL_ARQUIVO_VALIDADO',u.status_vinculo='PUBLICAVEL' AND u.status_arquivo='VALIDADO'),
  ('ANUNCIO_NAO_PUBLICO_HISTORICO',NOT u.publico),
  ('PUBLICO_SELECIONADO_ANTES_FILTRO',u.publico AND u.selecionada_antes_filtro),
  ('PUBLICO_SELECIONADO_ARQUIVO_INVALIDO',u.publico AND u.selecionada_antes_filtro AND NOT u.galeria),
  ('PUBLICO_GALERIA_DTO_SELECIONADO',u.publico AND u.galeria),
  ('PUBLICO_CARD_DTO_SELECIONADO',u.publico AND u.card),
  ('PUBLICO_NAO_SELECIONADO_HISTORICO',u.publico AND NOT u.selecionada_antes_filtro)
 ) s(escopo,incluir) WHERE s.incluir
),
linhas AS (
 SELECT escopo,'TOTAL'::text AS secao,'UNIVERSO'::text AS categoria,
   count(DISTINCT arquivo_midia_id) AS arquivos_distintos_referenciados,
   count(DISTINCT arquivo_midia_id) FILTER (WHERE arquivo_existe) AS arquivos_distintos_existentes,
   count(DISTINCT vinculo_id) AS vinculos,count(DISTINCT anuncio_id) AS anuncios
 FROM escopos GROUP BY escopo
 UNION ALL
 SELECT escopo,'CONTRATO',CASE WHEN aceita_contrato THEN 'ACEITA_COMPLETO' ELSE 'NAO_ACEITA_OU_NAO_COMPROVADO' END,
   count(DISTINCT arquivo_midia_id),count(DISTINCT arquivo_midia_id) FILTER (WHERE arquivo_existe),count(DISTINCT vinculo_id),count(DISTINCT anuncio_id)
 FROM escopos GROUP BY escopo,aceita_contrato
 UNION ALL
 SELECT escopo,'ESTADO_PERSISTIDO',estado,count(DISTINCT arquivo_midia_id),
   count(DISTINCT arquivo_midia_id) FILTER (WHERE arquivo_existe),count(DISTINCT vinculo_id),count(DISTINCT anuncio_id)
 FROM escopos GROUP BY escopo,estado
 UNION ALL
 SELECT escopo,'MOTIVO_SOBREPOSTO',motivo,count(DISTINCT arquivo_midia_id),
   count(DISTINCT arquivo_midia_id) FILTER (WHERE arquivo_existe),count(DISTINCT vinculo_id),count(DISTINCT anuncio_id)
 FROM escopos CROSS JOIN LATERAL unnest(motivos) motivo GROUP BY escopo,motivo
 UNION ALL
 SELECT escopo,'COMBINACAO_EXCLUSIVA',CASE WHEN cardinality(motivos)=0 THEN 'SEM_MOTIVOS' ELSE array_to_string(motivos,'+') END,
   count(DISTINCT arquivo_midia_id),count(DISTINCT arquivo_midia_id) FILTER (WHERE arquivo_existe),count(DISTINCT vinculo_id),count(DISTINCT anuncio_id)
 FROM escopos GROUP BY escopo,motivos
 UNION ALL
 SELECT escopo,'LIMITE_E_CARROSSEL',limite_fotos::text || '_FOTOS_CARROSSEL_' || carrossel::text,
   count(DISTINCT arquivo_midia_id),count(DISTINCT arquivo_midia_id) FILTER (WHERE arquivo_existe),count(DISTINCT vinculo_id),count(DISTINCT anuncio_id)
 FROM escopos GROUP BY escopo,limite_fotos,carrossel
 UNION ALL
 SELECT s.escopo,'TOTAL','UNIVERSO',0,0,0,0
 FROM (VALUES ('PUBLICO_GALERIA_DTO_SELECIONADO'),('PUBLICO_CARD_DTO_SELECIONADO')) s(escopo)
 WHERE NOT EXISTS (SELECT 1 FROM escopos e WHERE e.escopo = s.escopo)
 UNION ALL
 SELECT s.escopo,'CONTRATO',c.categoria,0,0,0,0
 FROM (VALUES ('PUBLICO_GALERIA_DTO_SELECIONADO'),('PUBLICO_CARD_DTO_SELECIONADO')) s(escopo)
 CROSS JOIN (VALUES ('ACEITA_COMPLETO',true),('NAO_ACEITA_OU_NAO_COMPROVADO',false)) c(categoria,aceita)
 WHERE NOT EXISTS (SELECT 1 FROM escopos e WHERE e.escopo = s.escopo AND e.aceita_contrato = c.aceita)
)
SELECT p.observado_em, l.* FROM linhas l CROSS JOIN p ORDER BY escopo,secao,categoria;
SELECT 'PUBLIC_PREVIEW_CONTRACT_QUERY_COMPLETE';
ROLLBACK;
