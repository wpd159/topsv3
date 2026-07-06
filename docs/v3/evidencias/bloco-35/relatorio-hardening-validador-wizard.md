# Relatorio - hardening do validador do wizard

## Arquivo

- `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`.

## Endurecimento aplicado

O validador renderizado do wizard passa a reprovar, no texto publico da pagina, os termos:

- `V3 local`;
- `dados sinteticos locais`, incluindo variante acentuada renderizada;
- `A producao valoriza`, incluindo variante acentuada renderizada;
- `A producao destaca`, incluindo variante acentuada renderizada;
- `producao observavel`, incluindo variante acentuada renderizada;
- `esta etapa local`;
- `etapa local`;
- `arquivo real`;
- `ambiente local`;
- `fixture`;
- `mock`;
- `E2E`;
- `API local`;
- `validacao sintetica`, incluindo variante acentuada renderizada;
- `snake_case`;
- `UPPER_SNAKE_CASE`.

O validador tambem reprova padroes tecnicos visiveis como `PENDENTE_`, `FALHA_`, `ERRO_`, snake_case e UPPER_SNAKE_CASE por regex de texto renderizado.

## Alvo

A regra se aplica apenas ao texto publico renderizado do wizard. Docs, scripts, relatorios e comentarios nao renderizados podem mencionar esses termos para auditoria.

## Resultado

- `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`: OK.
- Check `sem texto publico de bastidor`: OK em desktop e mobile.
- Checkpoint local corrigido do Bloco 34: `9b677ea`.
