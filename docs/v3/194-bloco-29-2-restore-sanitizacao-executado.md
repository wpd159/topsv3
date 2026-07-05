# Bloco 29.2 - restore e sanitizacao com PostgreSQL 17 autorizado

## Objetivo

Disponibilizar localmente a imagem oficial `postgres:17`, endurecer scripts para impedir pull automatico involuntario, reexecutar restore local isolado do backup autorizado, sanitizar o banco sanitizado local e validar dados/SEO apenas com agregados.

## Resultado desta execucao

Status:

```text
BLOQUEADO_PENDENTE_CLIENTE_POSTGRES_COMPATIVEL
```

O comando explicitamente autorizado foi executado:

```powershell
docker pull postgres:17
```

O comando falhou porque o Docker daemon local nao estava disponivel no pipe `dockerDesktopLinuxEngine`. Pelo escopo do bloco, a execucao parou sem tentar iniciar Docker Desktop, sem executar outro download e sem acessar producao/VPS.

## Hardening aplicado

- `diagnosticar-cliente-postgres-compativel-local.ps1` usa `docker run --pull=never` para `pg_restore --version` e `pg_restore -l`.
- `producao-restore-local-isolado.ps1` usa `docker run --pull=never` para validar `pg_restore -l`.
- `producao-restore-local-isolado.ps1` usa `docker run --pull=never` para criar o container PostgreSQL local isolado.
- A unica excecao autorizada foi o comando manual/local `docker pull postgres:17`, que falhou por Docker daemon indisponivel.

## Backup autorizado

Backup externo:

```text
C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump
```

SHA-256 esperado e conferido:

```text
ca1ab4d33e8ac9f484f9c5cf61589014189407c960d1a296cf4b984817f9cee9
```

O backup bruto nao entrou no repositorio, no ZIP, em SQL bruto ou em relatorio com conteudo.

## Gates nao executados por bloqueio operacional

- `pg_restore -l` com `postgres:17`;
- restore local isolado;
- banco bruto local;
- banco sanitizado local;
- sanitizacao real;
- validacao de ausencia de dados sensiveis;
- validacao SEO com dados sanitizados.

## Validacoes locais executadas

Passaram nesta execucao:

- diagnostico de toolchain local;
- build backend/frontend local agregado;
- compilacao backend sem testes e testes backend via Maven local;
- lint e build frontend;
- persistencia JPA estatica;
- UI mobile estatica;
- SEO publico local;
- rotas publicas e SEO local;
- layout publico renderizado;
- mapa de preservacao SEO;
- localizacao do backup autorizado e conferencia de SHA-256;
- scanners de codificacao, arquivos proibidos e segredos com fallback local;
- validacao estatica de migrations SQL;
- validacao de fonte de importacao local;
- `git diff --check` e `git diff --cached --check`.

Ficaram pendentes por dependencia direta do Docker daemon/cliente PostgreSQL 17.x:

- E2E local descartavel;
- smoke HTTP completo da API local;
- diagnostico `pg_restore -l` com imagem PostgreSQL 17;
- restore local isolado;
- sanitizacao real;
- validacao de dados sanitizados;
- validacao SEO com dados sanitizados.

## Confirmacoes

- Producao alterada: nao.
- VPS/producao acessada: nao.
- Banco de producao usado: nao.
- SQL em producao: nao.
- Dump novo de producao: nao.
- SQL bruto gerado/versionado: nao.
- Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real ou API externa real: nao.
- Remote, push ou commit nao autorizado: nao.
