-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Migration estrutural para auditoria local. Nao aplicar sem revisao.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

COMMENT ON EXTENSION pg_trgm IS 'Suporte planejado para busca textual aproximada da V3; uso final depende de auditoria Pro.';
COMMENT ON EXTENSION unaccent IS 'Suporte planejado para normalizacao textual de busca; uso final depende de auditoria Pro.';
