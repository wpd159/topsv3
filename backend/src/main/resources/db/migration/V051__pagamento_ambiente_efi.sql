-- Persiste a origem ambiental das novas cobrancas Efi sem inferir historico.

ALTER TABLE pagamento
  ADD COLUMN ambiente text;

ALTER TABLE pagamento
  ADD CONSTRAINT pagamento_ambiente_chk
  CHECK (ambiente IS NULL OR ambiente IN ('SANDBOX', 'PRODUCAO'));
