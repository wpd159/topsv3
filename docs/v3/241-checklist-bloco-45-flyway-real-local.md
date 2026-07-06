# Checklist Bloco 45 - Flyway real local

## Checkpoint

- [x] `git status --short` executado antes do commit do Bloco 44.
- [x] `git remote -v` confirmado vazio.
- [x] `git diff --check` executado.
- [x] `git diff --cached --check` executado.
- [x] Scanners de codificacao, arquivos proibidos e segredos executados.
- [x] Commit local do Bloco 44 criado: `0603a59`.
- [x] Push nao executado.

## Diagnostico

- [x] `where.exe flyway` executado.
- [x] `docker image ls flyway/flyway` executado.
- [x] `docker image ls postgres` executado.
- [x] Flyway CLI nao encontrado.
- [x] Imagem `flyway/flyway` nao encontrada.
- [x] Imagens `postgres:16` e `postgres:17` encontradas localmente.

## Validador

- [x] `scripts/local/validar-flyway-real-local.ps1` criado.
- [x] Exit `0` reservado para Flyway real OK.
- [x] Exit `1` reservado para falha real.
- [x] Exit `2` reservado para pendencia operacional.
- [x] Sem fallback `psql` como substituto de Flyway real.
- [x] Sem instalacao automatica.
- [x] Sem `docker pull`.
- [x] Sem migration nova.

## Resultado atual

- [x] `PENDENTE_FLYWAY_REAL_LOCAL` registrado.
- [x] Nenhum recurso Docker criado para Flyway nesta execucao.
- [x] Nenhum dado real usado.
- [x] Nenhum restore executado.
- [x] Nenhuma fase posterior iniciada.
