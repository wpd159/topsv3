# Checklist Bloco 49 - Observabilidade e auditoria local

## Checkpoint

- [x] `git status --short` executado.
- [x] `git remote -v` confirmado vazio.
- [x] `git diff --check` executado.
- [x] `git diff --cached --check` executado.
- [x] Scanners de codificacao, arquivos proibidos e segredos executados.
- [x] Commit local do Bloco 48 criado: `9bd38f3`.
- [x] Push nao executado.

## Request-id e logs

- [x] `X-Request-Id` propagado em resposta 200.
- [x] Request-id invalido substituido.
- [x] `X-Request-Id` propagado em 400.
- [x] `X-Request-Id` propagado em 401.
- [x] `X-Request-Id` propagado em 403.
- [x] `X-Request-Id` propagado em 404.
- [x] Logs locais contem request-id.
- [x] Logs locais nao expuseram cabecalho bruto, cookie, credencial, documento, contato, IP bruto, user-agent bruto ou stack trace.

## Auditoria e erros

- [x] 400 sanitizado.
- [x] 401 sanitizado.
- [x] 403 sanitizado.
- [x] 404 sanitizado.
- [x] 500 validado por handler generico.
- [x] Auditoria admin local usa requestId.
- [x] Sanitizers de moderacao/outbox/readonly verificados.
- [x] Eventos de moderacao rastreaveis.

## Proibicoes preservadas

- [x] Sem producao, VPS, restore, staging ou dados reais.
- [x] Sem Pix/Efi real, webhook, pagamento real ou API externa.
- [x] Sem nova arquitetura de observabilidade.
- [x] Sem migration nova.
- [x] Sem push.
- [x] Sem fase posterior.
