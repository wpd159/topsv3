# Bloco 32.1 - correcao de texto tecnico publico

## Objetivo

Corrigir a apresentacao publica renderizada para impedir que estados internos, enums, snake_case, UPPER_SNAKE_CASE ou chaves de politica aparecam para visitantes.

Este bloco e local, sintetico e visual/SEO. Nao precisa Pro para analise. Pro continua obrigatorio antes de homologacao/cutover real com dados reais ou sanitizados, restore completo, financeiro, Pix/Efi, webhooks ou producao.

## Causa

A pagina publica de anuncio recebia estados tecnicos do fluxo local de idade e pendencias tecnicas do DTO publico. O componente `PublicAnuncioDetalhe` renderizava esses valores diretamente em texto visivel.

Exemplos reprovados na auditoria visual do Bloco 32:

- `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO`;
- `conteudo_autorizado`.

## Correcao

- O frontend publico passou a formatar o estado de exibicao com `formatarStatusPublico`.
- A pendencia tecnica de contato passou a ser apresentada com `formatarContatoPublico`.
- A permissao e a disponibilidade continuam vindo do backend.
- O frontend nao decide classificacao, contato, WhatsApp, midia ou liberacao de conteudo.
- O anuncio `BLOQUEADO` continua sem WhatsApp publico indevido.

## Rotulos publicos

- `conteudo_autorizado` passou a aparecer como `Conteudo disponivel`.
- `aguardando_idade` passou a aparecer como `Confirmacao de idade necessaria`.
- `confirmando` passou a aparecer como `Confirmando idade`.
- `idade_negada` passou a aparecer como `Idade nao confirmada`.
- `indisponivel` passou a aparecer como `Conteudo indisponivel`.
- `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO` passou a aparecer como `Contato mediado pelo Tops do Job.`

## Hardening do validador

`scripts/local/validar-publico-renderizado-sintetico-local.ps1` agora falha quando texto renderizado de pagina publica contem categorias tecnicas como:

- status `PENDENTE_`, `FALHA_` ou `ERRO_`;
- `WHATSAPP_PUBLICO`;
- `POLITICA_EXPOSICAO`;
- estado interno de age gate;
- snake_case visivel;
- UPPER_SNAKE_CASE visivel;
- texto tecnico generico como skeleton, API local, mock, debug, stack trace ou JSON bruto.

O relatorio lista categoria e rota, sem despejar payload bruto.

## Limites

Nao houve redesign, nova paleta, nova tipografia, animacao, botao flutuante, scroll lock ou `document.body.style.overflow`.

Nao houve dados reais, producao, VPS, banco de producao, SQL em producao, restore, `POST_DATA`, sanitizacao real, correcao de orfaos, dump novo, SQL bruto/log bruto versionado, Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real, API externa, remote, push, commit ou fase posterior.
