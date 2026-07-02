-- Tops do Job V3 - Bloco 7
-- Dados sinteticos locais, neutros e descartaveis.
-- Nao usar em producao. Nao contem nome real, telefone, e-mail, CPF, documento, foto, URL real, pagamento ou storage real.

INSERT INTO usuario (
  id,
  nome,
  email_normalizado,
  telefone_normalizado,
  status,
  tipo_conta,
  criado_em,
  atualizado_em,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000101',
  'Usuario Sintetico Local',
  NULL,
  NULL,
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
  'Estado Sintetico',
  'estado sintetico',
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
  'Cidade Sintetica',
  'cidade sintetica',
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
  'Bairro Sintetico',
  'bairro sintetico',
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
  classificacao_conteudo,
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
  'Anuncio sintetico local',
  'Registro sintetico neutro para smoke test local descartavel.',
  'PUBLICADO',
  'APROVADO',
  'SINTETICO',
  'LIVRE',
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
  classificacao_conteudo,
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
  'Anuncio sintetico pendente local',
  'Registro sintetico neutro para validar filas administrativas locais.',
  'PENDENTE_REVISAO',
  'PENDENTE',
  'SINTETICO',
  'LIVRE',
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
  classificacao_conteudo,
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
  'anuncio-sintetico-bloqueado-local',
  'Anuncio sintetico bloqueado local',
  'Registro sintetico neutro para validar confirmacao de idade local.',
  'PUBLICADO',
  'APROVADO',
  'SINTETICO',
  'BLOQUEADO',
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
  'Endereco sintetico local',
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
  'Endereco sintetico local',
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
  'Endereco sintetico local',
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
  classificacao_conteudo,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000701',
  'LOCAL_SINTETICO',
  'bucket-sintetico-local',
  'synthetic/story-bloqueado.bin',
  NULL,
  'video/mp4',
  1,
  NULL,
  NULL,
  1000,
  NULL,
  NULL,
  'VALIDADO',
  'BLOQUEADO',
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
  classificacao_conteudo,
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
  'LIVRE',
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
  classificacao_conteudo,
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
  'BLOQUEADO',
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
  classificacao_conteudo,
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
  'LIVRE',
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
  classificacao_conteudo,
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
    'Anuncio sintetico reprovar local',
    'Registro sintetico neutro para decisao local de reprovacao.',
    'PENDENTE_REVISAO',
    'PENDENTE',
    'SINTETICO',
    'LIVRE',
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
    'Anuncio sintetico finalizado local',
    'Registro sintetico neutro para conflito de revisao finalizada.',
    'APROVADO',
    'APROVADO',
    'SINTETICO',
    'LIVRE',
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
    'Anuncio sintetico moderador local',
    'Registro sintetico neutro para decisao local por moderador.',
    'PENDENTE_REVISAO',
    'PENDENTE',
    'SINTETICO',
    'LIVRE',
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
    '00000000-0000-4000-8000-000000000504',
    '00000000-0000-4000-8000-000000000201',
    '00000000-0000-4000-8000-000000000301',
    '00000000-0000-4000-8000-000000000401',
    'Endereco sintetico local',
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
    'Endereco sintetico local',
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
    'Endereco sintetico local',
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
  classificacao_conteudo,
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
    'LIVRE',
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
    'LIVRE',
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
    'LIVRE',
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
  classificacao_conteudo,
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
    'LIVRE',
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
    'LIVRE',
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
  classificacao_conteudo,
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
    'Anuncio sintetico ajuste local',
    'Registro sintetico neutro para solicitacao local de ajuste.',
    'PENDENTE_REVISAO',
    'PENDENTE',
    'SINTETICO',
    'LIVRE',
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
    'Anuncio sintetico remeter local',
    'Registro sintetico neutro para remeter revisao local.',
    'APROVADO',
    'APROVADO',
    'SINTETICO',
    'LIVRE',
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
    'Anuncio sintetico remeter conflito local',
    'Registro sintetico neutro para conflito de remeter revisao local.',
    'PENDENTE_REVISAO',
    'PENDENTE',
    'SINTETICO',
    'LIVRE',
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
    'Endereco sintetico local',
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
    'Endereco sintetico local',
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
    'Endereco sintetico local',
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
  'anuncio sintetico local cidade sintetica bairro sintetico',
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
    'Smoke test local descartavel',
    now(),
    now(),
    0
  ),
  (
    '00000000-0000-4000-8000-000000000606',
    '/anuncios/anuncio-sintetico-bloqueado-local',
    '/anuncios/anuncio-sintetico-bloqueado-local',
    'ANUNCIO',
    'ANUNCIO',
    '00000000-0000-4000-8000-000000000502',
    'NOINDEX',
    false,
    false,
    'APROVADO',
    now(),
    'Smoke test local descartavel',
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
    'Smoke test local descartavel',
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
    'Smoke test local descartavel',
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
    'Smoke test local descartavel',
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
    'Smoke test local descartavel',
    now(),
    now(),
    0
  )
ON CONFLICT (id) DO NOTHING;
