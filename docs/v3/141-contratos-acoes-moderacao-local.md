# Contratos de acoes de moderacao local

## Autenticacao e RBAC

Todos os endpoints usam sessao admin local por cookie.

| Endpoint | ADMIN | MODERADOR | COMERCIAL | USUARIO | Sem sessao |
| --- | --- | --- | --- | --- | --- |
| `POST /api/admin/moderacao/revisoes/{id}/decidir` | 200 | 200 | 403 | 403 | 401 |
| `POST /api/admin/midias/{id}/decidir` | 200 | 200 | 403 | 403 | 401 |

O frontend pode esconder botoes, mas a decisao de permissao e sempre do backend.

## Request de revisao

```json
{
  "decisao": "APROVAR",
  "classificacaoConteudo": "LIVRE",
  "motivo": "acao local de moderacao",
  "observacao": "sem e-mail real",
  "requestIdCliente": "opcional-local"
}
```

`decisao` aceita somente:

- `APROVAR`;
- `REPROVAR`;
- `SOLICITAR_AJUSTE` somente para revisao.

`classificacaoConteudo` aceita somente:

- `LIVRE`;
- `BLOQUEADO`.

Para `REPROVAR` e `SOLICITAR_AJUSTE`, o campo `motivo` e obrigatorio. Valor nulo, vazio ou apenas com espacos retorna `400`. `observacao` e complementar e nao substitui `motivo`.

`requestIdCliente` permanece reservado para idempotencia futura. Nesta fase ele nao deduplica, nao reprocessa com seguranca e nao deve ser usado como garantia operacional.

`APROVAR` e `REPROVAR` sao decisoes finais e gravam `decisao_moderacao`. `SOLICITAR_AJUSTE` e acao intermediaria local: nao grava `decisao_moderacao`, nao finaliza a revisao, registra auditoria/outbox pendente e ainda permite `APROVAR` ou `REPROVAR` depois. Duplicidade de `SOLICITAR_AJUSTE` com outbox pendente retorna `409`.

## Request de midia

```json
{
  "decisao": "REPROVAR",
  "classificacaoConteudo": "BLOQUEADO",
  "motivo": "acao local de moderacao",
  "observacao": "sem upload e sem exclusao",
  "requestIdCliente": "opcional-local"
}
```

`SOLICITAR_AJUSTE` em midia retorna `400` nesta fase, pois nao existe status seguro de ajuste para `anuncio_midia` sem migration.

## Remeter anuncio para revisao

```json
{
  "motivo": "remeter anuncio para revisao local",
  "observacao": "sem comunicacao real",
  "requestIdCliente": "reservado-local"
}
```

Endpoint:

- `POST /api/admin/anuncios/{id}/remeter-revisao`.

Resultado esperado:

- exige `motivo` valido; valor nulo, vazio ou apenas com espacos retorna `400`;
- cria `revisao_anuncio` local `ABERTA` se nao houver revisao aberta;
- atualiza anuncio para `PENDENTE_REVISAO` e `status_moderacao` `PENDENTE`;
- registra auditoria sanitizada;
- registra outbox local pendente `ANUNCIO_REMETIDO_REVISAO`;
- nao envia e-mail, WhatsApp ou evento externo.

## Response

```json
{
  "id": "00000000-0000-4000-8000-000000000000",
  "recursoTipo": "REVISAO_ANUNCIO",
  "recursoId": "00000000-0000-4000-8000-000000000801",
  "decisao": "APROVAR",
  "status": "APROVADA",
  "classificacaoConteudo": "LIVRE",
  "auditoriaRegistrada": true,
  "emailRealEnviado": false,
  "hardDeleteExecutado": false,
  "requestId": "req-local-123456",
  "decididoEm": "2026-07-01T00:00:00Z",
  "mensagem": "revisao aprovada localmente"
}
```

## Codigos

- `200`: decisao local registrada;
- `400`: decisao invalida, motivo ausente em `REPROVAR`/`SOLICITAR_AJUSTE`, ajuste de midia pendente ou arquivo privado tratado como midia publica;
- `401`: sessao ausente;
- `403`: papel/permissao insuficiente;
- `404`: recurso inexistente;
- `409`: revisao ou midia ja finalizada, decisao de revisao ja registrada, ajuste duplicado com outbox pendente ou anuncio ja com revisao aberta;
- `500`: falha inesperada sem detalhe interno.

## Campos proibidos

Requests, responses e auditoria nao podem expor:

- documento privado;
- CPF;
- telefone ou WhatsApp bruto;
- e-mail privado completo;
- senha, hash, token ou cookie;
- IP ou User-Agent bruto;
- storage provider, bucket, chaveObjeto, sha256, etag, URL privada ou nome original;
- payload financeiro;
- payload completo de revisao.

## Pendencias de contrato

- `SOLICITAR_AJUSTE` em revisao foi corrigido no Bloco 17.1 para nao consumir decisao final.
- `SOLICITAR_AJUSTE` em midia permanece pendente ate existir status compativel.
- `requestIdCliente` fica reservado para idempotencia futura; nao ha garantia de idempotencia atual.
- E-mail real de rejeicao permanece pendente.
- Auditoria JSON sanitizada e suficiente localmente, mas precisa de revisao Pro antes de homologacao/producao.
