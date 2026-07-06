# HOMOLOGACAO - CORS, cookies e CSRF

## CORS

Contrato para homologacao:

- `APP_CORS_ALLOWED_ORIGINS` deve conter lista explicita de origens permitidas.
- Wildcard `*` e proibido quando houver credenciais.
- Admin e frontend publico devem usar apenas dominios de homologacao aprovados.
- Localhost pode existir apenas em ambiente local, nao em homologacao publica.
- Mudanca de origem exige revisao antes de deploy.

Exemplo documental sem valor real:

```text
APP_CORS_ALLOWED_ORIGINS=https://homologacao.example.invalid
```

## Cookies

Contrato para homologacao:

- Cookie de sessao administrativo deve ser `HttpOnly`.
- Cookie deve usar `Secure=true`.
- Cookie deve usar `SameSite=Lax` por padrao, salvo decisao Pro documentada.
- Frontend admin deve continuar usando `credentials: include`.
- Credencial nao pode ser persistida em `localStorage` ou `sessionStorage`.

## CSRF

Contrato para ambiente nao-local:

- CSRF deve estar habilitado antes de homologacao real.
- Estrategia atual nao-local usa repositorio de token CSRF por cookie.
- Smoke local pode manter CSRF desabilitado somente em `APP_ENV=local`.
- Homologacao deve validar POST/PUT/PATCH/DELETE com e sem token.
- Erros CSRF devem ser sanitizados e conter request-id.

## Pendencias

- Revisao Pro da estrategia CSRF nao-local.
- Teste real de CORS com dominio de homologacao.
- Teste de cookie seguro sob HTTPS.
- Validacao de logout e expiracao de sessao no ambiente alvo.
