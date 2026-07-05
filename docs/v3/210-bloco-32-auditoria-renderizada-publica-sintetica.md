# Bloco 32 - auditoria renderizada publica sintetica

## Objetivo

Consolidar o checkpoint local dos Blocos 31/31.1 e iniciar a auditoria renderizada das rotas publicas principais com dados sinteticos locais.

## Checkpoint local

- Commit local dos Blocos 31/31.1: `790188b`.
- Mensagem: `test: valida api seo sinteticos ate bloco 31.1`.
- Remote Git: vazio.
- Push: nao executado.

## Escopo da auditoria

Rotas renderizadas em desktop e mobile:

- `/`;
- `/anunciar`;
- `/acompanhantes/go/goiania`;
- `/acompanhantes/go/goiania/setor-bueno`;
- `/acompanhantes/df/brasilia`;
- `/anuncios/demo-goiania-livre-premium`;
- `/anuncios/demo-goiania-bloqueado`;
- `/sitemap.xml`;
- `/robots.txt`.

## Execucao

- Script: `scripts/local/validar-publico-renderizado-sintetico-local.ps1`.
- Backend temporario: E2E descartavel local.
- Prefixo Docker: `topsv3-render-sintetico`.
- Dados: fixture sintetica local.
- Frontend: Next.js dev local apontando para o backend temporario.
- Browser: Edge/Chrome/Chromium local via CDP.
- Prints: `docs/v3/evidencias/bloco-32/prints/`.

## Resultado

- Auditoria renderizada sintetica automatizada: OK.
- SEO renderizado sintetico: OK.
- UI desktop/mobile sintetica: OK.
- `BLOQUEADO` sem WhatsApp publico indevido: OK.
- Sem scroll horizontal: OK.
- Sem scroll lock e sem `document.body.style.overflow`: OK.
- Sem rotas publicas proibidas como `/anuncio/[id]`, `/perfil/[slug]`, `/acompanhante/[slug]` ou `/ads/[slug]`: OK.

## Correcao posterior

A revisao visual humana do Bloco 32 reprovou a entrega porque os prints publicos de anuncio exibiam codigos internos como `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO` e `conteudo_autorizado`. O Bloco 32.1 corrige essa falha de apresentacao, reforca o validador renderizado para detectar enum/status/snake_case visivel e gera novas evidencias em `docs/v3/evidencias/bloco-32-1/`.

## Limites

Nao houve redesign, troca de paleta, troca de tipografia, animacao nova, botao flutuante, scroll lock ou ajuste visual/frontend. Nao houve dados reais, producao, VPS, banco de producao, restore, `POST_DATA`, sanitizacao real, correcao de orfaos, dump, SQL bruto, Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real, API externa, remote ou push.

Pro nao e necessario para analisar este bloco local/sintetico, mas continua obrigatorio antes de homologacao/cutover real com dados reais/sanitizados, financeiro, Pix/Efi, webhooks ou producao.
