# Contratos de moderacao, ajuste e outbox local

## Revisao - decidir

`POST /api/admin/moderacao/revisoes/{id}/decidir`

Decisoes suportadas em revisao:

- `APROVAR`;
- `REPROVAR`;
- `SOLICITAR_AJUSTE`.

`REPROVAR` e `SOLICITAR_AJUSTE` exigem `motivo`.

`APROVAR` e `REPROVAR` sao decisoes finais: gravam `decisao_moderacao` e finalizam a revisao.

`SOLICITAR_AJUSTE` e acao intermediaria local: nao grava `decisao_moderacao`, nao finaliza a revisao e permite `APROVAR` ou `REPROVAR` depois. A auditoria sanitizada e o outbox local pendente continuam obrigatorios.

Duplicidade de `SOLICITAR_AJUSTE` enquanto existir outbox pendente `MODERACAO_SOLICITAR_AJUSTE` para a revisao retorna `409`. A escolha e conflito explicito, nao retorno idempotente, para evitar falsa confirmacao enquanto `requestIdCliente` nao tiver idempotencia real.

## Midia - decidir

`POST /api/admin/midias/{id}/decidir`

Decisoes suportadas em midia:

- `APROVAR`;
- `REPROVAR`.

`SOLICITAR_AJUSTE` retorna `400` porque o schema atual nao possui status seguro de ajuste em midia.

## Remeter revisao

`POST /api/admin/anuncios/{id}/remeter-revisao`

Retornos:

- `200`: revisao local criada;
- `400`: motivo ausente, vazio ou apenas com espacos;
- `401`: sem sessao;
- `403`: papel/permissao insuficiente;
- `404`: anuncio inexistente;
- `409`: anuncio ja possui revisao aberta.

## Outbox

O outbox local registra apenas payload sanitizado:

- ids tecnicos;
- status;
- decisao;
- motivo sanitizado;
- flags de ausencia de envio externo.

`requestIdCliente` permanece reservado. Ele nao deduplica nesta fase; a unica deduplicacao atual e a chave local de outbox para `SOLICITAR_AJUSTE`.

O outbox nao pode conter:

- documento privado;
- CPF;
- telefone ou WhatsApp bruto;
- e-mail privado completo;
- storage provider, bucket, chaveObjeto, hash, etag ou URL privada;
- pagamento, credito, Pix/Efi;
- payload completo sensivel.

## Consulta read-only

O Bloco 18 cria:

- `GET /api/admin/outbox`;
- `GET /api/admin/outbox/{id}`.

Esses endpoints retornam somente DTO sanitizado e previa logica. Nao retornam JSON bruto integral, nao exibem destino real e sempre mantem `envioExternoExecutado=false`.

Nao existem endpoints `POST`, `PUT`, `PATCH` ou `DELETE` para outbox.

## Garantias

- Sem envio externo.
- Sem e-mail real.
- Sem WhatsApp real.
- Sem hard delete.
- Sem upload.
- Sem importador real.
- Sem dado real.
- Sem migration ou SQL de schema.
