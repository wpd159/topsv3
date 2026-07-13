# Relatorio Flyway real local

## Resultado

- VALIDATION_RESULT=OK_FLYWAY_REAL_LOCAL
- Detalhe: Migrations aplicadas e validadas com Flyway real em PostgreSQL descartavel.
- Fonte Flyway: Docker image
- Versao Flyway: flyway/flyway:12.10.0
- Imagem PostgreSQL local selecionada: postgres:17
- Diretorio de migrations: `backend/src/main/resources/db/migration`
- Migrations encontradas: 23

## Recursos Docker

- Prefixo permitido: `topsv3-flyway-local`
- Network criada: True
- Network removida: True
- Container criado: True
- Container removido: True
- Network: `topsv3-flyway-local-net-20260713195954`
- Container: `topsv3-flyway-local-pg17-20260713195954`

## Passos
- Network descartavel criada com prefixo topsv3-flyway-local.
- Container PostgreSQL descartavel criado com --pull=never.
- PostgreSQL descartavel respondeu ao pg_isready.
- flyway info executado com sucesso via imagem local.
- flyway migrate executado com sucesso via imagem local.
- flyway validate executado com sucesso via imagem local.
- flyway info executado com sucesso via imagem local.

## Comandos

### Comando

- Comando: `docker info`
- Exit code: `0`
- Stdout:

```text
Docker daemon local disponivel.
```

### Comando

- Comando: `docker network create topsv3-flyway-local-net-20260713195954`
- Exit code: `0`
- Stdout:

```text
63653f3013e7e75239308783eccda15deb25e46f82cda85ace579c8cbc3a4837
```

### Comando

- Comando: `docker run --pull=never -d --name topsv3-flyway-local-pg17-20260713195954 --network topsv3-flyway-local-net-20260713195954 -e POSTGRES_DB=topsv3_flyway -e POSTGRES_USER=topsv3_flyway -e "POSTGRES_PASSWORD valor_omitido" -p 127.0.0.1::5432 postgres:17`
- Exit code: `0`
- Stdout:

```text
fe4ad62261549b27945113f320fa05f8f1fa105376a9da20acdb7be248f71d74
```

### Comando

- Comando: `docker run --pull=never --rm --network topsv3-flyway-local-net-20260713195954 -v "C:\topsv3\backend\src\main\resources\db\migration:/flyway/sql:ro" flyway/flyway:12.10.0 -url=jdbc:postgresql://topsv3-flyway-local-pg17-20260713195954:5432/topsv3_flyway -user=topsv3_flyway "-password valor_omitido" -locations=filesystem:/flyway/sql info`
- Exit code: `0`
- Stdout:

