# HOMOLOGACAO - Preflight local

## Proposito

Este preflight separa o estado local sintetico da V3 dos requisitos que ainda precisam ser resolvidos antes de homologacao, staging real, cutover ou producao.

## Pronto localmente

- MVP local sintetico consolidado e revalidado nos blocos anteriores.
- `gitleaks` real validado localmente no Bloco 44.
- Flyway real validado via Docker local no Bloco 47 com `OK_FLYWAY_REAL_LOCAL`.
- Auth/RBAC/CSRF local validado no Bloco 48 com `OK_AUTH_RBAC_CSRF_LOCAL`.
- Observabilidade, request-id e auditoria local sanitizada validados no Bloco 49 com `OK_OBSERVABILIDADE_AUDITORIA_LOCAL`.
- Midia/fotos/stories sinteticos validados sem storage real, upload real ou dados reais.
- Premium/beneficios sinteticos validados sem Pix/Efi real, checkout, pagamento ou webhook.

## Pendente antes de homologacao

- Seguir o contrato documental do Bloco 51 em `docs/v3/HOMOLOGACAO-contrato-ambiente.md`.
- Criar ambiente de homologacao/staging com secrets fora do Git.
- Definir `APP_ENV` nao-local e profiles de deploy sem versionar credenciais reais.
- Definir CORS definitivo para dominios autorizados.
- Revisar CSRF real, HTTPS, cookie seguro e politica de sessao.
- Repetir Flyway real e gitleaks real no ambiente controlado.
- Definir storage/CDN/upload real com separacao entre midia publica, privada e documentos.
- Fechar SEO real: canonical, sitemap, robots, 301, Search Console e validacao de URLs.
- Planejar backup/rollback testado.
- Fechar monitoramento, logs estruturados e auditoria JSON final.
- Revisar os contratos criticos do Bloco 53 antes de qualquer execucao real.

## Bloqueante antes de producao

- Bloco 29 / restore completo consistente ainda pendente.
- Quarentena sanitizada sem `POST_DATA` nao pode virar staging final.
- Pix/Efi real, checkout, pagamentos e webhooks continuam proibidos sem bloco proprio.
- Importador real depende de fonte autorizada, dry-run e relatorios agregados.
- Dados reais/sanitizados exigem Pro obrigatorio e decisao humana.
- Backup/rollback devem estar testados antes de qualquer cutover.
- Auditoria JSON final, LGPD e retencao de dados sensiveis exigem revisao Pro.

## Validador

Executar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-preflight-homologacao-local.ps1
```

Resultado esperado em ambiente local seguro:

```text
VALIDATION_RESULT=OK_PREFLIGHT_HOMOLOGACAO_LOCAL
```

Esse resultado significa apenas que o preflight local/documental nao encontrou falha local concreta. Ele nao autoriza homologacao, staging real, cutover ou producao.

## Contratos complementares

- Ambiente: `docs/v3/HOMOLOGACAO-contrato-ambiente.md`
- Secrets externos: `docs/v3/HOMOLOGACAO-secrets-externos.md`
- CORS, cookies e CSRF: `docs/v3/HOMOLOGACAO-cors-cookies-csrf.md`
- Rollback e monitoramento: `docs/v3/HOMOLOGACAO-rollback-monitoramento.md`
- Importacao real/dry-run: `docs/v3/HOMOLOGACAO-importacao-real-dryrun.md`
- SEO real/cutover: `docs/v3/HOMOLOGACAO-seo-cutover.md`
- Financeiro/Pix/Efi/webhooks: `docs/v3/HOMOLOGACAO-financeiro-pix-efi-webhooks.md`
- Backup/rollback: `docs/v3/HOMOLOGACAO-backup-rollback.md`
- Monitoramento operacional: `docs/v3/HOMOLOGACAO-monitoramento-operacional.md`
- Go/No-Go: `docs/v3/HOMOLOGACAO-go-no-go.md`
