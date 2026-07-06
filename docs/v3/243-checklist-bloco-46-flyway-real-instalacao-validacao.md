# Checklist Bloco 46 - Flyway real instalacao/validacao

## Checkpoint

- [x] `git status --short` executado.
- [x] `git remote -v` confirmado vazio.
- [x] `git diff --check` executado.
- [x] `git diff --cached --check` executado.
- [x] Scanners de codificacao, arquivos proibidos e segredos executados.
- [x] Commit local do Bloco 45 criado: `282802d`.
- [x] Push nao executado.

## Winget/Flyway

- [x] `where.exe winget` executado.
- [x] `where.exe flyway` executado.
- [x] `winget search --id Redgate.Flyway -e --accept-source-agreements` executado.
- [x] Pacote exato `Redgate.Flyway` nao encontrado.
- [x] `PENDENTE_FLYWAY_INSTALACAO_LOCAL` registrado.
- [x] Nenhuma instalacao alternativa tentada.
- [x] Nenhum `docker pull` executado.

## Validador

- [x] `scripts/local/validar-flyway-real-local.ps1` executado.
- [x] Resultado `PENDENTE_FLYWAY_REAL_LOCAL` registrado.
- [x] Exit code operacional `2` confirmado via `$LASTEXITCODE`.
- [x] Nenhum recurso Docker criado para Flyway.
- [x] Nenhum fallback por `psql` usado como aprovacao.

## Proibicoes preservadas

- [x] Sem dados reais.
- [x] Sem producao, VPS, restore ou staging.
- [x] Sem Pix/Efi real, webhook ou API externa.
- [x] Sem migration nova.
- [x] Sem push.
- [x] Sem fase posterior.
