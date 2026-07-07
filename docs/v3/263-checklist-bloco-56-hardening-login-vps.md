# Checklist Bloco 56 - Hardening login admin e VPS

## Checkpoint

- [x] `git status --short` executado.
- [x] `git remote -v` confirmado vazio.
- [x] `git diff --check` executado.
- [x] `git diff --cached --check` executado.
- [x] Scanners iniciais executados.
- [x] Inventario inicial salvo fora do repositorio.
- [x] Nenhum delta staged inicial exigia commit de checkpoint.

## Login admin

- [x] 5 falhas em 15 minutos bloqueiam por 15 minutos.
- [x] Bloqueio aplicado por login normalizado/hash.
- [x] Bloqueio aplicado por IP hash.
- [x] Login inexistente conta como falha.
- [x] Erro generico preservado.
- [x] `429` usado durante bloqueio.
- [x] Sucesso limpa tentativas.
- [x] Senha, cookie, token, e-mail bruto, IP bruto e user-agent bruto nao sao gravados pelo lockout.
- [x] Migration nao criada por nao ser necessaria nesta etapa local.

## Session fixation

- [x] Login bem-sucedido troca ID da sessao antes de salvar `SecurityContext`.
- [x] Login falho nao autentica sessao.
- [x] Logout invalida sessao.

## Gitleaks

- [x] Gitleaks real localizado apos recarga de PATH.
- [x] Historico Git completo escaneado.
- [x] Resultado sem leaks.

## VPS e site.zip

- [x] Protocolo VPS isolada criado.
- [x] `site.zip` manual tratado como artefato confidencial excepcional.
- [x] `site.zip` manual nao tratado como pacote oficial.
- [x] Nenhum backup, dump, log bruto ou dado sensivel foi copiado para docs, Git ou ZIP novo.

## Proibicoes preservadas

- [x] Sem producao.
- [x] Sem VPS.
- [x] Sem dados reais novos.
- [x] Sem restore.
- [x] Sem staging real.
- [x] Sem Pix/Efi real.
- [x] Sem webhook real.
- [x] Sem API externa.
- [x] Sem remote.
- [x] Sem push.
- [x] Sem fase posterior.
