# Bloco 17 - solicitar ajuste e remeter revisao

## Objetivo

Implementar a continuacao local do fluxo de moderacao, sem producao, sem dados reais, sem migration e sem envio externo.

## Implementado

- `SOLICITAR_AJUSTE` em `POST /api/admin/moderacao/revisoes/{id}/decidir`;
- `POST /api/admin/anuncios/{id}/remeter-revisao`;
- outbox local pendente para eventos de moderacao;
- auditoria administrativa sanitizada;
- botoes locais no `AdminModerationPanel`.

## SOLICITAR_AJUSTE em revisao

Regras:

- exige `motivo`;
- motivo e observacao sao sanitizados e limitados;
- e-mail, contato e documento sao mascarados;
- nao registra `decisao_moderacao`, pois e acao intermediaria local e nao decisao final;
- registra `auditoria_evento`;
- registra `outbox_evento` pendente `MODERACAO_SOLICITAR_AJUSTE`;
- duplicidade de outbox pendente para a mesma revisao retorna `409`;
- depois de `SOLICITAR_AJUSTE`, a mesma revisao ainda pode receber `APROVAR` ou `REPROVAR`;
- nao envia e-mail real;
- nao envia WhatsApp real;
- nao exclui nada.

A revisao permanece `ABERTA` ou `EM_ANALISE`, porque o schema atual nao possui status especifico de "aguardando ajuste" sem migration.

## Midia

`SOLICITAR_AJUSTE` em midia permanece pendente.

Motivo: `anuncio_midia` possui apenas `PENDENTE`, `PUBLICAVEL`, `REJEITADA` e `REMOVIDA`. Nao ha status seguro de ajuste sem migration, e o Bloco 17 proibe improviso.

## Remeter anuncio para revisao

Endpoint:

- `POST /api/admin/anuncios/{id}/remeter-revisao`.

Regras:

- somente `ADMIN` e `MODERADOR` com `ANUNCIO_MODERAR`;
- exige `motivo` valido; motivo nulo, vazio ou apenas espacos retorna `400`;
- sem sessao retorna `401`;
- `COMERCIAL` e `USUARIO` retornam `403`;
- anuncio inexistente retorna `404`;
- anuncio com revisao aberta retorna `409`;
- cria `revisao_anuncio` local `ABERTA`;
- atualiza anuncio para `PENDENTE_REVISAO` e `status_moderacao` `PENDENTE`;
- registra auditoria e outbox local pendente `ANUNCIO_REMETIDO_REVISAO`;
- nao envia comunicacao real.

## Outbox local

Eventos usados:

- `MODERACAO_SOLICITAR_AJUSTE`;
- `MODERACAO_REPROVADA`;
- `ANUNCIO_REMETIDO_REVISAO`.

Todos ficam `PENDENTE`, sem worker, scheduler, SMTP, WhatsApp, API externa ou processamento real.

O Bloco 18 adiciona consulta administrativa read-only para esses eventos. A consulta nao altera status, nao envia, nao reenvia e nao processa fila.

## Consulta a producao

Nao houve consulta SSH. As regras foram confirmadas por schema local, enums, codigo local e dados sinteticos.

## Revisao Pro

Antes de homologacao/producao ou qualquer envio real, exigir revisao Pro sobre transicoes, auditoria JSON, payload de outbox e politica de comunicacao.
