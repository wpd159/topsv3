# Bloco 13 - hardening auth admin

## Objetivo

Endurecer a autenticacao/RBAC admin criada no Bloco 12 antes de qualquer endpoint administrativo funcional.

## Consulta a producao

Nao houve consulta SSH somente leitura. As correcoes foram determinadas pela auditoria local do Bloco 12 e pelos arquivos do workspace.

## Correcoes aplicadas

- `backend/src/main/resources/application.yml` passou a usar `app.env: ${APP_ENV:nao_configurado}`.
- Somente `application-local.yml` declara `APP_ENV:local` como default local.
- `SecurityConfig` deixou de usar `anyRequest().permitAll()`.
- `/api/health/**`, `/actuator/health/**`, `/api/public/**` e `POST /api/admin/auth/login` continuam publicos conforme contrato.
- `/api/admin/**` exige sessao autenticada.
- Qualquer outro `/api/**` recebe deny-all por padrao.
- Requests frontend admin usam `credentials: "include"` explicitamente.
- O painel admin nao preenche login ou credencial por padrao.
- O empacotador nao registra mais `Testes executados: Nenhum` quando metadados nao forem informados; nesse caso registra `nao informado`.

## Escopo preservado

Nao foram criadas migrations, SQL de schema, seed real, acao administrativa critica, moderacao real, financeiro/Pix, importador real, producao, VPS, banco de producao, API externa, remote, push ou commit.

## Validacao esperada

O smoke/E2E local deve confirmar:

- login admin sintetico;
- cookie de sessao emitido;
- `me` autenticado;
- `permissions` autenticado;
- logout;
- `me` apos logout com 401;
- `/api/public/**` publico;
- `/api/admin/**` protegido;
- `/api/desconhecida` bloqueada por deny-all;
- frontend admin com `credentials: "include"`;
- ausencia de `localStorage` e `sessionStorage`.

## Pendencias

- `PENDENTE_CSRF_ADMIN_PRODUCAO` segue aberto antes de qualquer promocao para ambiente nao local.
- Nenhuma permissao RBAC libera acao administrativa real neste bloco.

## Complemento Bloco 14

O Bloco 14 habilitou `@EnableMethodSecurity` para proteger resumos admin read-only com `@PreAuthorize`.

O fail-closed de API continua valido: endpoints nao previstos seguem bloqueados, e os novos endpoints sao apenas `GET` autenticado/RBAC.
