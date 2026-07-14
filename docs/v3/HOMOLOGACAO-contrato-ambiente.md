# HOMOLOGACAO - Contrato do ambiente

## Principio

O ambiente de homologacao/staging deve ser isolado, reproduzivel e reversivel. Ele nao pode usar producao como bancada de teste, nao pode apontar para banco de producao e nao pode depender de secrets versionados.

Este documento e contrato, nao deploy.

## Estado esperado

| Item | Contrato |
| --- | --- |
| Ambiente | `APP_ENV=homologacao` |
| Profile backend | `SPRING_PROFILES_ACTIVE=homologacao` ou equivalente nao-local aprovado |
| Frontend | build com variaveis de homologacao sem segredo |
| Dominio | dominio de homologacao dedicado, por exemplo `https://homologacao.example.invalid` |
| Canonical | dominio de homologacao ou canonical controlado conforme plano SEO; nunca producao por engano |
| Banco | PostgreSQL isolado de homologacao, sem acesso a producao |
| Migrations | Flyway real repetido no ambiente antes de aceitar dados |
| Gitleaks | scan real repetido antes de qualquer credencial externa |
| Logs | request-id/correlation-id em todas as respostas e eventos |
| Auditoria | JSON sanitizado, sem dado sensivel bruto |
| Rollback | plano testado antes de qualquer cutover |

## Variaveis obrigatorias sem valor real

| Variavel | Obrigatoria | Origem | Observacao |
| --- | --- | --- | --- |
| `APP_ENV` | Sim | ambiente | Valor contratual: `homologacao`. |
| `SPRING_PROFILES_ACTIVE` | Sim | ambiente | Profile nao-local aprovado. |
| `APP_CANONICAL_DOMAIN` | Sim | ambiente | Dominio de homologacao ou canonical aprovado. |
| `APP_CORS_ALLOWED_ORIGINS` | Sim | ambiente | Lista explicita de origens autorizadas. |
| `APP_ADMIN_SESSION_COOKIE_SECURE` | Sim | ambiente | Deve ser `true` em homologacao. |
| `EFI_ENABLED` | Sim | ambiente | Fixo em `false` no HML ate o gate de importacao/preparacao do cutover. |
| `EFI_ENVIRONMENT` | Nao nesta fase | ambiente | Configuracao adiada; quando autorizada, nunca pode apontar para producao em HML. |
| `EFI_BASE_URL` | Nao nesta fase | ambiente | Configuracao adiada para o gate financeiro. |
| `EFI_CLIENT_ID` | Nao nesta fase | secret externo | Nao instalar no HML durante o freeze. |
| `EFI_CLIENT_SECRET` | Nao nesta fase | secret externo | Nao instalar no HML durante o freeze. |
| `EFI_CERTIFICATE_PROTECTION_VALUE` | Nao nesta fase | secret externo | Nao instalar no HML durante o freeze. |
| `EFI_PIX_KEY` | Nao nesta fase | secret externo | Nao instalar no HML durante o freeze. |
| `EFI_WEBHOOK_VERIFIER_VALUE` | Nao nesta fase | secret externo | Nao instalar nem registrar webhook durante o freeze. |
| `SPRING_DATASOURCE_URL` | Sim | secret externo | Banco isolado de homologacao. |
| `SPRING_DATASOURCE_USERNAME` | Sim | secret externo | Usuario sem permissao em producao. |
| `SPRING_DATASOURCE_PASSWORD` | Sim | secret externo | Nao versionar. |
| `APP_EVENT_HASH_SALT` | Sim | secret externo | Usado para minimizacao/hash de eventos. |
| `APP_AGE_GATE_SIGNING_VALUE` | Sim | secret externo | Assinatura de confirmacao de idade. |
| `STORAGE_ENDPOINT` | Pendente | secret externo | Somente quando storage real for aprovado. |
| `STORAGE_PUBLIC_BUCKET` | Pendente | secret externo | Separado de documentos privados. |
| `STORAGE_PRIVATE_BUCKET` | Pendente | secret externo | Nunca exposto em DTO publico. |
| `STORAGE_ACCESS_KEY` | Pendente | secret externo | Nao versionar. |
| `STORAGE_SECRET_KEY` | Pendente | secret externo | Nao versionar. |

## Banco de homologacao

- Deve ser banco proprio e isolado.
- Nao pode ser banco de producao.
- Nao pode ser quarentena sanitizada sem `POST_DATA`.
- Nao pode receber restore incompleto como staging final.
- Qualquer dado real/sanitizado exige autorizacao, Pro e novo bloco.

## Storage e midia

- Storage/CDN/upload real continuam pendentes.
- O contrato especifico fica em `docs/v3/HOMOLOGACAO-storage-upload-cdn.md`.
- Documento privado nunca pode virar midia publica.
- DTO publico nao pode expor bucket, storage key, provider, hash, etag ou URL privada.
- Antes de upload real, deve existir contrato de separacao entre midia publica, midia privada e documento privado.

## Gates antes de staging real

- Bloco 29 / restore completo resolvido ou explicitamente separado do escopo.
- Secrets externos definidos fora do Git.
- CORS/cookies/CSRF revisados.
- Flyway real e gitleaks real repetidos no ambiente.
- Backup/rollback testado.
- Monitoramento e auditoria JSON revisados.
- Revisao Pro quando houver dados reais/sanitizados, financeiro, Pix/Efi, webhook, importador real ou cutover.

## Contratos criticos complementares

- Importacao real/dry-run: `docs/v3/HOMOLOGACAO-importacao-real-dryrun.md`
- SEO real/cutover: `docs/v3/HOMOLOGACAO-seo-cutover.md`
- Financeiro/Pix/Efi/webhooks: `docs/v3/HOMOLOGACAO-financeiro-pix-efi-webhooks.md`
- Backup/rollback: `docs/v3/HOMOLOGACAO-backup-rollback.md`
- Monitoramento operacional: `docs/v3/HOMOLOGACAO-monitoramento-operacional.md`
- Go/No-Go: `docs/v3/HOMOLOGACAO-go-no-go.md`
