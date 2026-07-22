# Politica de sessao e RBAC admin

## Sessao

A autenticacao admin local usa sessao/cookie HTTP do Spring Security.

Cookie:

- `HttpOnly`;
- `SameSite=Lax`;
- `Secure=false` somente em `APP_ENV=local`;
- `Secure=true` por padrao fora de local via `APP_ADMIN_SESSION_COOKIE_SECURE`.

Nao ha token JWT, OAuth, autenticacao social ou token retornado no body.

## CSRF

No ambiente local, CSRF fica desabilitado para permitir smoke/E2E local com sessao.

Pendencia registrada:

```text
PENDENTE_CSRF_ADMIN_PRODUCAO
```

Fora de local, a configuracao prepara `CookieCsrfTokenRepository`; a politica final de producao deve ser revisada antes de qualquer promocao.

## CORS

CORS com credentials continua limitado a `APP_ENV=local` e origens localhost configuradas.

Proibido:

- wildcard com credentials;
- dominio de producao como default local;
- credencial real no frontend;
- localStorage/sessionStorage.

## RBAC

O backend e a fonte da autorizacao. Papeis e permissoes retornados ao frontend nao autorizam acao real neste bloco.

## Campos proibidos em resposta

Respostas admin auth nao retornam:

- senha;
- hash;
- token;
- cookie;
- CPF;
- documento;
- storage key;
- bucket;
- provider;
- hash interno.

## Complemento Bloco 13 - fail-closed

O ambiente base nao assume `local` por default. Sem `APP_ENV=local`, CSRF nao deve ser desabilitado, CORS com credentials nao deve abrir e cookie inseguro nao deve ser aceito como default.

Rotas de API agora seguem:

- health publico;
- `/api/public/**` publico;
- `POST /api/admin/auth/login` publico;
- `/api/admin/**` autenticado;
- qualquer outro `/api/**` bloqueado por deny-all.

## Complemento Bloco 14 - read-only

Os endpoints admin read-only usam `@PreAuthorize` e a matriz RBAC local:

- `ADMIN`: todos os resumos e status do sistema;
- `MODERADOR`: visao geral, anuncios, moderacao e midia;
- `USUARIO`: nenhum endpoint admin.

Nao existe perfil operacional `COMERCIAL` na V3. `ANUNCIANTE` e tipo de conta, usa o papel `USUARIO` e nao recebe permissao administrativa. A fila e as decisoes de moderacao exigem `ADMIN` ou `MODERADOR` com `ANUNCIO_LER`/`ANUNCIO_MODERAR`.

Sem sessao, a resposta esperada e `401`. Com sessao sem permissao, a resposta esperada e `403`.

Essas permissoes autorizam apenas leitura agregada local; nao autorizam aprovacao, reprovacao, exclusao, financeiro, Pix, upload, importador ou moderacao real.
