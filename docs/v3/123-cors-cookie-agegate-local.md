# CORS e cookie local do age gate

## Politica local

O backend permite CORS com credentials somente quando `APP_ENV=local` e ha origens locais configuradas em `app.cors.allowed-origins`.

Origens locais padrao no perfil local:

```text
http://localhost:3000
http://127.0.0.1:3000
```

Fora de local, a configuracao fica fechada por padrao.

## Cookie

`POST /api/public/idade/confirmar` emite `topsv3_idade_confirmada` com:

- `HttpOnly`;
- `SameSite=Lax`;
- `Secure=false` em local HTTP;
- `Secure=true` fora de local;
- assinatura HMAC-SHA-256;
- validade curta.

## Validacao smoke

O smoke local cobre:

- preflight `OPTIONS` para `/api/public/idade/confirmar`;
- `Access-Control-Allow-Origin` restrito a localhost;
- `Access-Control-Allow-Credentials=true`;
- ausencia de wildcard com credentials;
- `Set-Cookie` com HttpOnly e SameSite=Lax;
- cookie sem `Secure` em local HTTP.

## Consulta a producao

Nao houve consulta SSH somente leitura nesta fase. As regras foram confirmadas por documentos locais, codigo do workspace, smoke e E2E descartavel.
