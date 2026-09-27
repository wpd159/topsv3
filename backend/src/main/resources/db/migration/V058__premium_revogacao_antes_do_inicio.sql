-- Preserve the absence of effective service dates when a pending right is revoked.
-- No historical rows or dates are rewritten; the V046 branches remain unchanged.
ALTER TABLE ativacao_beneficio
  DROP CONSTRAINT ativacao_beneficio_janela_chk;

ALTER TABLE ativacao_beneficio
  ADD CONSTRAINT ativacao_beneficio_janela_chk CHECK (
    (status = 'AGUARDANDO_MODERACAO' AND inicio_em IS NULL AND fim_em IS NULL)
    OR
    (status <> 'AGUARDANDO_MODERACAO' AND inicio_em IS NOT NULL AND fim_em IS NOT NULL AND fim_em > inicio_em)
    OR
    (status = 'REVOGADA' AND inicio_em IS NULL AND fim_em IS NULL
      AND revogada_em IS NOT NULL
      AND NULLIF(BTRIM(motivo_revogacao), '') IS NOT NULL)
  );
