# Bloco 19 - simulacao local de outbox

## Escopo

O Bloco 19 adiciona simulacao local e controlada de processamento do outbox administrativo.

Endpoint criado:

- `POST /api/admin/outbox/{id}/simular-processamento-local`

O endpoint e restrito a `ADMIN`, exige sessao administrativa local e usa apenas dados sinteticos no E2E descartavel.

O Bloco 19.1 endurece o endpoint para exigir `app.env=local`. Fora de local, o backend retorna `403` e nao altera o outbox.

O Bloco 20 adiciona preview read-only em endpoint separado. Preview nao executa simulacao e nao altera status.

## Checkpoint local

Antes das alteracoes deste bloco, foi executado checkpoint local sem push:

- commit: `ebbb94a`
- mensagem: `feat: consolida base v3 local ate bloco 18`
- remote: vazio
- push: nao executado

## Status usado

O schema atual de `outbox_evento.status` permite:

- `PENDENTE`
- `PROCESSANDO`
- `PROCESSADO`
- `ERRO`
- `CANCELADO`

Por isso, a simulacao local altera somente `PENDENTE -> PROCESSADO`.

Essa alteracao nao significa envio real. Significa apenas que o evento sintetico/local foi processado pela simulacao do Bloco 19.

Fora de `APP_ENV=local`, essa alteracao nao pode ocorrer.

Nao foi criada migration e nao houve alteracao de SQL de schema.

## Regras da simulacao

- carrega `outbox_evento` por id;
- exige `app.env=local`;
- fora de local retorna `403`;
- aceita somente status `PENDENTE`;
- outbox inexistente retorna `404`;
- outbox fora de `PENDENTE` retorna `409`;
- altera status para `PROCESSADO`;
- preenche `processado_em`;
- registra auditoria `OUTBOX_SIMULACAO_LOCAL`;
- retorna `envioExternoExecutado=false`;
- nao envia e-mail, WhatsApp, SMS, webhook ou qualquer comunicacao externa.

## Auditoria

A auditoria registra:

- ator da sessao;
- acao `OUTBOX_SIMULACAO_LOCAL`;
- recurso `OUTBOX_EVENTO`;
- id do outbox;
- status antes/depois;
- dados sanitizados por allowlist;
- `payloadBrutoExposto=false`;
- `envioExternoExecutado=false`;
- `workerExecutado=false`;
- `schedulerExecutado=false`.

Nao deve armazenar contato real, documento, storage privado, segredo, dado financeiro ou Pix.

## Permissoes

- `ADMIN`: pode consultar e simular processamento local.
- `MODERADOR`: pode consultar outbox de moderacao, mas nao simular.
- `COMERCIAL`: nao acessa outbox nem simula.
- `USUARIO`: nao acessa outbox nem simula.
- sem sessao: `401`.

## Frontend

O painel `AdminOutboxPanel` exibe o botao `Simular processamento local` somente quando a sessao retornada pelo backend tem papel `ADMIN`.

O frontend:

- usa `credentials: include`;
- nao usa `localStorage`;
- nao usa `sessionStorage`;
- nao decide permissao final;
- nao cria botao enviar;
- nao cria botao reenviar;
- nao cria botao marcar enviado.

## Riscos residuais

- Envio real continua inexistente e proibido.
- Idempotencia real por `requestIdCliente` segue reservada para fase futura.
- Politica de retry real, worker, scheduler e provider externo exige revisao Pro antes de qualquer homologacao/producao.
- Auditoria JSON e allowlist de outbox devem passar revisao Pro antes de ambiente real.
