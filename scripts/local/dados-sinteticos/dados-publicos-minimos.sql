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
  '00000000-0000-4000-8000-000000000510',
  '00000000-0000-4000-8000-000000000101',
  'anuncio-sintetico-gratuito-local',
  'Anuncio sintetico gratuito local',
  'Registro sintetico neutro para validar plano gratuito sem limite comercial de contato.',
  'PUBLICADO',
  'APROVADO',
  'SINTETICO',
  'LIVRE',
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
  'Endereco sintetico local',
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
  'anuncio sintetico gratuito local cidade sintetica bairro sintetico',
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
  criado_em
) VALUES
  ('00000000-0000-4000-8000-000000000611', 'DESTAQUE', 'Destaque sintetico', 'Beneficio sintetico de exposicao adicional.', 'ANUNCIO', true, true, now()),
  ('00000000-0000-4000-8000-000000000612', 'ANUNCIO_TOPO', 'Anuncio topo sintetico', 'Beneficio sintetico de topo preservado.', 'ANUNCIO', true, true, now()),
  ('00000000-0000-4000-8000-000000000613', 'FOTOS_EXTRA', 'Fotos extra sinteticas', 'Beneficio sintetico de midia adicional.', 'MIDIA', false, true, now()),
  ('00000000-0000-4000-8000-000000000614', 'STORIES', 'Stories sinteticos', 'Beneficio sintetico para stories.', 'MIDIA', false, true, now()),
  ('00000000-0000-4000-8000-000000000615', 'VIDEO', 'Video sintetico', 'Beneficio sintetico para video.', 'MIDIA', false, true, now()),
  ('00000000-0000-4000-8000-000000000616', 'RELATORIO', 'Relatorio sintetico', 'Beneficio sintetico de relatorio operacional.', 'RELATORIO', false, true, now())
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
    'Grupo sintetico local ativo sem compra real.',
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
    'Grupo sintetico expirado para validar consistencia.',
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
    'Grupo sintetico ativo com beneficio expirado antes do grupo.',
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
    'Grupo sintetico sem ativacao para validar relatorio.',
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
    '00000000-0000-4000-8000-000000000612',
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
