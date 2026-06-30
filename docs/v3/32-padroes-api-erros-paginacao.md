# Padrões de API, erros e paginação

## Escopo das Fases 1C.2 e 1C.3

A Fase 1C.2 documentou contratos transversais para implementação futura de backend e frontend. A Fase 1C.3 implementa apenas o subconjunto local mínimo: request id, resposta padronizada de erro, handler global genérico, health checks locais e cliente API local do frontend.

Esses padrões continuam sem regra de negócio, migration, SQL, entidade JPA de domínio, repository, service de negócio, conexão com banco ou integração externa.

## Princípios de contrato

- Entidades JPA não devem ser expostas diretamente em API pública ou administrativa.
- APIs expõem DTOs públicos, estáveis e versionáveis.
- Campos internos, flags operacionais, hashes, tokens, segredos, payloads financeiros e metadados sensíveis não entram em DTO público.
- Contratos OpenAPI devem ser atualizados antes ou junto da implementação.
- Alterações incompatíveis exigem versão, migração de cliente ou decisão formal.
- Todos os erros seguem formato único.
- Todo request deve ter `requestId` rastreável.

## Request id

Nome do header:

```text
X-Request-Id
```

Regras:

- Se o cliente enviar `X-Request-Id` válido, o backend deve propagá-lo.
- Se o cliente não enviar, o backend deve gerar um identificador opaco.
- O mesmo `requestId` deve aparecer em resposta, erro e logs.
- `requestId` não deve conter dado pessoal ou segredo.
- `requestId` não é token de autenticação.

## Padrão de resposta de erro

Formato implementado na camada transversal mínima:

```json
{
  "timestamp": "2026-06-26T03:00:00Z",
  "status": 500,
  "error": "Internal Server Error",
  "code": "INTERNAL_ERROR",
  "message": "Erro interno inesperado.",
  "path": "/api/health",
  "requestId": "11111111-1111-4111-8111-111111111111"
}
```

Campos:

- `timestamp`: instante UTC da resposta.
- `status`: status HTTP.
- `error`: razão HTTP padronizada.
- `code`: código estável para frontend e logs.
- `message`: mensagem segura em português do Brasil.
- `path`: caminho HTTP sem query string.
- `requestId`: identificador de correlação.

Não incluir:

- stack trace;
- classe Java;
- query SQL;
- token;
- senha;
- hash;
- payload financeiro integral;
- dados pessoais desnecessários;
- caminho interno de arquivo;
- segredo de configuração.

## Status HTTP padronizados

### 400 Bad Request

Uso: requisição malformada, JSON inválido, parâmetro com tipo incorreto ou contrato impossível de interpretar.

Código sugerido: `BAD_REQUEST`.

### 401 Unauthorized

Uso: autenticação ausente, expirada, inválida ou sessão revogada.

Código sugerido: `UNAUTHORIZED`.

Regra: não informar se usuário, e-mail ou telefone existem.

### 403 Forbidden

Uso: usuário autenticado sem permissão para a ação.

Código sugerido: `FORBIDDEN`.

### 404 Not Found

Uso: recurso inexistente ou recurso que não deve ser revelado ao usuário atual.

Código sugerido: `NOT_FOUND`.

### 409 Conflict

Uso: conflito de estado, concorrência otimista, duplicidade por índice único ou idempotência já processada.

Códigos sugeridos: `CONFLICT`, `VERSION_CONFLICT`, `DUPLICATE_RESOURCE`, `IDEMPOTENCY_CONFLICT`.

### 422 Unprocessable Entity

Uso: payload sintaticamente válido, mas inválido pelas regras de validação do contrato ou do domínio futuro.

Código sugerido: `UNPROCESSABLE_ENTITY`.

### 500 Internal Server Error

Uso: falha inesperada.

Código sugerido: `INTERNAL_ERROR`.

Regra: resposta pública genérica; detalhes ficam apenas em logs seguros com `requestId`.

## Padrão de validação

Validações de contrato devem retornar 400 quando a requisição não puder ser lida e 422 quando puder ser lida, mas violar campos, formatos ou regras de entrada.

Detalhes de validação devem usar:

- `field`: caminho lógico do campo no DTO;
- `code`: código estável;
- `message`: mensagem segura para usuário ou operador;
- `rejectedValue`: proibido por padrão, permitido somente se não for sensível e houver justificativa.

## Paginação

Parâmetros planejados:

- `page`: página baseada em zero, padrão `0`.
- `size`: quantidade por página, padrão por endpoint.
- `sort`: lista de ordenações permitidas.

Limites:

- `size` deve ter máximo por endpoint.
- Endpoints públicos devem evitar páginas grandes.
- Paginação não deve permitir extração massiva sem rate limit e autorização.

Formato de resposta:

```json
{
  "data": [],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

Regras:

- `data` sempre é array em respostas paginadas.
- `page.totalElements` pode ser omitido em endpoints onde contagem exata for cara ou arriscada.
- Cursor pagination pode ser adotada em feeds/eventos se offset pagination não for segura.

## Filtros

Filtros devem ser explícitos por endpoint. Não existe filtro livre que mapeia automaticamente qualquer campo interno.

Padrões:

- nomes sem acentos;
- valores escalares ou listas documentadas;
- datas em ISO 8601;
- instantes em UTC;
- enums documentados no OpenAPI;
- filtros desconhecidos retornam 400.

Filtros públicos planejados para busca:

- `uf`
- `cidade`
- `bairro`
- `categoria`
- `q`
- `precoMin`
- `precoMax`
- `temMidia`

## Ordenação

Parâmetro:

```text
sort=campo,direcao
```

Exemplos:

```text
sort=criadoEm,desc
sort=nome,asc
```

Regras:

- campos ordenáveis devem estar em allowlist por endpoint;
- direção aceita somente `asc` ou `desc`;
- ordenação padrão deve ser documentada;
- ordenação por campo sensível ou interno é proibida;
- ordenação que gere plano ruim deve ser negada ou substituída por índice adequado em fase futura.

## DTO público

Regras:

- DTO público não é entidade JPA.
- DTO público não contém relacionamento lazy, proxy, campo interno ou objeto de infraestrutura.
- DTO público deve ter nomes estáveis e claros.
- DTO de escrita e DTO de leitura devem ser separados quando houver risco de exposição ou mass assignment.
- Campos financeiros, autenticação, moderação e auditoria exigem DTOs específicos.

## Contratos OpenAPI futuros

Cada endpoint futuro deve documentar:

- método e caminho;
- autenticação exigida;
- permissões quando aplicável;
- request id;
- parâmetros de paginação/filtro/ordenação;
- DTO de request;
- DTO de response;
- erros 400, 401, 403, 404, 409, 422 e 500 quando aplicáveis;
- exemplos sem dados reais;
- campos sensíveis explicitamente ausentes.

## Fora de escopo

- controllers de domínio;
- validação Bean Validation;
- filtros reais;
- paginação real;
- consulta ao banco;
- entidade JPA;
- repository;
- service de negócio.
