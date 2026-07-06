# Checklist Bloco 50 - Preflight homologacao local

## Checkpoint

- [x] `git status --short` executado antes do checkpoint.
- [x] `git remote -v` confirmado vazio antes do checkpoint.
- [x] `git diff --check` executado.
- [x] `git diff --cached --check` executado.
- [x] Scanners de codificacao, arquivos proibidos e segredos executados.
- [x] Commit local do Bloco 49 criado: `037f9b22`.
- [x] Sem push e sem remote configurado.

## Preflight local

- [x] Validador `scripts/local/validar-preflight-homologacao-local.ps1` criado.
- [x] `APP_ENV` e perfis local/homologacao/producao documentados.
- [x] Secrets e `.env` reais tratados como fora do repositorio.
- [x] CORS definitivo registrado como pendente.
- [x] Cookies `Secure`/`SameSite`/`HttpOnly` documentados.
- [x] CSRF nao-local registrado como pendencia de revisao Pro.
- [x] Storage/CDN/upload real registrados como pendentes.
- [x] Midia publica e documento privado documentados como gate.
- [x] Pix/Efi/webhooks reais registrados como pendentes.
- [x] Importador real registrado como pendente.
- [x] SEO real, 301, canonical, sitemap, robots e Search Console registrados como pendentes.
- [x] Backup/rollback registrados como pendentes.
- [x] Observabilidade/logs estruturados finais registrados como pendentes.
- [x] Bloco 29 restore completo registrado como bloqueio.
- [x] Pro obrigatorio antes de homologacao/cutover real.

## Proibicoes preservadas

- [x] Sem producao.
- [x] Sem VPS.
- [x] Sem dados reais.
- [x] Sem restore.
- [x] Sem staging real.
- [x] Sem Pix/Efi real.
- [x] Sem webhook real.
- [x] Sem API externa.
- [x] Sem remote.
- [x] Sem push.
- [x] Sem fase posterior.
