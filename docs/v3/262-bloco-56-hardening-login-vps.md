# Bloco 56 - Hardening login admin e protocolo VPS

## Objetivo

O Bloco 56 endurece o login administrativo local e registra protocolo seguro para eventual VPS isolada de restore/importacao integral. O bloco nao abre homologacao real, nao executa restore, nao acessa VPS, nao acessa producao e nao usa dados reais novos.

## Entregas

- Lockout de login admin por tentativas falhas.
- Mitigacao de session fixation no login admin.
- Gitleaks real executado com historico Git completo.
- Protocolo documental para VPS isolada de restore/importacao integral.
- Registro do `site.zip` manual como artefato excepcional e confidencial de auditoria.

## Login lockout

Regra implementada:

- 5 falhas em 15 minutos bloqueiam por 15 minutos.
- O bloqueio considera login normalizado/hash e IP hash.
- Login inexistente tambem conta como falha.
- Erro permanece generico, sem enumerar usuario existente.
- Durante bloqueio o login retorna `429 Too Many Requests`.
- Sucesso limpa as tentativas do login/IP.
- Senha, cookie, token, e-mail bruto, IP bruto e user-agent bruto nao sao gravados pelo lockout.

O lockout e em memoria local/aplicacao. Migration nova nao foi criada porque o objetivo do bloco e hardening local sem persistencia operacional definitiva.

## Session fixation

No login admin bem-sucedido, a aplicacao chama `request.changeSessionId()` antes de salvar o `SecurityContext`. Login falho nao autentica sessao e logout invalida a sessao existente.

## Gitleaks historico

`gitleaks detect --source . --redact --verbose` foi executado com gitleaks 8.30.1 e historico completo do Git local.

Resultado: sem leaks encontrados.

## Protocolo VPS

O protocolo fica em `docs/v3/HOMOLOGACAO-vps-restore-integral-protocolo.md`. Ele e documental e nao cria VPS, nao acessa VPS e nao autoriza restore real.

## Limites preservados

- Sem producao.
- Sem VPS acessada.
- Sem dados reais novos.
- Sem restore.
- Sem staging real.
- Sem Pix/Efi real.
- Sem webhook real.
- Sem API externa.
- Sem remote.
- Sem push.
- Sem fase posterior.
