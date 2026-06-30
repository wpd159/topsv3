# Execução da validação PostgreSQL descartável - Fase 1D.4

## Status

Status da entrega: `OK PARA VALIDAÇÃO LOCAL, OK PARA AUDITORIA TÉCNICA LOCAL, AGUARDANDO_REVISAO_PRO`.

A Fase 1D.4 executou a validação real das migrations em PostgreSQL local descartável. Essa validação confirma que os arquivos SQL sobem limpos em banco vazio local, mas não aprova o schema para produção. A aprovação final continua pendente de revisão Pro.

## Ambiente local usado

- Docker CLI encontrado em `C:\Program Files\Docker\Docker\resources\bin\docker.exe`.
- Docker Desktop encontrado em `C:\Program Files\Docker\Docker\Docker Desktop.exe`.
- O Docker daemon estava indisponível na primeira execução da fase.
- O script iniciou o Docker Desktop local instalado e aguardou o daemon ficar disponível.
- Nas execuções seguintes, o daemon já estava disponível e não precisou ser iniciado novamente.
- Imagem PostgreSQL local selecionada: `postgres:16`.
- Imagem Flyway local: não encontrada.
- Flyway CLI: não encontrado.
- Pull de imagem: não necessário.
- Download ou instalação: não executado.

## Método de aplicação

Como Flyway CLI, Maven wrapper e imagem Flyway local não estavam disponíveis, a fase usou o fallback permitido:

```text
SQL_ORDENADO_PSQL
```

O script criou um PostgreSQL descartável, copiou os arquivos `V*.sql` para o container e aplicou cada arquivo em ordem com `psql -v ON_ERROR_STOP=1`. Essa validação é equivalente para sintaxe, ordem, FK, constraints, índices e extensões no PostgreSQL, mas não substitui um `flyway migrate/validate` futuro quando Flyway local estiver disponível.

## Resultado final

Resultado final do script:

```text
VALIDATION_RESULT=OK_POSTGRES_DESCARTAVEL
DOCKER_DAEMON_DISPONIVEL=True
POSTGRES_IMAGE_LOCAL=True
PULL_BLOQUEADO=False
METODO_APLICACAO=SQL_ORDENADO_PSQL
POSTGRES_EXECUTADO=True
FLYWAY_EXECUTADO=False
SQL_ORDENADO_EXECUTADO=True
MIGRATIONS_APLICADAS=True
BANCO_DESCARTADO=True
```

Resumo do schema descartável:

- tabelas em `public`: 68;
- índices em `public`: 222;
- constraints em `public`: 853;
- extensões de busca criadas: `pg_trgm`, `unaccent`.

Não restou container ou rede Docker com prefixo `topsv3-pg-1d4`.

## Resultado migration por migration

| Migration | Resultado | Método |
| --- | --- | --- |
| `V001__extensoes_postgresql.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V002__usuarios_autenticacao.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V003__localizacao.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V004__anuncios.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V005__midia_stories_documentos.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V006__moderacao.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V007__premium_creditos.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V008__financeiro_efi_historico_legado.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V009__metricas.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V010__seo_urls_redirects.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V011__banners.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V012__comercial_suporte.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V013__auditoria_outbox.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V014__backup.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V015__importacao_staging.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V016__indices_busca.sql` | OK | `SQL_ORDENADO_PSQL` |
| `V017__constraints_finais.sql` | OK | `SQL_ORDENADO_PSQL` |

## Correções feitas nesta fase

Nenhuma migration precisou ser corrigida na Fase 1D.4.

O script `scripts/local/validar-migrations-postgres-descartavel.ps1` foi ajustado para:

- tentar iniciar Docker Desktop local já instalado;
- aguardar o Docker daemon;
- bloquear pull/download quando a imagem PostgreSQL não existir localmente;
- usar fallback `SQL_ORDENADO_PSQL` quando Flyway não estiver disponível;
- registrar resultado migration por migration;
- corrigir o resumo do schema para exibir contagens completas;
- remover container e rede descartável no `finally`.

## Pendências restantes

- Executar `flyway migrate/validate` real em fase futura quando Flyway local estiver disponível sem instalação ou download.
- Manter revisão Pro obrigatória antes de qualquer aprovação final do schema.
- Validar custo de índices e extensões em revisão Pro.
- Definir sanitizer da aplicação para `auditoria_evento.antes_json` e `auditoria_evento.depois_json`.
- Não iniciar importador, backend de domínio, Fase 2 ou aplicação em banco persistente antes de autorização.

## Garantias

- Nenhuma produção, VPS, banco de produção, Efí real, OpenAI ou API externa foi acessada.
- Nenhum dado real, seed, dump, backup ou importação foi usado.
- Nenhum volume persistente foi criado.
- Nenhum `docker compose up` foi executado.
- Nenhuma entidade JPA, repository, service, controller de domínio ou importador foi criado.
- Nenhum remote, push ou commit foi executado.

## Complemento 1D.5

A Fase 1D.5 não alterou SQL de schema e não criou migration nova. Ela consolidou o pacote final de auditoria e padronizou os exit codes do validador:

- `0`: somente `OK_POSTGRES_DESCARTAVEL`;
- `1`: falha real de migration/SQL;
- `2`: pendência operacional.

O resultado real validado em PostgreSQL descartável permanece `OK_POSTGRES_DESCARTAVEL` via `SQL_ORDENADO_PSQL`.
