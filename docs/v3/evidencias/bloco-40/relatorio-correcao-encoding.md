# Relatório - Correção de encoding Bloco 40

- Resultado: OK_CORRECAO_ENCODING_BLOCO_40.
- Problema auditado: prints públicos renderizados exibiam mojibake em textos sintéticos acentuados.
- Textos afetados: os termos acentuados listados na auditoria apareciam com bytes UTF-8 interpretados como ANSI.
- Origem localizada: leitura do fixture `backend/src/test/resources/fixtures/v3-dados-sinteticos.json` no script `scripts/local/validar-e2e-local-descartavel.ps1` via `Get-Content -Raw` sem encoding explícito.
- Causa técnica: Windows PowerShell pode decodificar UTF-8 sem BOM como ANSI; o banco descartável recebia texto já corrompido durante o overlay sintético.
- Correção aplicada: leitura do fixture por `System.IO.File.ReadAllText` com `UTF8Encoding(false, true)`.
- Endurecimento aplicado: validadores passam a reprovar texto renderizado contendo os codepoints `U+00C3`, `U+00C2`, `U+FFFD` e combinações equivalentes aos termos auditados corrompidos.
- Regra de negócio alterada: não.
- Backend funcional, banco, DTO, rota, contrato, mídia, Premium, age gate ou arquitetura alterados: não.
- Produção, dados reais, upload real, CDN/storage real, restore, Pix/Efí real, pagamento real, API externa, push ou fase posterior: não.

## Prints regenerados

- `docs/v3/evidencias/bloco-32-1/prints/desktop-anuncio-livre.png`.
- `docs/v3/evidencias/bloco-32-1/prints/mobile-anuncio-livre.png`.

## Evidência renderizada

- H1 desktop/mobile: `Perfil de demonstração Goiânia premium`.
- Card público: `Perfil de demonstração para validação de navegação pública.`
- Relatório renderizado: `docs/v3/evidencias/bloco-32-1/relatorio-auditoria-renderizada-pos-correcao.md` com resultado `OK`.
- Relatório SEO renderizado: `docs/v3/evidencias/bloco-32-1/relatorio-seo-renderizado-sintetico.md` com resultado `OK`.

## Validações executadas

- `validar-publico-renderizado-sintetico-local.ps1`: OK.
- `validar-midia-publica-sintetica-local.ps1`: OK.
- `validar-agegate-whatsapp-sintetico-local.ps1`: OK.
- `validar-premium-beneficios-sintetico-local.ps1`: OK.
- `validar-admin-moderacao-sintetica-local.ps1`: OK.
- `validar-wizard-anunciar-sintetico-local.ps1`: OK.
- `validar-e2e-sintetico-local.ps1`: OK.
