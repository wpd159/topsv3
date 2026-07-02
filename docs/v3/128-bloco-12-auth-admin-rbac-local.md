# Bloco 12 - auth admin e RBAC local

## Objetivo

Criar autenticacao administrativa local minima, sessao segura por cookie e RBAC backend para preparar fases administrativas futuras sem criar acoes criticas.

## Consulta a producao

Nao houve consulta SSH somente leitura. O comportamento necessario foi definido pelos documentos locais e pelo schema V002.

## Dependencias adicionadas

- `spring-boot-starter-security`;
- `spring-security-test` para testes backend.

## Endpoints criados

- `POST /api/admin/auth/login`;
- `POST /api/admin/auth/logout`;
- `GET /api/admin/auth/me`;
- `GET /api/admin/auth/permissions`.

## Modelo

O backend usa as tabelas existentes:

- `usuario`;
- `credencial_usuario`;
- `papel_usuario`;
- `permissao`;
- `papel_permissao`.

Nao houve migration, alteracao de SQL de schema ou seed real.

## Dados sinteticos

Foi criado `scripts/local/dados-sinteticos/dados-admin-minimos.sql` apenas para PostgreSQL descartavel/E2E local.

O usuario sintetico usa dominio reservado:

```text
admin.local@example.invalid
```

O SQL versiona somente hash BCrypt sintetico. O valor bruto e montado em runtime pelo smoke local e nao fica em migration.

## RBAC

Papeis:

- `ADMIN`;
- `MODERADOR`;
- `COMERCIAL`;
- `USUARIO`.

Permissoes:

- `ADMIN_CONFIGURAR`;
- `SEGURANCA_GERENCIAR`;
- `ANUNCIO_LER`;
- `ANUNCIO_MODERAR`;
- `MIDIA_REVISAR`;
- `DOCUMENTO_REVISAR`;
- `COMERCIAL_GERENCIAR`;
- `SUPORTE_ATENDER`;
- `AUDITORIA_LER`;
- `FINANCEIRO_LER`.

## Frontend

O shell admin consome:

- `/api/admin/auth/me`;
- `/api/admin/auth/permissions`;
- `/api/admin/auth/login`;
- `/api/admin/auth/logout`.

Nao usa localStorage ou sessionStorage e nao salva credencial.

## Fora do escopo

Nao houve admin real, moderacao real, aprovacao/reprovacao, exclusao, pagamento, ajuste de credito, Pix/Efi, financeiro funcional, importador real, producao, VPS, banco de producao, API externa, dado real, dump, remote, push ou commit.

## Complemento Bloco 13

O Bloco 13 endureceu os defaults da autenticacao admin:

- `APP_ENV` no arquivo base passou a ser `nao_configurado`;
- `/api/**` desconhecida passou a deny-all;
- frontend admin passou a exigir `credentials: "include"` explicitamente;
- login admin nao fica pre-preenchido no shell;
- o empacotador deixou de registrar testes como `Nenhum` quando metadados nao forem informados.

## Complemento Bloco 14

O Bloco 14 usa a sessao/RBAC local para autorizar endpoints administrativos somente leitura.

Usuarios sinteticos adicionais foram incluidos apenas para E2E local:

- `moderador.local@example.invalid`;
- `comercial.local@example.invalid`;
- `usuario.local@example.invalid`.

Nenhum endpoint read-only cria acao critica ou escrita de dominio.
