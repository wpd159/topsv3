-- Tops do Job V3 - Bloco 12
-- Dados administrativos sinteticos locais e descartaveis.
-- Nao usar em producao. Nao contem credencial real, usuario real, CPF, telefone, documento ou e-mail real.
-- O valor bruto da credencial local e montado apenas em runtime pelos smokes/E2E; somente hash BCrypt sintetico e versionado.

INSERT INTO usuario (
  id,
  nome,
  email_normalizado,
  telefone_normalizado,
  status,
  tipo_conta,
  email_verificado_em,
  telefone_verificado_em,
  criado_em,
  atualizado_em,
  desativado_em,
  versao
) VALUES (
  '00000000-0000-4000-8000-000000000901',
  'Admin de demonstração',
  'admin.local@example.invalid',
  NULL,
  'ATIVO',
  'STAFF',
  now(),
  NULL,
  now(),
  now(),
  NULL,
  0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO credencial_usuario (
  id,
  usuario_id,
  senha_hash,
  algoritmo,
  alterada_em,
  precisa_redefinir,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000902',
  '00000000-0000-4000-8000-000000000901',
  '$2a$10$Tqle5d1.djgr6CDy9roIAu5lOXctgjD7EmrfiNVqeNmQ5cSEHJn/O',
  'BCRYPT',
  now(),
  false,
  now()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO papel_usuario (
  usuario_id,
  papel,
  criado_por,
  criado_em
) VALUES (
  '00000000-0000-4000-8000-000000000901',
  'ADMIN',
  NULL,
  now()
) ON CONFLICT (usuario_id, papel) DO NOTHING;

INSERT INTO usuario (
  id,
  nome,
  email_normalizado,
  telefone_normalizado,
  status,
  tipo_conta,
  email_verificado_em,
  telefone_verificado_em,
  criado_em,
  atualizado_em,
  desativado_em,
  versao
) VALUES
  ('00000000-0000-4000-8000-000000000903', 'Moderador de demonstração', 'moderador.local@example.invalid', NULL, 'ATIVO', 'STAFF', now(), NULL, now(), now(), NULL, 0),
  ('00000000-0000-4000-8000-000000000904', 'Comercial de demonstração', 'comercial.local@example.invalid', NULL, 'ATIVO', 'STAFF', now(), NULL, now(), now(), NULL, 0),
  ('00000000-0000-4000-8000-000000000905', 'Usuário de demonstração', 'usuario.local@example.invalid', NULL, 'ATIVO', 'ANUNCIANTE', now(), NULL, now(), now(), NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO credencial_usuario (
  id,
  usuario_id,
  senha_hash,
  algoritmo,
  alterada_em,
  precisa_redefinir,
  criado_em
) VALUES
  ('00000000-0000-4000-8000-000000000906', '00000000-0000-4000-8000-000000000903', '$2a$10$Tqle5d1.djgr6CDy9roIAu5lOXctgjD7EmrfiNVqeNmQ5cSEHJn/O', 'BCRYPT', now(), false, now()),
  ('00000000-0000-4000-8000-000000000907', '00000000-0000-4000-8000-000000000904', '$2a$10$Tqle5d1.djgr6CDy9roIAu5lOXctgjD7EmrfiNVqeNmQ5cSEHJn/O', 'BCRYPT', now(), false, now()),
  ('00000000-0000-4000-8000-000000000908', '00000000-0000-4000-8000-000000000905', '$2a$10$Tqle5d1.djgr6CDy9roIAu5lOXctgjD7EmrfiNVqeNmQ5cSEHJn/O', 'BCRYPT', now(), false, now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO papel_usuario (
  usuario_id,
  papel,
  criado_por,
  criado_em
) VALUES
  ('00000000-0000-4000-8000-000000000903', 'MODERADOR', NULL, now()),
  ('00000000-0000-4000-8000-000000000904', 'COMERCIAL', NULL, now()),
  ('00000000-0000-4000-8000-000000000905', 'USUARIO', NULL, now())
ON CONFLICT (usuario_id, papel) DO NOTHING;

INSERT INTO permissao (
  id,
  codigo,
  descricao,
  criado_em
) VALUES
  ('00000000-0000-4000-8000-000000000921', 'ADMIN_CONFIGURAR', 'Configurar administração futura.', now()),
  ('00000000-0000-4000-8000-000000000922', 'SEGURANCA_GERENCIAR', 'Gerenciar seguranca administrativa futura.', now()),
  ('00000000-0000-4000-8000-000000000923', 'ANUNCIO_LER', 'Ler metadados administrativos futuros de anuncios.', now()),
  ('00000000-0000-4000-8000-000000000924', 'ANUNCIO_MODERAR', 'Autorizar moderação mínima de anúncios.', now()),
  ('00000000-0000-4000-8000-000000000925', 'MIDIA_REVISAR', 'Autorizar revisão mínima de mídia.', now()),
  ('00000000-0000-4000-8000-000000000926', 'DOCUMENTO_REVISAR', 'Preparar autorizacao futura de revisao de documentos.', now()),
  ('00000000-0000-4000-8000-000000000927', 'COMERCIAL_GERENCIAR', 'Preparar autorizacao futura comercial.', now()),
  ('00000000-0000-4000-8000-000000000928', 'SUPORTE_ATENDER', 'Preparar autorizacao futura de suporte.', now()),
  ('00000000-0000-4000-8000-000000000929', 'AUDITORIA_LER', 'Ler auditoria administrativa futura.', now()),
  ('00000000-0000-4000-8000-000000000930', 'FINANCEIRO_LER', 'Ler financeiro administrativo futuro sem acao critica.', now())
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO papel_permissao (
  papel,
  permissao_id,
  criado_em
) SELECT
  vinculo.papel,
  permissao.id,
  now()
FROM (
  VALUES
    ('ADMIN', 'ADMIN_CONFIGURAR'),
    ('ADMIN', 'SEGURANCA_GERENCIAR'),
    ('ADMIN', 'ANUNCIO_LER'),
    ('ADMIN', 'ANUNCIO_MODERAR'),
    ('ADMIN', 'MIDIA_REVISAR'),
    ('ADMIN', 'DOCUMENTO_REVISAR'),
    ('ADMIN', 'COMERCIAL_GERENCIAR'),
    ('ADMIN', 'SUPORTE_ATENDER'),
    ('ADMIN', 'AUDITORIA_LER'),
    ('ADMIN', 'FINANCEIRO_LER'),
    ('MODERADOR', 'ANUNCIO_LER'),
    ('MODERADOR', 'ANUNCIO_MODERAR'),
    ('MODERADOR', 'MIDIA_REVISAR'),
    ('MODERADOR', 'DOCUMENTO_REVISAR'),
    ('MODERADOR', 'SUPORTE_ATENDER'),
    ('MODERADOR', 'AUDITORIA_LER'),
    ('COMERCIAL', 'ANUNCIO_LER'),
    ('COMERCIAL', 'COMERCIAL_GERENCIAR'),
    ('COMERCIAL', 'SUPORTE_ATENDER'),
    ('COMERCIAL', 'FINANCEIRO_LER'),
    ('USUARIO', 'ANUNCIO_LER')
) AS vinculo(papel, codigo)
JOIN permissao ON permissao.codigo = vinculo.codigo
ON CONFLICT (papel, permissao_id) DO NOTHING;
