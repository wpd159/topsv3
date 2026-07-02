# Politica fail-closed API admin

## APP_ENV

O arquivo base usa `APP_ENV:nao_configurado`. Isso impede que uma execucao sem ambiente explicito assuma comportamento local.

Somente o profile local pode usar:

```text
app.env=${APP_ENV:local}
```

## API

Contrato de seguranca:

- health publico;
- `/api/public/**` publico;
- `POST /api/admin/auth/login` publico;
- `/api/admin/**` autenticado;
- demais `/api/**` bloqueados por deny-all;
- demais rotas nao API tambem permanecem fechadas no backend local.

## CSRF e CORS

CSRF fica desabilitado somente quando `app.env=local`.

Fora de local:

- CSRF nao deve ficar desligado por default;
- cookie admin deve ser `Secure=true` por default;
- CORS com credentials nao deve abrir sem origem local explicita;
- segredo ficticio de idade/metrica nao deve ser aceito.

## Frontend admin

O frontend admin usa sessao/cookie e deve chamar auth admin com:

```text
credentials: "include"
```

Continuam proibidos:

- JWT;
- OAuth;
- token no body;
- `localStorage`;
- `sessionStorage`;
- credencial pre-preenchida.

## Efi Pix mock

O arquivo base deve manter:

```text
efi.pix.mock-mode=${EFI_PIX_MOCK_MODE:false}
```

Somente `application-local.yml` pode usar:

```text
efi.pix.mock-mode=${EFI_PIX_MOCK_MODE:true}
```

Health e status administrativo podem reportar o valor efetivo, mas nao podem transformar execucao fora de local em mock por default.

## Admin read-only

Novos endpoints `GET /api/admin/*/resumo`, `GET /api/admin/visao-geral` e `GET /api/admin/sistema/status` seguem a mesma politica fail-closed:

- exigem sessao;
- exigem RBAC;
- retornam apenas contadores agregados;
- nao expoem segredo, dado real, storage privado ou dado financeiro sensivel;
- nao criam acao critica.
