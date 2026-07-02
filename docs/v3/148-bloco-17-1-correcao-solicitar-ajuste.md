# Bloco 17.1 - correcao do solicitar ajuste

## Objetivo

Corrigir o fluxo local de `SOLICITAR_AJUSTE` para que ele seja acao intermediaria de moderacao, sem consumir a decisao final da revisao.

## Regra corrigida

- `APROVAR` registra `decisao_moderacao` e finaliza a revisao.
- `REPROVAR` registra `decisao_moderacao` e finaliza a revisao.
- `SOLICITAR_AJUSTE` nao registra `decisao_moderacao`.
- `SOLICITAR_AJUSTE` nao finaliza a revisao.
- `SOLICITAR_AJUSTE` mantem a revisao `ABERTA` ou `EM_ANALISE`.
- Depois de `SOLICITAR_AJUSTE`, a mesma revisao ainda pode receber `APROVAR` ou `REPROVAR`.
- Se ja existir decisao final para a revisao, `SOLICITAR_AJUSTE` retorna `409`.

## Auditoria e outbox

`SOLICITAR_AJUSTE` continua registrando:

- `auditoria_evento` sanitizado;
- `outbox_evento` local pendente `MODERACAO_SOLICITAR_AJUSTE`;
- motivo sanitizado e limitado;
- mascaramento de e-mail, contato e documento nos payloads.

Nao ha envio externo, worker, scheduler, SMTP, WhatsApp real ou API externa.

No Bloco 18, esses registros passam a ser consultaveis em admin read-only, sempre como pendencia local sanitizada e sem envio real.

## Duplicidade

A opcao escolhida foi conflito explicito.

Se ja existir outbox pendente `MODERACAO_SOLICITAR_AJUSTE` para a revisao, a nova tentativa retorna `409`.

Motivo: sem idempotencia real por `requestIdCliente`, retornar sucesso em repeticao poderia criar falsa confirmacao operacional.

## requestIdCliente

`requestIdCliente` permanece reservado para idempotencia futura.

Nesta fase:

- nao deduplica por `requestIdCliente`;
- nao garante reprocessamento seguro;
- nao substitui chave local de outbox;
- nao deve ser documentado como contrato de idempotencia.

## Remeter revisao

`POST /api/admin/anuncios/{id}/remeter-revisao` agora exige `motivo` valido.

Valor nulo, vazio ou apenas com espacos retorna `400`. `observacao` e complementar e nao substitui `motivo`.

O fluxo continua criando revisao local quando permitido, atualizando o anuncio para `PENDENTE_REVISAO`, registrando auditoria/outbox local e sem enviar comunicacao real.

## Consulta a producao

Nao houve consulta SSH. A correcao foi definida a partir de codigo local, contrato local, schema ja existente e auditoria do Bloco 17.

## Proibicoes mantidas

- Sem nova acao administrativa.
- Sem migration.
- Sem SQL de schema.
- Sem hard delete.
- Sem upload.
- Sem e-mail real.
- Sem WhatsApp real.
- Sem pagamento, credito, Pix ou Efi.
- Sem importador real.
- Sem dado real.
- Sem producao, VPS ou banco de producao.
- Sem API externa.
- Sem remote, push ou commit.

## Revisao Pro

Antes de homologacao/producao, revisar por Pro:

- politica final de idempotencia;
- estrutura futura de outbox e entrega real;
- formato definitivo da auditoria JSON;
- possivel status futuro de ajuste se o produto exigir acompanhamento estruturado.
