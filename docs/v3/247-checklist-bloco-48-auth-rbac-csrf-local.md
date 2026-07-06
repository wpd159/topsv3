# Checklist Bloco 48 - Auth/RBAC/CSRF local

## Checkpoint

- [x] `git status --short` executado.
- [x] `git remote -v` confirmado vazio.
- [x] `git diff --check` executado.
- [x] `git diff --cached --check` executado.
- [x] Scanners de codificacao, arquivos proibidos e segredos executados.
- [x] Commit local do Bloco 47 criado: `7791d11`.
- [x] Push nao executado.

## Auth

- [x] Login admin local validado.
- [x] Login invalido recusado.
- [x] Cookie de sessao verificado sem registrar valor.
- [x] Logout validado.
- [x] Sessao invalidada apos logout.
- [x] Respostas sem token, cookie, credencial ou stack trace.

## RBAC

- [x] Rota admin sem sessao bloqueada.
- [x] `ADMIN` com acesso esperado.
- [x] `MODERADOR` sem acesso a configuracao sensivel.
- [x] `MODERADOR` sem acesso financeiro.
- [x] `MODERADOR` com acesso esperado a moderacao.
- [x] Fallback `/api/**` desconhecido bloqueado.

## CSRF e CORS

- [x] CORS local restrito a localhost validado.
- [x] CSRF local documentado como desabilitado para smoke local.
- [x] Configuracao nao-local com repositorio CSRF identificada.
- [x] Revisao Pro antes de homologacao/producao mantida.

## Proibicoes preservadas

- [x] Sem producao, VPS, restore, staging ou dados reais.
- [x] Sem Pix/Efi real, webhook, pagamento real ou API externa.
- [x] Sem nova arquitetura de auth.
- [x] Sem migration nova.
- [x] Sem push.
- [x] Sem fase posterior.