```text
Flyway OSS Edition 12.10.0 by Redgate

See release notes here: https://help.red-gate.com/help/flyway-cli12/help_10.aspx?topic=release-notes-and-older-versions/release-notes-for-flyway-engine
Database: jdbc:postgresql://topsv3-flyway-local-pg17-20260713195954:5432/topsv3_flyway (PostgreSQL 17.10)
Schema history table "public"."flyway_schema_history" does not exist yet
Schema version: << Empty Schema >>

+-----------+---------+---------------------------------+------+--------------+---------+----------+
| Category  | Version | Description                     | Type | Installed On | State   | Undoable |
+-----------+---------+---------------------------------+------+--------------+---------+----------+
| Versioned | 001     | extensoes postgresql            | SQL  |              | Pending | No       |
| Versioned | 002     | usuarios autenticacao           | SQL  |              | Pending | No       |
| Versioned | 003     | localizacao                     | SQL  |              | Pending | No       |
| Versioned | 004     | anuncios                        | SQL  |              | Pending | No       |
| Versioned | 005     | midia stories documentos        | SQL  |              | Pending | No       |
| Versioned | 006     | moderacao                       | SQL  |              | Pending | No       |
| Versioned | 007     | premium creditos                | SQL  |              | Pending | No       |
| Versioned | 008     | financeiro efi historico legado | SQL  |              | Pending | No       |
| Versioned | 009     | metricas                        | SQL  |              | Pending | No       |
| Versioned | 010     | seo urls redirects              | SQL  |              | Pending | No       |
| Versioned | 011     | banners                         | SQL  |              | Pending | No       |
| Versioned | 012     | comercial suporte               | SQL  |              | Pending | No       |
| Versioned | 013     | auditoria outbox                | SQL  |              | Pending | No       |
| Versioned | 014     | backup                          | SQL  |              | Pending | No       |
| Versioned | 015     | importacao staging              | SQL  |              | Pending | No       |
| Versioned | 016     | indices busca                   | SQL  |              | Pending | No       |
| Versioned | 017     | constraints finais              | SQL  |              | Pending | No       |
| Versioned | 018     | visibilidade individual midia   | SQL  |              | Pending | No       |
| Versioned | 019     | selecao administrativa stories  | SQL  |              | Pending | No       |
| Versioned | 020     | atendimento servicos anuncio    | SQL  |              | Pending | No       |
| Versioned | 021     | categorias home idade publica   | SQL  |              | Pending | No       |
| Versioned | 022     | rbac revisao midia admin        | SQL  |              | Pending | No       |
| Versioned | 023     | kyc documentos privados         | SQL  |              | Pending | No       |
+-----------+---------+---------------------------------+------+--------------+---------+----------+
```

### Comando

- Comando: `docker run --pull=never --rm --network topsv3-flyway-local-net-20260713195954 -v "C:\topsv3\backend\src\main\resources\db\migration:/flyway/sql:ro" flyway/flyway:12.10.0 -url=jdbc:postgresql://topsv3-flyway-local-pg17-20260713195954:5432/topsv3_flyway -user=topsv3_flyway "-password valor_omitido" -locations=filesystem:/flyway/sql migrate`
- Exit code: `0`
- Stdout:

```text
Flyway OSS Edition 12.10.0 by Redgate

See release notes here: https://help.red-gate.com/help/flyway-cli12/help_10.aspx?topic=release-notes-and-older-versions/release-notes-for-flyway-engine
Database: jdbc:postgresql://topsv3-flyway-local-pg17-20260713195954:5432/topsv3_flyway (PostgreSQL 17.10)
Schema history table "public"."flyway_schema_history" does not exist yet
Successfully validated 23 migrations (execution time 00:00.064s)
Creating Schema History table "public"."flyway_schema_history" ...
Current version of schema "public": << Empty Schema >>
Migrating schema "public" to version "001 - extensoes postgresql"
Migrating schema "public" to version "002 - usuarios autenticacao"
Migrating schema "public" to version "003 - localizacao"
Migrating schema "public" to version "004 - anuncios"
Migrating schema "public" to version "005 - midia stories documentos"
Migrating schema "public" to version "006 - moderacao"
Migrating schema "public" to version "007 - premium creditos"
Migrating schema "public" to version "008 - financeiro efi historico legado"
Migrating schema "public" to version "009 - metricas"
Migrating schema "public" to version "010 - seo urls redirects"
Migrating schema "public" to version "011 - banners"
Migrating schema "public" to version "012 - comercial suporte"
Migrating schema "public" to version "013 - auditoria outbox"
Migrating schema "public" to version "014 - backup"
Migrating schema "public" to version "015 - importacao staging"
Migrating schema "public" to version "016 - indices busca"
Migrating schema "public" to version "017 - constraints finais"
Migrating schema "public" to version "018 - visibilidade individual midia"
Migrating schema "public" to version "019 - selecao administrativa stories"
Migrating schema "public" to version "020 - atendimento servicos anuncio"
Migrating schema "public" to version "021 - categorias home idade publica"
Migrating schema "public" to version "022 - rbac revisao midia admin"
Migrating schema "public" to version "023 - kyc documentos privados"
Successfully applied 23 migrations to schema "public", now at version v023 (execution time 00:00.273s)

-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
A more recent version of Flyway is available. Find out more about Flyway 12.11.0 at https://help.red-gate.com/help/flyway-cli12/help_10.aspx?topic=release-notes-and-older-versions/release-notes-for-flyway-engine
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
```

