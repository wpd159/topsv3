# Relatorio de riscos residuais do Bloco 43

## Riscos residuais

- `gitleaks` real permanece ausente no PATH.
- Fallback local nao substitui gitleaks real para producao sem decisao formal.
- Gate de homologacao/producao continua bloqueado ate instalacao/validacao real do gitleaks ou decisao Pro/humana registrada.
- Bloco 29, restore completo, staging, dados reais/sanitizados, Pix/Efi real, webhooks, API externa e producao continuam fora do escopo.

## Mitigacao

- Empacotador agora evita `Objetivo` vazio em `RESUMO-ENTREGA.md`.
- Scanners locais continuam rodando com fallback conservador.
- Novo bloco deve validar gitleaks real apos instalacao manual autorizada.
