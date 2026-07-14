-- Tops do Job V3 - Bloco 7
-- Dados sinteticos locais, neutros e descartaveis.
-- Nao usar em producao. Nao contem nome real, telefone, e-mail, CPF, documento, foto, URL real, pagamento ou storage real.

INSERT INTO usuario (
  id,
  nome,
  email_normalizado,
  telefone_normalizado,
  data_nascimento,
  status,
  tipo_conta,
  criado_em,
  atualizado_em,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000101',
  'Usuário de demonstração',
  NULL,
  NULL,
  DATE '1990-06-15',
  'ATIVO',
  'ANUNCIANTE',
  now(),
  now(),
  0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO categoria_home (
  id,
  categoria_enum,
  nome,
  descricao,
  destino,
  imagem_publica_url,
  ordem,
  ativo
) VALUES
  ('f2000000-0000-4000-8000-000000000001', 'ACOMPANHANTE_FEMININA', 'Acompanhante feminina', 'Encontre as melhores acompanhantes femininas.', '/anuncios?categoria=ACOMPANHANTE_FEMININA', '/cards/acompanhante-feminina.jpg', 1, true),
  ('f2000000-0000-4000-8000-000000000002', 'VENDA_DE_CONTEUDO', 'Sexo Virtual', 'Videochamadas, conteúdo exclusivo e atendimento online.', '/anuncios?categoria=VENDA_DE_CONTEUDO', '/cards/casual.jpg', 2, true),
  ('f2000000-0000-4000-8000-000000000003', 'ACOMPANHANTE_MASCULINO', 'Acompanhante masculino', 'Homens elegantes e discretos.', '/anuncios?categoria=ACOMPANHANTE_MASCULINO', '/cards/acompanhante-masculino.jpg', 3, true),
  ('f2000000-0000-4000-8000-000000000004', 'TRANSEX_TRAVESTIS', 'Transex e Travestis', 'As mais desejadas transex e travestis.', '/anuncios?categoria=TRANSEX_TRAVESTIS', '/cards/acompanhante-trans.jpg', 4, true),
  ('f2000000-0000-4000-8000-000000000005', 'MASSAGENS', 'Massagens', 'Massagistas sensuais e terapêuticas.', '/anuncios?categoria=MASSAGENS', '/cards/massagem.jpg', 5, true),
  ('f2000000-0000-4000-8000-000000000006', 'ENCONTROS_CASUAIS', 'Casual e encontros', 'Encontros leves e espontâneos.', '/anuncios?categoria=ENCONTROS_CASUAIS', '/cards/casual.jpg', 6, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO usuario (
  id,
  nome,
  email_normalizado,
  telefone_normalizado,
  data_nascimento,
  status,
  tipo_conta,
  criado_em,
  atualizado_em,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000102',
  'Usuário financeiro de demonstração',
  NULL,
  NULL,
  DATE '1992-02-20',
  'ATIVO',
  'ANUNCIANTE',
  now(),
  now(),
  0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO estado (
  id,
  uf,
  nome,
  nome_normalizado,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000201',
  'ZZ',
  'Estado de demonstração',
  'estado demonstracao',
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO cidade (
  id,
  estado_id,
  nome,
  nome_normalizado,
  slug,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000301',
  '00000000-0000-4000-8000-000000000201',
  'Cidade de demonstração',
  'cidade demonstracao',
  'cidade-sintetica',
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO bairro (
  id,
  cidade_id,
  nome,
  nome_normalizado,
  slug,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000401',
  '00000000-0000-4000-8000-000000000301',
  'Bairro de demonstração',
  'bairro demonstracao',
  'bairro-sintetico',
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio (
  id,
  usuario_id,
  slug,
  titulo,
  descricao,
  status,
  status_moderacao,
  categoria,
  preco,
  whatsapp_normalizado,
  publicado_em,
  ultima_publicacao_em,
  criado_em,
  atualizado_em,
  removido_em,
  origem_importacao_id,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000501',
  '00000000-0000-4000-8000-000000000101',
  'anuncio-sintetico-local',
  'Anúncio de demonstração',
  'Perfil de demonstração para validação.',
  'PUBLICADO',
  'APROVADO',
  'SINTETICO',
  NULL,
  '+5500000000000',
  now(),
  now(),
  now(),
  now(),
  NULL,
  NULL,
  0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio (
  id,
  usuario_id,
  slug,
  titulo,
  descricao,
  status,
  status_moderacao,
  categoria,
  preco,
  whatsapp_normalizado,
  publicado_em,
  ultima_publicacao_em,
  criado_em,
  atualizado_em,
  removido_em,
  origem_importacao_id,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000503',
  '00000000-0000-4000-8000-000000000101',
  'anuncio-sintetico-pendente-local',
  'Anúncio de demonstração pendente',
  'Perfil de demonstração para validação administrativa.',
  'PENDENTE_REVISAO',
  'PENDENTE',
  'SINTETICO',
  NULL,
  NULL,
  NULL,
  NULL,
  now(),
  now(),
  NULL,
  NULL,
  0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio (
  id,
  usuario_id,
  slug,
  titulo,
  descricao,
  status,
  status_moderacao,
  categoria,
  preco,
  whatsapp_normalizado,
  publicado_em,
  ultima_publicacao_em,
  criado_em,
  atualizado_em,
  removido_em,
  origem_importacao_id,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000502',
  '00000000-0000-4000-8000-000000000101',
  'anuncio-sintetico-midia-restrita-local',
  'Anúncio de demonstração com mídia restrita',
  'Perfil de demonstração protegido por confirmação de idade.',
  'PUBLICADO',
  'APROVADO',
  'SINTETICO',
  NULL,
  '+5500000000000',
  now(),
  now(),
  now(),
  now(),
  NULL,
  NULL,
  0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio_localizacao (
  anuncio_id,
  estado_id,
  cidade_id,
  bairro_id,
  endereco_resumido,
  latitude,
  longitude,
  criado_em,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000501',
  '00000000-0000-4000-8000-000000000201',
  '00000000-0000-4000-8000-000000000301',
  '00000000-0000-4000-8000-000000000401',
  'Endereço de demonstração',
  NULL,
  NULL,
  now(),
  now()
) ON CONFLICT (anuncio_id) DO NOTHING;

INSERT INTO anuncio_localizacao (
  anuncio_id,
  estado_id,
  cidade_id,
  bairro_id,
  endereco_resumido,
  latitude,
  longitude,
  criado_em,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000503',
  '00000000-0000-4000-8000-000000000201',
  '00000000-0000-4000-8000-000000000301',
  '00000000-0000-4000-8000-000000000401',
  'Endereço de demonstração',
  NULL,
  NULL,
  now(),
  now()
) ON CONFLICT (anuncio_id) DO NOTHING;

INSERT INTO anuncio_localizacao (
  anuncio_id,
  estado_id,
  cidade_id,
  bairro_id,
  endereco_resumido,
  latitude,
  longitude,
  criado_em,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000502',
  '00000000-0000-4000-8000-000000000201',
  '00000000-0000-4000-8000-000000000301',
  '00000000-0000-4000-8000-000000000401',
  'Endereço de demonstração',
  NULL,
  NULL,
  now(),
  now()
) ON CONFLICT (anuncio_id) DO NOTHING;

INSERT INTO arquivo_midia (
  id,
  storage_provider,
  bucket,
  chave_objeto,
  nome_original,
  mime_type,
  tamanho_bytes,
  largura,
  altura,
  duracao_ms,
  sha256,
  etag,
  status_arquivo,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000701',
  'LOCAL_SINTETICO',
  'bucket-sintetico-local',
  'synthetic/story-restrita-18.bin',
  NULL,
  'video/mp4',
  1,
  NULL,
  NULL,
  1000,
  NULL,
  NULL,
  'VALIDADO',
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO arquivo_midia (
  id,
  storage_provider,
  bucket,
  chave_objeto,
  nome_original,
  mime_type,
  tamanho_bytes,
  largura,
  altura,
  duracao_ms,
  sha256,
  etag,
  status_arquivo,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000720',
  'LOCAL_SINTETICO',
  'bucket-sintetico-local',
  'synthetic/foto-restrita-18.bin',
  NULL,
  'image/jpeg',
  1,
  NULL,
  NULL,
  NULL,
  NULL,
  NULL,
  'VALIDADO',
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO arquivo_midia (
  id,
  storage_provider,
  bucket,
  chave_objeto,
  nome_original,
  mime_type,
  tamanho_bytes,
  largura,
  altura,
  duracao_ms,
  sha256,
  etag,
  status_arquivo,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000704',
  'LOCAL_SINTETICO',
  'bucket-sintetico-local',
  'synthetic/midia-pendente.bin',
  NULL,
  'image/jpeg',
  1,
  NULL,
  NULL,
  NULL,
  NULL,
  NULL,
  'PENDENTE',
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio_midia (
  id,
  anuncio_id,
  arquivo_midia_id,
  tipo,
  finalidade,
  ordem,
  status,
  visibilidade_midia,
  criado_em,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000702',
  '00000000-0000-4000-8000-000000000501',
  '00000000-0000-4000-8000-000000000701',
  'STORY',
  'STORY',
  0,
  'PUBLICAVEL',
  'RESTRITA_18',
  now(),
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio_midia (
  id,
  anuncio_id,
  arquivo_midia_id,
  tipo,
  finalidade,
  ordem,
  status,
  visibilidade_midia,
  criado_em,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000721',
  '00000000-0000-4000-8000-000000000502',
  '00000000-0000-4000-8000-000000000720',
  'FOTO',
  'CAPA',
  0,
  'PUBLICAVEL',
  'RESTRITA_18',
  now(),
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio_midia (
  id,
  anuncio_id,
  arquivo_midia_id,
  tipo,
  finalidade,
  ordem,
  status,
  visibilidade_midia,
  criado_em,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000705',
  '00000000-0000-4000-8000-000000000503',
  '00000000-0000-4000-8000-000000000704',
  'FOTO',
  'CAPA',
  0,
  'PENDENTE',
  NULL,
  now(),
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO revisao_anuncio (
  id,
  anuncio_id,
  tipo,
  status,
  payload_solicitado,
  criado_por,
  criado_em,
  finalizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000801',
  '00000000-0000-4000-8000-000000000503',
  'CRIACAO',
  'ABERTA',
  '{"origem":"sintetica-local"}'::jsonb,
  NULL,
  now(),
  NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio (
  id,
  usuario_id,
  slug,
  titulo,
  descricao,
  status,
  status_moderacao,
  categoria,
  preco,
  whatsapp_normalizado,
  publicado_em,
  ultima_publicacao_em,
  criado_em,
  atualizado_em,
  removido_em,
  origem_importacao_id,
  versao
) VALUES
  (
    '00000000-0000-4000-8000-000000000504',
    '00000000-0000-4000-8000-000000000101',
    'anuncio-sintetico-reprovar-local',
    'Anúncio de demonstração para reprovação',
    'Perfil de demonstração para decisão de reprovação.',
    'PENDENTE_REVISAO',
    'PENDENTE',
    'SINTETICO',
    NULL,
    NULL,
    NULL,
    NULL,
    now(),
    now(),
    NULL,
    NULL,
    0
  ),
  (
    '00000000-0000-4000-8000-000000000505',
    '00000000-0000-4000-8000-000000000101',
    'anuncio-sintetico-finalizado-local',
    'Anúncio de demonstração finalizado',
    'Perfil de demonstração para conflito de revisão finalizada.',
    'APROVADO',
    'APROVADO',
    'SINTETICO',
    NULL,
    NULL,
    NULL,
    NULL,
    now(),
    now(),
    NULL,
    NULL,
    0
  ),
  (
    '00000000-0000-4000-8000-000000000506',
    '00000000-0000-4000-8000-000000000101',
    'anuncio-sintetico-moderador-local',
    'Anúncio de demonstração do moderador',
    'Perfil de demonstração para decisão por moderador.',
    'PENDENTE_REVISAO',
    'PENDENTE',
    'SINTETICO',
    NULL,
    NULL,
    NULL,
    NULL,
    now(),
    now(),
    NULL,
    NULL,
    0
  )
ON CONFLICT (id) DO NOTHING;

-- Bloco 25 - prova de resultado/desempenho sintetica local.
-- Nao representa tracking externo, Google Analytics, pixel, dado real, IP real, user-agent real, telefone real ou promessa de contratacao.

INSERT INTO anuncio (
  id,
  usuario_id,
  slug,
  titulo,
  descricao,
  status,
  status_moderacao,
  categoria,
  preco,
  whatsapp_normalizado,
  publicado_em,
  ultima_publicacao_em,
  criado_em,
  atualizado_em,
  removido_em,
  origem_importacao_id,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000511',
  '00000000-0000-4000-8000-000000000101',
  'anuncio-sintetico-sem-metricas-local',
  'Anúncio de demonstração sem métricas',
  'Perfil de demonstração para validar estado vazio de prova de resultado.',
  'PUBLICADO',
  'APROVADO',
  'SINTETICO',
  NULL,
  '+5500000000000',
  now() - interval '3 days',
  now() - interval '3 days',
  now() - interval '3 days',
  now() - interval '3 days',
  NULL,
  NULL,
  0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio_localizacao (
  anuncio_id,
  estado_id,
  cidade_id,
  bairro_id,
  endereco_resumido,
  latitude,
  longitude,
  criado_em,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000511',
  '00000000-0000-4000-8000-000000000201',
  '00000000-0000-4000-8000-000000000301',
  '00000000-0000-4000-8000-000000000401',
  'Endereço de demonstração',
  NULL,
  NULL,
  now(),
  now()
) ON CONFLICT (anuncio_id) DO NOTHING;

INSERT INTO documento_busca_anuncio (
  anuncio_id,
  texto_busca,
  estado_id,
  cidade_id,
  bairro_id,
  categoria,
  preco,
  status_publicacao,
  tem_midia_valida,
  beneficios_ranking_json,
  ranking_base,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000511',
  'anuncio demonstracao sem metricas cidade demonstracao bairro demonstracao',
  '00000000-0000-4000-8000-000000000201',
  '00000000-0000-4000-8000-000000000301',
  '00000000-0000-4000-8000-000000000401',
  'SINTETICO',
  NULL,
  'PUBLICAVEL',
  false,
  '{}'::jsonb,
  1,
  now()
) ON CONFLICT (anuncio_id) DO NOTHING;

INSERT INTO evento_visualizacao (
  id,
  anuncio_id,
  visitante_hash,
  ip_hash,
  user_agent_hash,
  referer_hash,
  origem_pais,
  origem_uf,
  origem_cidade,
  dispositivo,
  request_id,
  criado_em
) VALUES
  ('00000000-0000-4000-8000-000000000801', '00000000-0000-4000-8000-000000000501', 'visitante-hash-sintetico-0801', 'ip-hash-sintetico-local-0801', 'ua-hash-sintetico-local-0801', 'referer-hash-sintetico-local-0801', 'BR', 'ZZ', 'Cidade de demonstração', 'DESKTOP', 'req-metrica-local-0801', now() - interval '3 days'),
  ('00000000-0000-4000-8000-000000000802', '00000000-0000-4000-8000-000000000501', 'visitante-hash-sintetico-0802', 'ip-hash-sintetico-local-0802', 'ua-hash-sintetico-local-0802', 'referer-hash-sintetico-local-0802', 'BR', 'ZZ', 'Cidade de demonstração', 'MOBILE', 'req-metrica-local-0802', now() - interval '1 day'),
  ('00000000-0000-4000-8000-000000000803', '00000000-0000-4000-8000-000000000501', 'visitante-hash-sintetico-0803', 'ip-hash-sintetico-local-0803', 'ua-hash-sintetico-local-0803', 'referer-hash-sintetico-local-0803', 'BR', 'ZZ', 'Cidade de demonstração', 'MOBILE', 'req-metrica-local-0803', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO clique_whatsapp (
  id,
  anuncio_id,
  visitante_hash,
  ip_hash,
  user_agent_hash,
  origem_pais,
  origem_uf,
  origem_cidade,
  dispositivo,
  permitido,
  motivo_bloqueio,
  request_id,
  criado_em
) VALUES
  ('00000000-0000-4000-8000-000000000811', '00000000-0000-4000-8000-000000000501', 'visitante-hash-sintetico-0811', 'ip-hash-sintetico-local-0811', 'ua-hash-sintetico-local-0811', 'BR', 'ZZ', 'Cidade de demonstração', 'DESKTOP', true, NULL, 'req-clique-local-0811', now() - interval '3 days'),
  ('00000000-0000-4000-8000-000000000812', '00000000-0000-4000-8000-000000000501', 'visitante-hash-sintetico-0812', 'ip-hash-sintetico-local-0812', 'ua-hash-sintetico-local-0812', 'BR', 'ZZ', 'Cidade de demonstração', 'MOBILE', true, NULL, 'req-clique-local-0812', now() - interval '1 day'),
  ('00000000-0000-4000-8000-000000000813', '00000000-0000-4000-8000-000000000501', 'visitante-hash-sintetico-0813', 'ip-hash-sintetico-local-0813', 'ua-hash-sintetico-local-0813', 'BR', 'ZZ', 'Cidade de demonstração', 'MOBILE', true, NULL, 'req-clique-local-0813', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO agregado_visualizacao_diaria (
  id,
  anuncio_id,
  data_referencia,
  origem_uf,
  origem_cidade,
  origem_uf_chave,
  origem_cidade_chave,
  total_visualizacoes,
  visitantes_estimados,
  atualizado_em
) VALUES
  ('00000000-0000-4000-8000-000000000821', '00000000-0000-4000-8000-000000000501', current_date - 3, 'ZZ', 'Cidade de demonstração', 'ZZ', 'cidade-sintetica', 40, 25, now()),
  ('00000000-0000-4000-8000-000000000822', '00000000-0000-4000-8000-000000000501', current_date - 1, 'ZZ', 'Cidade de demonstração', 'ZZ', 'cidade-sintetica', 80, 45, now()),
  ('00000000-0000-4000-8000-000000000823', '00000000-0000-4000-8000-000000000501', current_date, 'ZZ', 'Cidade de demonstração', 'ZZ', 'cidade-sintetica', 100, 60, now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO agregado_clique_whatsapp_diario (
  id,
  anuncio_id,
  data_referencia,
  origem_uf,
  origem_cidade,
  origem_uf_chave,
  origem_cidade_chave,
  total_cliques,
  visitantes_estimados,
  atualizado_em
) VALUES
  ('00000000-0000-4000-8000-000000000831', '00000000-0000-4000-8000-000000000501', current_date - 3, 'ZZ', 'Cidade de demonstração', 'ZZ', 'cidade-sintetica', 4, 4, now()),
  ('00000000-0000-4000-8000-000000000832', '00000000-0000-4000-8000-000000000501', current_date - 1, 'ZZ', 'Cidade de demonstração', 'ZZ', 'cidade-sintetica', 12, 10, now()),
  ('00000000-0000-4000-8000-000000000833', '00000000-0000-4000-8000-000000000501', current_date, 'ZZ', 'Cidade de demonstração', 'ZZ', 'cidade-sintetica', 16, 14, now())
ON CONFLICT (id) DO NOTHING;

-- Bloco 23 - dados sinteticos locais para creditos/ledger read-only.
INSERT INTO pagamento (
  id,
  usuario_id,
  plano_credito_id,
  provedor,
  metodo,
  txid,
  identificador_provedor,
  valor,
  moeda,
  quantidade_creditos,
  status_interno,
  status_provedor,
  expiracao_em,
  aprovado_em,
  cancelado_em,
  creditado_em,
  idempotency_key,
  criado_em,
  atualizado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000691',
    '00000000-0000-4000-8000-000000000101',
    NULL,
    'DESCONHECIDO',
    'DESCONHECIDO',
    NULL,
    NULL,
    10.00,
    'BRL',
    100,
    'APROVADO',
    'APROVADO_LOCAL_SINTETICO',
    NULL,
    now() - interval '2 hours',
    NULL,
    now() - interval '90 minutes',
    'credito-sintetico-pagamento-ok',
    now() - interval '2 hours',
    now() - interval '90 minutes'
  ),
  (
    '00000000-0000-4000-8000-000000000692',
    '00000000-0000-4000-8000-000000000101',
    NULL,
    'DESCONHECIDO',
    'DESCONHECIDO',
    NULL,
    NULL,
    20.00,
    'BRL',
    200,
    'APROVADO',
    'APROVADO_LOCAL_SINTETICO',
    NULL,
    now() - interval '1 hour',
    NULL,
    NULL,
    'credito-sintetico-pagamento-sem-credito',
    now() - interval '1 hour',
    now() - interval '1 hour'
  ),
  (
    '00000000-0000-4000-8000-000000000693',
    '00000000-0000-4000-8000-000000000101',
    NULL,
    'DESCONHECIDO',
    'DESCONHECIDO',
    NULL,
    NULL,
    5.00,
    'BRL',
    50,
    'AGUARDANDO_PAGAMENTO',
    'PENDENTE_LOCAL_SINTETICO',
    now() + interval '1 hour',
    NULL,
    NULL,
    NULL,
    'credito-sintetico-pagamento-nao-confirmado',
    now() - interval '30 minutes',
    now() - interval '30 minutes'
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO movimento_credito (
  id,
  usuario_id,
  tipo,
  direcao,
  quantidade,
  saldo_antes,
  saldo_depois,
  origem,
  referencia_tipo,
  referencia_id,
  idempotency_key,
  ator_usuario_id,
  observacao,
  criado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000681',
    '00000000-0000-4000-8000-000000000101',
    'ENTRADA',
    'CREDITO',
    100,
    0,
    100,
    'PAGAMENTO',
    'PAGAMENTO',
    '00000000-0000-4000-8000-000000000691',
    'credito-sintetico-ledger-pagamento-ok',
    NULL,
    'Crédito conciliado para conferência.',
    now() - interval '2 hours'
  ),
  (
    '00000000-0000-4000-8000-000000000682',
    '00000000-0000-4000-8000-000000000101',
    'SAIDA',
    'DEBITO',
    30,
    100,
    70,
    'BENEFICIO',
    'ATIVACAO_BENEFICIO',
    '00000000-0000-4000-8000-000000000661',
    'credito-sintetico-ledger-beneficio',
    NULL,
    'Débito por benefício.',
    now() - interval '110 minutes'
  ),
  (
    '00000000-0000-4000-8000-000000000683',
    '00000000-0000-4000-8000-000000000101',
    'ENTRADA',
    'CREDITO',
    10,
    70,
    80,
    'CAMPANHA',
    'CAMPANHA',
    NULL,
    'credito-sintetico-ledger-campanha',
    NULL,
    'Crédito de campanha.',
    now() - interval '100 minutes'
  ),
  (
    '00000000-0000-4000-8000-000000000684',
    '00000000-0000-4000-8000-000000000101',
    'AJUSTE',
    'CREDITO',
    5,
    80,
    85,
    'AJUSTE_ADMIN',
    'AJUSTE_LOCAL',
    NULL,
    'credito-sintetico-ledger-ajuste',
    '00000000-0000-4000-8000-000000000101',
    'Ajuste administrativo pendente de revisão.',
    now() - interval '90 minutes'
  ),
  (
    '00000000-0000-4000-8000-000000000685',
    '00000000-0000-4000-8000-000000000101',
    'ENTRADA',
    'CREDITO',
    7,
    85,
    92,
    'PAGAMENTO',
    NULL,
    NULL,
    'credito-sintetico-ledger-sem-pagamento',
    NULL,
    'Crédito sem pagamento vinculado para alerta.',
    now() - interval '80 minutes'
  ),
  (
    '00000000-0000-4000-8000-000000000686',
    '00000000-0000-4000-8000-000000000101',
    'ENTRADA',
    'CREDITO',
    50,
    92,
    142,
    'PAGAMENTO',
    'PAGAMENTO',
    '00000000-0000-4000-8000-000000000693',
    'credito-sintetico-ledger-pagamento-pendente',
    NULL,
    'Crédito vinculado a pagamento não aprovado.',
    now() - interval '70 minutes'
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO pagamento_conciliacao (
  id,
  pagamento_id,
  movimento_credito_id,
  origem,
  status,
  valor_confirmado,
  creditos_confirmados,
  aprovado_em,
  creditado_em,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000694',
  '00000000-0000-4000-8000-000000000691',
  '00000000-0000-4000-8000-000000000681',
  'IMPORTACAO',
  'CONCILIADO',
  10.00,
  100,
  now() - interval '2 hours',
  now() - interval '90 minutes',
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO saldo_credito_usuario (
  usuario_id,
  saldo_atual,
  atualizado_em,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000101',
  140,
  now(),
  1
)
ON CONFLICT (usuario_id) DO UPDATE SET
  saldo_atual = EXCLUDED.saldo_atual,
  atualizado_em = EXCLUDED.atualizado_em,
  versao = EXCLUDED.versao;

-- Bloco 24 - pagamentos sinteticos locais read-only.
INSERT INTO pagamento (
  id,
  usuario_id,
  plano_credito_id,
  provedor,
  metodo,
  txid,
  identificador_provedor,
  valor,
  moeda,
  quantidade_creditos,
  status_interno,
  status_provedor,
  expiracao_em,
  aprovado_em,
  cancelado_em,
  creditado_em,
  idempotency_key,
  criado_em,
  atualizado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000721',
    '00000000-0000-4000-8000-000000000102',
    NULL,
    'EFI',
    'PIX',
    'pix-efi-local-0721',
    'efi-local-0721',
    10.00,
    'BRL',
    100,
    'APROVADO',
    'CONCLUIDO_LOCAL_SINTETICO',
    NULL,
    now() - interval '25 minutes',
    NULL,
    now() - interval '20 minutes',
    'pagamento-sintetico-efi-com-credito',
    now() - interval '30 minutes',
    now() - interval '20 minutes'
  ),
  (
    '00000000-0000-4000-8000-000000000722',
    '00000000-0000-4000-8000-000000000102',
    NULL,
    'EFI',
    'PIX',
    'pix-efi-local-0722',
    'efi-local-0722',
    20.00,
    'BRL',
    200,
    'APROVADO',
    'CONCLUIDO_LOCAL_SINTETICO',
    NULL,
    now() - interval '22 minutes',
    NULL,
    NULL,
    'pagamento-sintetico-aprovado-sem-credito',
    now() - interval '24 minutes',
    now() - interval '22 minutes'
  ),
  (
    '00000000-0000-4000-8000-000000000723',
    '00000000-0000-4000-8000-000000000102',
    NULL,
    'MERCADO_PAGO_LEGADO',
    'LEGADO',
    NULL,
    'mp-legado-local-0723',
    0.00,
    'BRL',
    0,
    'LEGADO',
    'IMPORTADO_LEGADO_LOCAL',
    NULL,
    NULL,
    NULL,
    NULL,
    'pagamento-sintetico-mp-legado',
    now() - interval '21 minutes',
    now() - interval '21 minutes'
  ),
  (
    '00000000-0000-4000-8000-000000000724',
    '00000000-0000-4000-8000-000000000102',
    NULL,
    'EFI',
    'PIX',
    NULL,
    NULL,
    5.00,
    'BRL',
    50,
    'AGUARDANDO_PAGAMENTO',
    'PENDENTE_LOCAL_SINTETICO',
    now() + interval '2 hours',
    NULL,
    NULL,
    NULL,
    'pagamento-sintetico-efi-pendente',
    now() - interval '18 minutes',
    now() - interval '18 minutes'
  ),
  (
    '00000000-0000-4000-8000-000000000725',
    '00000000-0000-4000-8000-000000000102',
    NULL,
    'DESCONHECIDO',
    'DESCONHECIDO',
    NULL,
    NULL,
    0.00,
    'BRL',
    0,
    'AGUARDANDO_PAGAMENTO',
    'PENDENTE_LOCAL_SINTETICO',
    now() + interval '1 hour',
    NULL,
    NULL,
    NULL,
    'pagamento-sintetico-sem-provedor',
    now() - interval '16 minutes',
    now() - interval '16 minutes'
  ),
  (
    '00000000-0000-4000-8000-000000000726',
    '00000000-0000-4000-8000-000000000102',
    NULL,
    'EFI',
    'PIX',
    'pix-efi-local-0726',
    'efi-local-0726',
    3.00,
    'BRL',
    30,
    'APROVADO',
    'PENDENTE_LOCAL_SINTETICO',
    NULL,
    now() - interval '15 minutes',
    NULL,
    NULL,
    'pagamento-sintetico-status-inconsistente',
    now() - interval '15 minutes',
    now() - interval '15 minutes'
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO movimento_credito (
  id,
  usuario_id,
  tipo,
  direcao,
  quantidade,
  saldo_antes,
  saldo_depois,
  origem,
  referencia_tipo,
  referencia_id,
  idempotency_key,
  ator_usuario_id,
  observacao,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000731',
  '00000000-0000-4000-8000-000000000102',
  'ENTRADA',
  'CREDITO',
  100,
  0,
  100,
  'PAGAMENTO',
  'PAGAMENTO',
  '00000000-0000-4000-8000-000000000721',
  'pagamento-sintetico-ledger-efi',
  NULL,
  'Crédito vinculado a pagamento Efí para conferência.',
  now() - interval '19 minutes'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO pagamento_conciliacao (
  id,
  pagamento_id,
  movimento_credito_id,
  origem,
  status,
  valor_confirmado,
  creditos_confirmados,
  aprovado_em,
  creditado_em,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000741',
  '00000000-0000-4000-8000-000000000721',
  '00000000-0000-4000-8000-000000000731',
  'WEBHOOK',
  'CONCILIADO',
  10.00,
  100,
  now() - interval '25 minutes',
  now() - interval '20 minutes',
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO saldo_credito_usuario (
  usuario_id,
  saldo_atual,
  atualizado_em,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000102',
  100,
  now(),
  1
)
ON CONFLICT (usuario_id) DO UPDATE SET
  saldo_atual = EXCLUDED.saldo_atual,
  atualizado_em = EXCLUDED.atualizado_em,
  versao = EXCLUDED.versao;

INSERT INTO pagamento_evento (
  id,
  pagamento_id,
  provedor,
  provedor_evento_id,
  tipo_evento,
  payload_hash,
  status_provedor,
  recebido_em,
  processado_em,
  resultado
) VALUES
  (
    '00000000-0000-4000-8000-000000000751',
    '00000000-0000-4000-8000-000000000721',
    'EFI',
    'efi-evento-local-0751',
    'PIX_CONCLUIDO_LOCAL',
    'payload-hash-sintetico-efi-0751',
    'CONCLUIDO_LOCAL_SINTETICO',
    now() - interval '24 minutes',
    now() - interval '23 minutes',
    'EVENTO_SANITIZADO_LOCAL'
  ),
  (
    '00000000-0000-4000-8000-000000000752',
    '00000000-0000-4000-8000-000000000723',
    'MERCADO_PAGO_LEGADO',
    'mp-evento-local-0752',
    'PAGAMENTO_LEGADO_IMPORTADO_LOCAL',
    'payload-hash-sintetico-mp-0752',
    'IMPORTADO_LEGADO_LOCAL',
    now() - interval '20 minutes',
    now() - interval '20 minutes',
    'EVENTO_LEGADO_SANITIZADO_LOCAL'
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO pagamento_webhook (
  id,
  provedor,
  evento_id,
  txid,
  payload_hash,
  origem_ip_hash,
  validacao_resultado,
  recebido_em,
  processado_em,
  resultado,
  erro_resumido,
  tentativas
) VALUES
  (
    '00000000-0000-4000-8000-000000000761',
    'EFI',
    'efi-webhook-local-0761',
    'pix-efi-local-0721',
    'payload-hash-sintetico-webhook-duplicado',
    'ip-hash-sintetico-local',
    'VALIDO',
    now() - interval '24 minutes',
    now() - interval '23 minutes',
    'WEBHOOK_SANITIZADO_LOCAL',
    NULL,
    0
  ),
  (
    '00000000-0000-4000-8000-000000000762',
    'EFI',
    'efi-webhook-local-0762',
    'pix-efi-local-0721',
    'payload-hash-sintetico-webhook-duplicado',
    'ip-hash-sintetico-local',
    'VALIDO',
    now() - interval '23 minutes',
    now() - interval '22 minutes',
    'WEBHOOK_DUPLICADO_SANITIZADO_LOCAL',
    NULL,
    0
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio_localizacao (
  anuncio_id,
  estado_id,
  cidade_id,
  bairro_id,
  endereco_resumido,
  latitude,
  longitude,
  criado_em,
  atualizado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000504',
    '00000000-0000-4000-8000-000000000201',
    '00000000-0000-4000-8000-000000000301',
    '00000000-0000-4000-8000-000000000401',
    'Endereço de demonstração',
    NULL,
    NULL,
    now(),
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000505',
    '00000000-0000-4000-8000-000000000201',
    '00000000-0000-4000-8000-000000000301',
    '00000000-0000-4000-8000-000000000401',
    'Endereço de demonstração',
    NULL,
    NULL,
    now(),
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000506',
    '00000000-0000-4000-8000-000000000201',
    '00000000-0000-4000-8000-000000000301',
    '00000000-0000-4000-8000-000000000401',
    'Endereço de demonstração',
    NULL,
    NULL,
    now(),
    now()
  )
ON CONFLICT (anuncio_id) DO NOTHING;

INSERT INTO arquivo_midia (
  id,
  storage_provider,
  bucket,
  chave_objeto,
  nome_original,
  mime_type,
  tamanho_bytes,
  largura,
  altura,
  duracao_ms,
  sha256,
  etag,
  status_arquivo,
  criado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000706',
    'LOCAL_SINTETICO',
    'bucket-sintetico-local',
    'synthetic/midia-reprovar.bin',
    NULL,
    'image/jpeg',
    1,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    'PENDENTE',
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000708',
    'LOCAL_SINTETICO',
    'bucket-sintetico-local',
    'synthetic/midia-finalizada.bin',
    NULL,
    'image/jpeg',
    1,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    'VALIDADO',
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000710',
    'LOCAL_SINTETICO',
    'bucket-sintetico-local',
    'synthetic/midia-moderador.bin',
    NULL,
    'image/jpeg',
    1,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    'PENDENTE',
    now()
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio_midia (
  id,
  anuncio_id,
  arquivo_midia_id,
  tipo,
  finalidade,
  ordem,
  status,
  visibilidade_midia,
  criado_em,
  atualizado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000707',
    '00000000-0000-4000-8000-000000000503',
    '00000000-0000-4000-8000-000000000706',
    'FOTO',
    'GALERIA',
    1,
    'PENDENTE',
    NULL,
    now(),
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000709',
    '00000000-0000-4000-8000-000000000503',
    '00000000-0000-4000-8000-000000000708',
    'FOTO',
    'GALERIA',
    2,
    'PUBLICAVEL',
    'LIVRE',
    now(),
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000711',
    '00000000-0000-4000-8000-000000000506',
    '00000000-0000-4000-8000-000000000710',
    'FOTO',
    'CAPA',
    0,
    'PENDENTE',
    NULL,
    now(),
    now()
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO revisao_anuncio (
  id,
  anuncio_id,
  tipo,
  status,
  payload_solicitado,
  criado_por,
  criado_em,
  finalizado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000802',
    '00000000-0000-4000-8000-000000000504',
    'CRIACAO',
    'ABERTA',
    '{"origem":"sintetica-local-reprovacao"}'::jsonb,
    NULL,
    now(),
    NULL
  ),
  (
    '00000000-0000-4000-8000-000000000803',
    '00000000-0000-4000-8000-000000000505',
    'CRIACAO',
    'APROVADA',
    '{"origem":"sintetica-local-finalizada"}'::jsonb,
    NULL,
    now() - interval '10 minutes',
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000804',
    '00000000-0000-4000-8000-000000000506',
    'CRIACAO',
    'ABERTA',
    '{"origem":"sintetica-local-moderador"}'::jsonb,
    NULL,
    now(),
    NULL
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio (
  id,
  usuario_id,
  slug,
  titulo,
  descricao,
  status,
  status_moderacao,
  categoria,
  preco,
  whatsapp_normalizado,
  publicado_em,
  ultima_publicacao_em,
  criado_em,
  atualizado_em,
  removido_em,
  origem_importacao_id,
  versao
) VALUES
  (
    '00000000-0000-4000-8000-000000000507',
    '00000000-0000-4000-8000-000000000101',
    'anuncio-sintetico-ajuste-local',
    'Anúncio de demonstração para ajuste',
    'Perfil de demonstração para solicitação de ajuste.',
    'PENDENTE_REVISAO',
    'PENDENTE',
    'SINTETICO',
    NULL,
    NULL,
    NULL,
    NULL,
    now(),
    now(),
    NULL,
    NULL,
    0
  ),
  (
    '00000000-0000-4000-8000-000000000508',
    '00000000-0000-4000-8000-000000000101',
    'anuncio-sintetico-remeter-local',
    'Anúncio de demonstração para revisão',
    'Perfil de demonstração para remeter revisão.',
    'APROVADO',
    'APROVADO',
    'SINTETICO',
    NULL,
    NULL,
    NULL,
    NULL,
    now(),
    now(),
    NULL,
    NULL,
    0
  ),
  (
    '00000000-0000-4000-8000-000000000509',
    '00000000-0000-4000-8000-000000000101',
    'anuncio-sintetico-remeter-conflito-local',
    'Anúncio de demonstração com conflito de revisão',
    'Perfil de demonstração para conflito ao remeter revisão.',
    'PENDENTE_REVISAO',
    'PENDENTE',
    'SINTETICO',
    NULL,
    NULL,
    NULL,
    NULL,
    now(),
    now(),
    NULL,
    NULL,
    0
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio_localizacao (
  anuncio_id,
  estado_id,
  cidade_id,
  bairro_id,
  endereco_resumido,
  latitude,
  longitude,
  criado_em,
  atualizado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000507',
    '00000000-0000-4000-8000-000000000201',
    '00000000-0000-4000-8000-000000000301',
    '00000000-0000-4000-8000-000000000401',
    'Endereço de demonstração',
    NULL,
    NULL,
    now(),
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000508',
    '00000000-0000-4000-8000-000000000201',
    '00000000-0000-4000-8000-000000000301',
    '00000000-0000-4000-8000-000000000401',
    'Endereço de demonstração',
    NULL,
    NULL,
    now(),
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000509',
    '00000000-0000-4000-8000-000000000201',
    '00000000-0000-4000-8000-000000000301',
    '00000000-0000-4000-8000-000000000401',
    'Endereço de demonstração',
    NULL,
    NULL,
    now(),
    now()
  )
ON CONFLICT (anuncio_id) DO NOTHING;

INSERT INTO revisao_anuncio (
  id,
  anuncio_id,
  tipo,
  status,
  payload_solicitado,
  criado_por,
  criado_em,
  finalizado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000805',
    '00000000-0000-4000-8000-000000000507',
    'CRIACAO',
    'ABERTA',
    '{"origem":"sintetica-local-ajuste"}'::jsonb,
    NULL,
    now(),
    NULL
  ),
  (
    '00000000-0000-4000-8000-000000000806',
    '00000000-0000-4000-8000-000000000509',
    'EDICAO',
    'ABERTA',
    '{"origem":"sintetica-local-remeter-conflito"}'::jsonb,
    NULL,
    now(),
    NULL
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO story_anuncio (
  id,
  anuncio_midia_id,
  status,
  inicio_em,
  fim_em,
  ordem,
  criado_por,
  criado_em,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000703',
  '00000000-0000-4000-8000-000000000702',
  'PUBLICADO',
  now(),
  NULL,
  0,
  NULL,
  now(),
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO documento_busca_anuncio (
  anuncio_id,
  texto_busca,
  estado_id,
  cidade_id,
  bairro_id,
  categoria,
  preco,
  status_publicacao,
  tem_midia_valida,
  beneficios_ranking_json,
  ranking_base,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000501',
  'anuncio demonstracao cidade demonstracao bairro demonstracao',
  '00000000-0000-4000-8000-000000000201',
  '00000000-0000-4000-8000-000000000301',
  '00000000-0000-4000-8000-000000000401',
  'SINTETICO',
  NULL,
  'PUBLICAVEL',
  false,
  '{}'::jsonb,
  1,
  now()
) ON CONFLICT (anuncio_id) DO NOTHING;

INSERT INTO seo_url (
  id,
  caminho_publico,
  canonical_path,
  tipo,
  entidade_tipo,
  entidade_id,
  status_esperado,
  indexavel,
  incluir_sitemap,
  qualidade_status,
  ultima_validacao_em,
  motivo_noindex,
  criado_em,
  atualizado_em,
  versao
) VALUES
  (
    '00000000-0000-4000-8000-000000000601',
    '/anuncios/anuncio-sintetico-local',
    '/anuncios/anuncio-sintetico-local',
    'ANUNCIO',
    'ANUNCIO',
    '00000000-0000-4000-8000-000000000501',
    'NOINDEX',
    false,
    false,
    'APROVADO',
    now(),
    'Validação controlada',
    now(),
    now(),
    0
  ),
  (
    '00000000-0000-4000-8000-000000000606',
    '/anuncios/anuncio-sintetico-midia-restrita-local',
    '/anuncios/anuncio-sintetico-midia-restrita-local',
    'ANUNCIO',
    'ANUNCIO',
    '00000000-0000-4000-8000-000000000502',
    'NOINDEX',
    false,
    false,
    'APROVADO',
    now(),
    'Validação controlada',
    now(),
    now(),
    0
  ),
  (
    '00000000-0000-4000-8000-000000000602',
    '/acompanhantes/zz/cidade-sintetica',
    '/acompanhantes/zz/cidade-sintetica',
    'CIDADE',
    'CIDADE',
    '00000000-0000-4000-8000-000000000301',
    'NOINDEX',
    false,
    false,
    'APROVADO',
    now(),
    'Validação controlada',
    now(),
    now(),
    0
  ),
  (
    '00000000-0000-4000-8000-000000000603',
    '/acompanhantes/zz/cidade-sintetica/bairro-sintetico',
    '/acompanhantes/zz/cidade-sintetica/bairro-sintetico',
    'BAIRRO',
    'BAIRRO',
    '00000000-0000-4000-8000-000000000401',
    'NOINDEX',
    false,
    false,
    'APROVADO',
    now(),
    'Validação controlada',
    now(),
    now(),
    0
  ),
  (
    '00000000-0000-4000-8000-000000000604',
    '/sitemap.xml',
    '/sitemap.xml',
    'SITEMAP',
    NULL,
    NULL,
    'NOINDEX',
    false,
    false,
    'APROVADO',
    now(),
    'Validação controlada',
    now(),
    now(),
    0
  ),
  (
    '00000000-0000-4000-8000-000000000605',
    '/robots.txt',
    '/robots.txt',
    'ROBOTS',
    NULL,
    NULL,
    'NOINDEX',
    false,
    false,
    'APROVADO',
    now(),
    'Validação controlada',
    now(),
    now(),
    0
  )
ON CONFLICT (id) DO NOTHING;

-- Bloco 22 - Premium/beneficios sinteticos locais.
-- Nao representa compra real, cobranca, credito real, Pix/Efi, checkout ou ajuste financeiro.

INSERT INTO anuncio (
  id,
  usuario_id,
  slug,
  titulo,
  descricao,
  status,
  status_moderacao,
  categoria,
  preco,
  whatsapp_normalizado,
  publicado_em,
  ultima_publicacao_em,
  criado_em,
  atualizado_em,
  removido_em,
  origem_importacao_id,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000510',
  '00000000-0000-4000-8000-000000000101',
  'anuncio-sintetico-gratuito-local',
  'Anúncio de demonstração gratuito',
  'Perfil de demonstração para validar plano gratuito sem limite comercial de contato.',
  'PUBLICADO',
  'APROVADO',
  'SINTETICO',
  NULL,
  '+5500000000000',
  now() - interval '2 days',
  now() - interval '2 days',
  now() - interval '2 days',
  now() - interval '2 days',
  NULL,
  NULL,
  0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO anuncio_localizacao (
  anuncio_id,
  estado_id,
  cidade_id,
  bairro_id,
  endereco_resumido,
  latitude,
  longitude,
  criado_em,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000510',
  '00000000-0000-4000-8000-000000000201',
  '00000000-0000-4000-8000-000000000301',
  '00000000-0000-4000-8000-000000000401',
  'Endereço de demonstração',
  NULL,
  NULL,
  now(),
  now()
) ON CONFLICT (anuncio_id) DO NOTHING;

INSERT INTO documento_busca_anuncio (
  anuncio_id,
  texto_busca,
  estado_id,
  cidade_id,
  bairro_id,
  categoria,
  preco,
  status_publicacao,
  tem_midia_valida,
  beneficios_ranking_json,
  ranking_base,
  atualizado_em
) VALUES (
  '00000000-0000-4000-8000-000000000510',
  'anuncio demonstracao gratuito cidade demonstracao bairro demonstracao',
  '00000000-0000-4000-8000-000000000201',
  '00000000-0000-4000-8000-000000000301',
  '00000000-0000-4000-8000-000000000401',
  'SINTETICO',
  NULL,
  'PUBLICAVEL',
  false,
  '{}'::jsonb,
  1,
  now()
) ON CONFLICT (anuncio_id) DO NOTHING;

INSERT INTO beneficio_premium (
  id,
  codigo,
  nome,
  descricao,
  escopo,
  afeta_ranking,
  ativo,
  ordem_exibicao,
  criado_em,
  atualizado_em
) VALUES
  ('00000000-0000-4000-8000-000000000611', 'DESTAQUE', 'Destaque de demonstração', 'Benefício de demonstração para exposição adicional.', 'ANUNCIO', true, true, 0, now(), now()),
  ('00000000-0000-4000-8000-000000000613', 'FOTOS_EXTRA', 'Fotos extras de demonstração', 'Benefício de demonstração para mídia adicional.', 'MIDIA', false, true, 0, now(), now()),
  ('00000000-0000-4000-8000-000000000614', 'STORIES', 'Stories de demonstração', 'Benefício de demonstração para stories.', 'MIDIA', false, true, 0, now(), now()),
  ('00000000-0000-4000-8000-000000000615', 'VIDEO', 'Vídeo de demonstração', 'Benefício de demonstração para vídeo.', 'MIDIA', false, true, 0, now(), now()),
  ('00000000-0000-4000-8000-000000000616', 'RELATORIO', 'Relatório de demonstração', 'Benefício de demonstração para relatório operacional.', 'RELATORIO', false, true, 0, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO grupo_ativacao_beneficio (
  id,
  tipo,
  origem,
  usuario_id,
  anuncio_id,
  ator_usuario_id,
  campanha_codigo,
  validade_inicio_em,
  validade_fim_em,
  status,
  idempotency_key,
  observacao,
  criado_em,
  atualizado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000651',
    'PACOTE',
    'CORTESIA',
    '00000000-0000-4000-8000-000000000101',
    '00000000-0000-4000-8000-000000000501',
    NULL,
    'SINTETICO_PREMIUM_ATIVO',
    now() - interval '1 day',
    now() + interval '5 days',
    'ATIVO',
    'premium-sintetico-grupo-ativo',
    'Grupo de benefícios ativo.',
    now(),
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000652',
    'PACOTE',
    'CORTESIA',
    '00000000-0000-4000-8000-000000000101',
    '00000000-0000-4000-8000-000000000503',
    NULL,
    'SINTETICO_GRUPO_EXPIRADO',
    now() - interval '20 days',
    now() - interval '5 days',
    'EXPIRADO',
    'premium-sintetico-grupo-expirado',
    'Grupo de benefícios expirado para conferência.',
    now(),
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000653',
    'CAMPANHA',
    'CORTESIA',
    '00000000-0000-4000-8000-000000000101',
    '00000000-0000-4000-8000-000000000504',
    NULL,
    'SINTETICO_BENEFICIO_CURTO',
    now() - interval '10 days',
    now() + interval '10 days',
    'ATIVO',
    'premium-sintetico-beneficio-curto',
    'Grupo ativo com benefício expirado antes do grupo.',
    now(),
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000654',
    'ADMIN',
    'CORTESIA',
    '00000000-0000-4000-8000-000000000101',
    '00000000-0000-4000-8000-000000000510',
    NULL,
    'SINTETICO_GRUPO_SEM_BENEFICIO',
    now() - interval '1 day',
    now() + interval '20 days',
    'ATIVO',
    'premium-sintetico-grupo-sem-beneficio',
    'Grupo sem ativação para conferência.',
    now(),
    now()
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO ativacao_beneficio (
  id,
  beneficio_id,
  opcao_id,
  usuario_id,
  anuncio_id,
  grupo_ativacao_id,
  origem,
  ator_usuario_id,
  campanha_codigo,
  inicio_em,
  fim_em,
  status,
  custo_creditos_snapshot,
  preco_snapshot,
  idempotency_key,
  revogada_em,
  motivo_revogacao,
  criado_em
) VALUES
  (
    '00000000-0000-4000-8000-000000000661',
    '00000000-0000-4000-8000-000000000611',
    NULL,
    '00000000-0000-4000-8000-000000000101',
    '00000000-0000-4000-8000-000000000501',
    '00000000-0000-4000-8000-000000000651',
    'CORTESIA',
    NULL,
    'SINTETICO_PREMIUM_ATIVO',
    now() - interval '1 day',
    now() + interval '5 days',
    'ATIVA',
    0,
    NULL,
    'premium-sintetico-destaque-ativo',
    NULL,
    NULL,
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000662',
    '00000000-0000-4000-8000-000000000614',
    NULL,
    '00000000-0000-4000-8000-000000000101',
    '00000000-0000-4000-8000-000000000501',
    '00000000-0000-4000-8000-000000000651',
    'CORTESIA',
    NULL,
    'SINTETICO_PREMIUM_ATIVO',
    now() - interval '1 day',
    now() + interval '5 days',
    'ATIVA',
    0,
    NULL,
    'premium-sintetico-stories-ativo',
    NULL,
    NULL,
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000663',
    '00000000-0000-4000-8000-000000000613',
    NULL,
    '00000000-0000-4000-8000-000000000101',
    '00000000-0000-4000-8000-000000000501',
    NULL,
    'CORTESIA',
    NULL,
    'SINTETICO_VENCENDO',
    now() - interval '1 day',
    now() + interval '2 days',
    'ATIVA',
    0,
    NULL,
    'premium-sintetico-fotos-vencendo',
    NULL,
    NULL,
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000664',
    'f3000000-0000-4000-8000-000000000003',
    NULL,
    '00000000-0000-4000-8000-000000000101',
    '00000000-0000-4000-8000-000000000503',
    '00000000-0000-4000-8000-000000000652',
    'CORTESIA',
    NULL,
    'SINTETICO_GRUPO_EXPIRADO',
    now() - interval '20 days',
    now() + interval '5 days',
    'ATIVA',
    0,
    NULL,
    'premium-sintetico-topo-inconsistente',
    NULL,
    NULL,
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000665',
    '00000000-0000-4000-8000-000000000615',
    NULL,
    '00000000-0000-4000-8000-000000000101',
    '00000000-0000-4000-8000-000000000504',
    '00000000-0000-4000-8000-000000000653',
    'CORTESIA',
    NULL,
    'SINTETICO_BENEFICIO_CURTO',
    now() - interval '10 days',
    now() - interval '1 day',
    'EXPIRADA',
    0,
    NULL,
    'premium-sintetico-video-expirado-antes-grupo',
    NULL,
    NULL,
    now()
  ),
  (
    '00000000-0000-4000-8000-000000000666',
    '00000000-0000-4000-8000-000000000616',
    NULL,
    '00000000-0000-4000-8000-000000000101',
    '00000000-0000-4000-8000-000000000503',
    NULL,
    'CORTESIA',
    NULL,
    'SINTETICO_RELATORIO_VENCENDO',
    now() - interval '1 day',
    now() + interval '3 days',
    'ATIVA',
    0,
    NULL,
    'premium-sintetico-relatorio-vencendo',
    NULL,
    NULL,
    now()
  )
ON CONFLICT (id) DO NOTHING;
