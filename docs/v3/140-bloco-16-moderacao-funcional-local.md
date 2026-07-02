# Bloco 16 - moderacao funcional local minima

## Objetivo

Criar a primeira camada local de acoes administrativas, limitada a decisao de moderacao de revisao e midia sinteticas/locais.

Este bloco nao libera producao, homologacao real, acoes administrativas amplas, e-mail real, upload, hard delete, pagamento, credito, Pix/Efi ou importador real.

## Consulta a producao

Nao houve consulta SSH somente leitura. O comportamento necessario foi confirmado por documentos locais, migrations, enums, codigo do workspace e dados sinteticos.

## Decisao sobre arquivoMidiaId

`arquivoMidiaId` permanece visivel apenas em DTO admin local para `ADMIN` e `MODERADOR`.

Motivo: ele e um identificador interno util para correlacao de moderacao local. O contrato continua proibindo `storageProvider`, `bucket`, `chaveObjeto`, `sha256`, `etag`, URL privada, nome original e documento privado.

Antes de homologacao ou producao, a exposicao de `arquivoMidiaId` deve ser reavaliada por revisao Pro.

## Endpoints criados

- `POST /api/admin/moderacao/revisoes/{id}/decidir`;
- `POST /api/admin/midias/{id}/decidir`.

Ambos usam sessao admin local, `credentials: "include"`, RBAC no backend e auditoria administrativa sanitizada.

## Hardening Bloco 16.1

O Bloco 16.1 endurece os mesmos endpoints, sem criar nova acao administrativa:

- `REPROVAR` exige `motivo` valido, nao nulo, nao vazio e nao composto apenas por espacos;
- `observacao` nao substitui o campo `motivo` para reprovar;
- motivo e observacao sao sanitizados e limitados antes de persistencia/auditoria;
- e-mail, contato e documento em motivo sintetico sao mascarados nos snapshots;
- `requestIdCliente` fica reservado para idempotencia futura e nao garante deduplicacao nesta fase;
- o painel frontend com botoes locais foi renomeado para `AdminModerationPanel`, evitando chamar de read-only uma tela com acao local.

## Transicoes implementadas

Revisao de anuncio:

- `ABERTA` ou `EM_ANALISE` + `APROVAR` -> revisao `APROVADA`;
- `ABERTA` ou `EM_ANALISE` + `REPROVAR` -> revisao `REJEITADA`;
- anuncio associado recebe `status_moderacao` `APROVADO`, `REJEITADO` ou `BLOQUEADO`;
- anuncio pendente aprovado passa para `APROVADO`;
- anuncio reprovado passa para `REJEITADO`;
- `classificacao_conteudo` continua binaria: `LIVRE` ou `BLOQUEADO`;
- decisao unica por revisao e registrada em `decisao_moderacao`;
- `finalizado_em` e preenchido.

Midia:

- `PENDENTE` + `APROVAR` -> `PUBLICAVEL`;
- `PENDENTE` + `REPROVAR` -> `REJEITADA`;
- arquivo canonico associado vai para `VALIDADO` ou `REJEITADO`;
- `classificacao_conteudo` continua `LIVRE` ou `BLOQUEADO`;
- story continua sujeito a confirmacao de idade no fluxo publico.

## Transicoes pendentes

- `SOLICITAR_AJUSTE` em revisao foi corrigido no Bloco 17.1 para ser acao intermediaria local: nao grava `decisao_moderacao`, registra auditoria/outbox pendente e deixa a revisao apta a `APROVAR` ou `REPROVAR` depois.
- `SOLICITAR_AJUSTE` em midia permanece pendente porque `anuncio_midia` nao possui status seguro de ajuste sem migration.
- `POST /api/admin/anuncios/{id}/remeter-revisao` foi implementado no Bloco 17 com criacao local de `revisao_anuncio` quando nao houver revisao aberta.
- E-mail de rejeicao final permanece pendente para fase futura.
- Remoderacao futura deve reaproveitar o mesmo fluxo, sem decisao paralela.

## Auditoria

Cada acao registra evento em `auditoria_evento` com:

- ator da sessao admin;
- acao;
- recurso tipo;
- recurso id;
- request id;
- timestamp;
- snapshots antes/depois sanitizados.

Os snapshots nao armazenam senha, token, CPF, documento privado, storage key, bucket, hash, payload financeiro, IP bruto, User-Agent bruto ou payload completo de revisao.

Para a fase local, snapshots JSON sanitizados sao suficientes. Antes de homologacao/producao, revisao Pro deve decidir se os snapshots JSON continuam, se serao complementados por hashes/campos controlados ou se outro formato auditavel sera adotado. Nenhuma alteracao de schema foi feita no Bloco 16.1.

## Garantias

- Sem hard delete.
- Sem apagar midia, documento ou anuncio.
- Sem e-mail real.
- Sem upload real.
- Sem pagamento, credito, Pix ou Efi.
- Sem importador real.
- Sem dado real, dump real ou seed real.
- Sem migration nova ou SQL de schema.
- Sem producao, VPS, banco de producao, API externa, OpenAI ou Efi real.

## Revisao Pro

Este bloco exige auditoria Pro antes de ampliar acoes administrativas, iniciar homologacao real ou liberar producao.
