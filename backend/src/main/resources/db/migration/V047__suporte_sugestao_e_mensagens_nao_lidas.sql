-- Amplia o fluxo canonico de suporte sem migrar ou duplicar manifestacoes historicas.

ALTER TABLE ticket_suporte
  DROP CONSTRAINT IF EXISTS ticket_suporte_categoria_chk;

ALTER TABLE ticket_suporte
  ADD CONSTRAINT ticket_suporte_categoria_chk
  CHECK (categoria IN (
    'ERRO_NO_SISTEMA',
    'PROBLEMAS_COM_PAGAMENTO',
    'ACESSO_CONTA',
    'SUGESTAO',
    'OUTROS'
  ));

ALTER TABLE mensagem_suporte
  ADD COLUMN IF NOT EXISTS nao_lida_usuario boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS mensagem_suporte_nao_lida_usuario_idx
  ON mensagem_suporte (ticket_id, criado_em)
  WHERE nao_lida_usuario = true
    AND origem = 'STAFF'
    AND privado_staff = false;
