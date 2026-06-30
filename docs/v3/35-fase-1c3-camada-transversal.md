# Fase 1C.3 - camada transversal mínima local

## Objetivo

Implementar somente infraestrutura transversal mínima para backend e frontend locais, aproveitando o skeleton da Fase 1B e os padrões documentados na Fase 1C.2.

Esta fase não cria regra de negócio, migration, SQL, schema, tabela, entidade JPA de domínio, repository, service de negócio, autenticação real, Pix/Efí, financeiro, créditos, importador, conexão com banco ou integração externa.

## Backend

Pacote principal:

```text
backend/src/main/java/br/com/topsdojob/v3/platform
```

Itens implementados:

- `platform/request/RequestIdFilter`: lê `X-Request-Id` válido ou gera UUID, devolve o header na resposta e usa MDC durante a requisição.
- `platform/request/RequestIdContext`: centraliza o nome do header e o atributo interno da requisição.
- `platform/error/ApiErrorCode`: define `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `UNPROCESSABLE_ENTITY` e `INTERNAL_ERROR`.
- `platform/error/ApiErrorResponse`: DTO público plano de erro, sem stack trace e sem segredo.
- `platform/error/GlobalExceptionHandler`: trata erros genéricos, validação Spring disponível, bad request e not found quando compatível.
- `platform/web/LocalCorsConfiguration`: restringe CORS local a origens configuradas para desenvolvimento.
- `platform/health/HealthController`: expõe `GET /api/health`, `GET /api/health/readiness` e `GET /api/health/liveness` sem consultar banco.

## Frontend

Estrutura adicionada:

```text
frontend/src/lib/api
frontend/src/lib/config
frontend/src/app/health
```

Itens implementados:

- configuração pública local com `NEXT_PUBLIC_APP_ENV`, `NEXT_PUBLIC_API_BASE_URL` e `NEXT_PUBLIC_CANONICAL_DOMAIN`;
- cliente API mínimo com montagem de URL local, envio de `X-Request-Id` e tratamento simples de erro;
- página `/health` que mostra estado local e tenta consultar `/api/health` sem quebrar quando o backend não está rodando.

Não há autenticação, token, sessão, `localStorage` de autenticação, chamada a produção ou consumo de dados reais.

## OpenAPI

O contrato local em `contracts/openapi/topsdojob-v3-local.yaml` documenta:

- `X-Request-Id`;
- `ApiErrorResponse`;
- `ApiErrorCode`;
- `GET /api/health`;
- `GET /api/health/readiness`;
- `GET /api/health/liveness`;
- respostas 200 dos health checks;
- resposta 500 genérica.

Nenhum endpoint de domínio foi criado.

## Segurança

Logs da fase:

- incluem `requestId`;
- não registram payload;
- não registram query string;
- não registram token, cookie, senha, Pix copia e cola, QR Code Pix ou dado pessoal.

Produção, VPS, banco de produção, Efí real, deploy, remote, push e commit permanecem proibidos nesta fase.

## Fase 1D

A Fase 1D continua aguardando revisão reforçada. Esta fase não cria migration, SQL, Flyway executável ou banco físico.