### Comando

- Comando: `docker run --pull=never --rm --network topsv3-flyway-local-net-20260713195954 -v "C:\topsv3\backend\src\main\resources\db\migration:/flyway/sql:ro" flyway/flyway:12.10.0 -url=jdbc:postgresql://topsv3-flyway-local-pg17-20260713195954:5432/topsv3_flyway -user=topsv3_flyway "-password valor_omitido" -locations=filesystem:/flyway/sql validate`
- Exit code: `0`
- Stdout:

```text
Flyway OSS Edition 12.10.0 by Redgate

See release notes here: https://help.red-gate.com/help/flyway-cli12/help_10.aspx?topic=release-notes-and-older-versions/release-notes-for-flyway-engine
Database: jdbc:postgresql://topsv3-flyway-local-pg17-20260713195954:5432/topsv3_flyway (PostgreSQL 17.10)
Successfully validated 23 migrations (execution time 00:00.075s)
```

### Comando

- Comando: `docker run --pull=never --rm --network topsv3-flyway-local-net-20260713195954 -v "C:\topsv3\backend\src\main\resources\db\migration:/flyway/sql:ro" flyway/flyway:12.10.0 -url=jdbc:postgresql://topsv3-flyway-local-pg17-20260713195954:5432/topsv3_flyway -user=topsv3_flyway "-password valor_omitido" -locations=filesystem:/flyway/sql info`
- Exit code: `0`
- Stdout:

```text
Flyway OSS Edition 12.10.0 by Redgate

See release notes here: https://help.red-gate.com/help/flyway-cli12/help_10.aspx?topic=release-notes-and-older-versions/release-notes-for-flyway-engine
Database: jdbc:postgresql://topsv3-flyway-local-pg17-20260713195954:5432/topsv3_flyway (PostgreSQL 17.10)
Schema version: 023

+-----------+---------+---------------------------------+------+---------------------+---------+----------+
| Category  | Version | Description                     | Type | Installed On        | State   | Undoable |
+-----------+---------+---------------------------------+------+---------------------+---------+----------+
| Versioned | 001     | extensoes postgresql            | SQL  | 2026-07-13 23:00:01 | Success | No       |
| Versioned | 002     | usuarios autenticacao           | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 003     | localizacao                     | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 004     | anuncios                        | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 005     | midia stories documentos        | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 006     | moderacao                       | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 007     | premium creditos                | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 008     | financeiro efi historico legado | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 009     | metricas                        | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 010     | seo urls redirects              | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 011     | banners                         | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 012     | comercial suporte               | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 013     | auditoria outbox                | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 014     | backup                          | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 015     | importacao staging              | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 016     | indices busca                   | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 017     | constraints finais              | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 018     | visibilidade individual midia   | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 019     | selecao administrativa stories  | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 020     | atendimento servicos anuncio    | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 021     | categorias home idade publica   | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 022     | rbac revisao midia admin        | SQL  | 2026-07-13 23:00:02 | Success | No       |
| Versioned | 023     | kyc documentos privados         | SQL  | 2026-07-13 23:00:02 | Success | No       |
+-----------+---------+---------------------------------+------+---------------------+---------+----------+
```

### Comando

- Comando: `docker rm -f topsv3-flyway-local-pg17-20260713195954`
- Exit code: `0`
- Stdout:

```text
topsv3-flyway-local-pg17-20260713195954
```

### Comando

- Comando: `docker network rm topsv3-flyway-local-net-20260713195954`
- Exit code: `0`
- Stdout:

```text
topsv3-flyway-local-net-20260713195954
```

## Limites preservados

- Sem dados reais.
- Sem producao, VPS, restore, staging, Pix/Efi real, webhook ou API externa.
- Sem instalacao automatica de Flyway pelo script.
- O script nao executa `docker pull`.
- Sem migration nova.
