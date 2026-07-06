# Bloco 45 - Flyway real local

## Objetivo

Consolidar o checkpoint local do Bloco 44 e validar se a toolchain local possui Flyway real para aplicar as migrations V001 a V017 em PostgreSQL descartavel/local.

## Checkpoint consolidado

- Checkpoint local do Bloco 44: `0603a59`.
- Mensagem do commit: `chore: valida gitleaks real ate bloco 44`.
- Remote: vazio.
- Push: nao executado.

## Diagnostico Flyway

Comandos de diagnostico solicitados:

```powershell
where.exe flyway
docker image ls flyway/flyway
docker image ls postgres
```

Resultado:

- Flyway CLI: nao localizado no PATH.
- Imagem `flyway/flyway`: nao localizada localmente.
- Imagens PostgreSQL locais: `postgres:16` e `postgres:17`.

## Validador criado

Script:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-flyway-real-local.ps1
```

Comportamento definido:

- exit `0`: `OK_FLYWAY_REAL_LOCAL`, somente quando Flyway real aplica e valida migrations em PostgreSQL descartavel;
- exit `1`: falha real de Flyway/migration;
- exit `2`: pendencia operacional, como Flyway ausente, Docker indisponivel ou imagem PostgreSQL ausente.

## Resultado desta execucao

- VALIDATION_RESULT: `PENDENTE_FLYWAY_REAL_LOCAL`.
- Motivo: Flyway CLI/imagem local nao encontrados.
- Instalacao automatica: nao executada.
- `docker pull`: nao executado.
- Recursos Docker do Flyway: nenhum criado.
- PostgreSQL descartavel do Flyway: nao iniciado porque Flyway real estava ausente.

## Limites preservados

- Sem dados reais.
- Sem producao, VPS, restore, staging, Pix/Efi real, webhook ou API externa.
- Sem migration nova.
- Sem alteracao de SQL de schema.
- Sem fase posterior iniciada.
