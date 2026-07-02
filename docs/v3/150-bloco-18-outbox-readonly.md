# Bloco 18 - outbox administrativo read-only

## Objetivo

Criar consulta administrativa local e somente leitura para pendencias de comunicacao registradas no `outbox_evento`.

Este bloco nao envia comunicacao, nao reenvia, nao marca como enviado, nao cria worker, nao cria scheduler e nao integra SMTP, WhatsApp ou provedor externo.

## Endpoints

- `GET /api/admin/outbox`
- `GET /api/admin/outbox/{id}`

Filtros da listagem:

- `page`
- `size`
- `status`
- `tipoEvento`
- `entidadeTipo`
- `criadoDe`
- `criadoAte`

## Permissoes

- `ADMIN`: consulta outbox administrativo local.
- `MODERADOR`: consulta apenas outbox de moderacao.
- `COMERCIAL`: nao consulta outbox de moderacao.
- `USUARIO`: nao acessa admin.
- Sem sessao: `401`.

O backend e a fonte de permissao. O frontend apenas reflete o retorno da API.

## Campos expostos

- `id`
- `tipoEvento`
- `entidadeTipo`
- `entidadeId`
- `status`
- `criadoEm`
- `tentativas`
- `proximaTentativaEm`
- `resumoSanitizado`
- `previa`
- `dadosSanitizados`
- `envioExternoExecutado=false`
- `somenteLeitura=true`

## Campos bloqueados

A API nao retorna:

- destino real;
- e-mail real;
- telefone real;
- WhatsApp real;
- CPF ou documento;
- storage provider, bucket, chave, hash ou etag;
- token, senha, cookie ou secret;
- payload financeiro;
- QR Code Pix ou Pix copia e cola;
- JSON bruto integral do outbox.

Mesmo quando o outbox atual ja foi gerado com payload sanitizado, a consulta trata o conteudo como nao confiavel e aplica nova sanitizacao.

## Frontend

Foi criado `AdminOutboxPanel` no shell admin local.

O painel:

- lista pendencias visiveis;
- mostra detalhe/previa sanitizada;
- avisa que nenhuma comunicacao real foi enviada;
- nao possui botao de envio;
- nao possui botao de reenvio;
- nao possui botao de marcar como enviado;
- nao usa `localStorage` ou `sessionStorage`.

## Consulta a producao

Nao houve consulta SSH. O comportamento necessario foi confirmado por codigo local, contratos locais, schema ja existente e dados sinteticos.

## Garantias

- Sem envio real.
- Sem reenvio.
- Sem worker.
- Sem scheduler.
- Sem e-mail real.
- Sem WhatsApp real.
- Sem API externa.
- Sem dado real.
- Sem migration ou SQL de schema.
- Sem producao, VPS ou banco de producao.
- Sem remote, push ou commit.
