# Bloco 16.1 - hardening da moderacao local

## Objetivo

Endurecer as acoes locais criadas no Bloco 16 sem criar novas acoes administrativas, sem migration, sem SQL de schema e sem acesso a producao.

## Escopo

Endpoints preservados:

- `POST /api/admin/moderacao/revisoes/{id}/decidir`;
- `POST /api/admin/midias/{id}/decidir`.

Nenhum endpoint novo foi criado.

## Motivo obrigatorio

`REPROVAR` exige `motivo` valido:

- nulo retorna `400`;
- vazio retorna `400`;
- somente espacos retorna `400`;
- `observacao` nao substitui `motivo`;
- motivo e observacao sao sanitizados e limitados a 240 caracteres antes de persistencia ou auditoria;
- e-mail, contato e documento sao mascarados em snapshots JSON.

`APROVAR` pode omitir motivo nesta fase local.

## RequestIdCliente

`requestIdCliente` fica reservado para idempotencia futura.

Decisao: opcao B.

Motivo: o schema atual nao possui coluna/campo dedicado para chave de idempotencia nos fluxos de revisao e midia sem migration. Nesta fase, o campo nao deduplica, nao garante reprocessamento seguro e nao deve ser descrito como contrato de idempotencia no OpenAPI.

## Frontend

O componente com botoes locais foi renomeado de `AdminDetailedReadonlyPanel` para `AdminModerationPanel`.

O frontend continua:

- usando `credentials: "include"`;
- sem `localStorage` ou `sessionStorage`;
- sem upload;
- sem exclusao;
- sem pagamento, credito, Pix/Efi ou e-mail real;
- sem novos botoes alem de aprovar/reprovar localmente os recursos ja previstos.

## Auditoria JSON

Auditoria em JSON sanitizado e suficiente para a fase local. Os snapshots antes/depois nao podem conter dado sensivel, storage privado, payload completo, documento privado, telefone/WhatsApp bruto, e-mail privado, IP bruto ou User-Agent bruto.

Antes de homologacao/producao, revisao Pro deve decidir se os snapshots JSON permanecem, se serao complementados por hashes/campos controlados ou se serao substituidos por outro formato auditavel.

Nenhuma alteracao de schema foi feita nesta fase.

## Proibicoes preservadas

- Sem nova acao administrativa.
- Sem hard delete.
- Sem e-mail real.
- Sem upload.
- Sem pagamento, credito, Pix/Efi.
- Sem importador real.
- Sem dado real, dump real ou seed real.
- Sem producao, VPS ou banco de producao.
- Sem migration nova ou SQL de schema.
- Sem remote, push ou commit.

## Complemento Bloco 17

O Bloco 17 reaproveita o hardening do motivo obrigatorio para `SOLICITAR_AJUSTE` em revisao. `requestIdCliente` continua reservado, e outbox local nao deve ser tratado como envio real ou idempotencia global.

## Consulta a producao

Nao houve consulta SSH. O comportamento necessario foi confirmado por codigo local, documentos locais, OpenAPI, testes e dados sinteticos.
