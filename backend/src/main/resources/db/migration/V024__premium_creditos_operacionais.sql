-- Torna operacional o catalogo Premium e o ledger de creditos, sem pagamento externo.

ALTER TABLE beneficio_premium
  ADD COLUMN ordem_exibicao integer NOT NULL DEFAULT 0,
  ADD COLUMN atualizado_em timestamptz;

UPDATE beneficio_premium
SET atualizado_em = criado_em
WHERE atualizado_em IS NULL;

ALTER TABLE beneficio_premium
  ALTER COLUMN atualizado_em SET NOT NULL,
  ADD CONSTRAINT beneficio_premium_ordem_chk CHECK (ordem_exibicao >= 0);

ALTER TABLE beneficio_premium_opcao
  ADD COLUMN ordem_exibicao integer NOT NULL DEFAULT 0,
  ADD COLUMN atualizado_em timestamptz;

UPDATE beneficio_premium_opcao
SET atualizado_em = criado_em
WHERE atualizado_em IS NULL;

ALTER TABLE beneficio_premium_opcao
  ALTER COLUMN atualizado_em SET NOT NULL,
  ADD CONSTRAINT beneficio_premium_opcao_ordem_chk CHECK (ordem_exibicao >= 0);

CREATE UNIQUE INDEX beneficio_premium_opcao_versao_uk
  ON beneficio_premium_opcao (beneficio_id, duracao_dias, versao_regra);

ALTER TABLE plano_credito
  ADD COLUMN descricao text NOT NULL DEFAULT '',
  ADD COLUMN ordem_exibicao integer NOT NULL DEFAULT 0,
  ADD CONSTRAINT plano_credito_ordem_chk CHECK (ordem_exibicao >= 0);

ALTER TABLE movimento_credito
  ADD COLUMN request_id text;

COMMENT ON COLUMN movimento_credito.request_id IS
  'Identificador sanitizado da requisicao que originou o lancamento.';

INSERT INTO permissao (id, codigo, descricao, criado_em)
VALUES
  ('f4000000-0000-4000-8000-000000000003', 'FINANCEIRO_LER', 'Consultar ledger, saldos e ativacoes.', CURRENT_TIMESTAMP),
  ('f4000000-0000-4000-8000-000000000004', 'FINANCEIRO_GERENCIAR', 'Ajustar creditos e administrar pacotes.', CURRENT_TIMESTAMP),
  ('f4000000-0000-4000-8000-000000000005', 'PREMIUM_GERENCIAR', 'Administrar catalogo e ativacoes Premium.', CURRENT_TIMESTAMP)
ON CONFLICT (codigo) DO UPDATE
SET descricao = EXCLUDED.descricao;

INSERT INTO papel_permissao (papel, permissao_id, criado_em)
SELECT 'ADMIN', permissao.id, CURRENT_TIMESTAMP
FROM permissao
WHERE permissao.codigo IN ('FINANCEIRO_LER', 'FINANCEIRO_GERENCIAR', 'PREMIUM_GERENCIAR')
ON CONFLICT (papel, permissao_id) DO NOTHING;

INSERT INTO beneficio_premium (
  id, codigo, nome, descricao, escopo, afeta_ranking, ativo,
  ordem_exibicao, criado_em, atualizado_em
)
VALUES
  ('f3000000-0000-4000-8000-000000000001', 'OCULTAR_IDADE', 'Ocultar idade', 'Oculta a idade publica durante a vigencia.', 'ANUNCIO', false, true, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('f3000000-0000-4000-8000-000000000002', 'FOTOS_EXTRA_5', 'Ate 10 fotos', 'Amplia o limite do anuncio para ate dez fotos.', 'ANUNCIO', false, true, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('f3000000-0000-4000-8000-000000000003', 'ANUNCIO_TOPO', 'Anuncio no topo', 'Prioriza o anuncio nas listagens durante a vigencia.', 'ANUNCIO', true, true, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('f3000000-0000-4000-8000-000000000004', 'WHATSAPP_CARD', 'WhatsApp no card', 'Destaca o contato no card publico do anuncio.', 'ANUNCIO', false, true, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('f3000000-0000-4000-8000-000000000005', 'CARROSSEL_FOTOS', 'Carrossel de fotos', 'Habilita a navegacao em carrossel nas fotos publicas.', 'ANUNCIO', false, true, 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('f3000000-0000-4000-8000-000000000006', 'VIDEO_1', 'Video no anuncio', 'Habilita um video aprovado no anuncio.', 'ANUNCIO', false, true, 70, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO beneficio_premium_opcao (
  id, beneficio_id, duracao_dias, custo_creditos, preco_referencia,
  versao_regra, ativo, vigencia_inicio_em, vigencia_fim_em,
  ordem_exibicao, criado_em, atualizado_em
)
SELECT
  md5(beneficio.codigo || ':' || duracao.dias)::uuid,
  beneficio.id,
  duracao.dias,
  duracao.custo,
  NULL,
  1,
  true,
  NULL,
  NULL,
  duracao.ordem,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM beneficio_premium beneficio
CROSS JOIN (
  VALUES (1, 2, 1), (7, 10, 2), (14, 18, 3), (30, 35, 4)
) AS duracao(dias, custo, ordem)
WHERE beneficio.codigo IN (
  'ANUNCIO_TOPO',
  'FOTOS_EXTRA_5',
  'OCULTAR_IDADE',
  'WHATSAPP_CARD',
  'CARROSSEL_FOTOS',
  'VIDEO_1'
)
ON CONFLICT (beneficio_id, duracao_dias, versao_regra) DO NOTHING;

INSERT INTO plano_credito (
  id, codigo, nome, quantidade_creditos, valor, moeda, ativo,
  descricao, ordem_exibicao, criado_em, atualizado_em
)
VALUES
  ('f5000000-0000-4000-8000-000000000001', 'PACOTE_50', '50 creditos', 50, 0, 'BRL', false, 'Pacote configuravel para futura cobranca.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('f5000000-0000-4000-8000-000000000002', 'PACOTE_100', '100 creditos', 100, 0, 'BRL', false, 'Pacote configuravel para futura cobranca.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('f5000000-0000-4000-8000-000000000003', 'PACOTE_250', '250 creditos', 250, 0, 'BRL', false, 'Pacote configuravel para futura cobranca.', 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (codigo) DO NOTHING;
