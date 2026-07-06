# Bloco 39 - Age Gate e WhatsApp sintetico

## Objetivo

Validar localmente, com dados sinteticos e ambiente descartavel, que a V3 preserva o modelo binario de classificacao `LIVRE`/`BLOQUEADO`, aplica confirmacao de idade pelo backend e libera WhatsApp apenas por decisao backend.

## Escopo executado

- Checkpoint local do Bloco 38 em `587e2df`.
- Criacao do validador `scripts/local/validar-agegate-whatsapp-sintetico-local.ps1`.
- Validacao de detalhe publico `LIVRE` sem age gate.
- Validacao de detalhe publico `BLOQUEADO` protegido antes da confirmacao de idade.
- Validacao de confirmacao de idade maior, menor de 18 anos e data invalida.
- Validacao de clique WhatsApp mediado pelo endpoint backend.
- Validacao de stories protegidos por confirmacao de idade quando a rota local existe.

## Regras preservadas

- Frontend nao decide classificacao.
- Frontend nao monta WhatsApp bruto.
- Conteudo `BLOQUEADO` nao aparece como anuncio publico normal sem idade confirmada.
- WhatsApp de `BLOQUEADO` nao e exposto antes da confirmacao de idade.
- Confirmacao de idade local usa cookie HttpOnly assinado e SameSite=Lax.
- Nenhum numero real, dado real, producao, VPS, restore, Pix/Efi real, pagamento, API externa ou push foi usado.

## Observacao de UI

A copy publica de stories foi ajustada para evitar enum tecnico visivel e manter texto natural ao usuario: `Stories protegidos ficam disponíveis após confirmação de idade.`

## Evidencias

- Relatorio de checkpoint: `docs/v3/evidencias/bloco-39/relatorio-checkpoint-bloco-38.md`.
- Relatorio Age Gate/WhatsApp: `docs/v3/evidencias/bloco-39/relatorio-agegate-whatsapp-sintetico.md`.
- Relatorio E2E descartavel: `docs/v3/evidencias/bloco-39/relatorio-e2e-agegate-whatsapp-sintetico.md`.
- Relatorio de validacoes: `docs/v3/evidencias/bloco-39/relatorio-validacoes.md`.
- Riscos residuais: `docs/v3/evidencias/bloco-39/relatorio-riscos-residuais.md`.
