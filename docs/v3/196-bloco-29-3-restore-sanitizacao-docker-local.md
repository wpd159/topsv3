# Bloco 29.3 - restore e sanitizacao com Docker local

## Objetivo

Iniciar/validar Docker Desktop local, disponibilizar `postgres:17`, usar recursos Docker exclusivos do Tops do Job V3 e executar restore/sanitizacao local do backup autorizado.

## Resultado desta execucao

Status:

```text
BLOQUEADO_FALHA_PG_RESTORE_RAW
```

O Docker daemon local ficou OK, `docker pull postgres:17` concluiu e `pg_restore -l` com `postgres:17` aceitou o backup autorizado.

O restore bruto, porem, falhou durante `pg_restore` dentro do container proprio do Tops V3. O erro bruto nao foi versionado para evitar exposicao de detalhes do dump. A execucao parou antes de sanitizacao, validacao de dados e validacao SEO com dados sanitizados.

## Recursos Docker exclusivos

- Network: `topsv3-bloco29-net`.
- Container bruto: `topsv3-bloco29-pg17-bruto`.
- Container sanitizado: `topsv3-bloco29-pg17-sanitizado`.
- Volume bruto: `topsv3-bloco29-pgdata-bruto`.
- Volume sanitizado: `topsv3-bloco29-pgdata-sanitizado`.
- Imagem: `postgres:17`.

Todos os `docker run` operacionais foram mantidos com `--pull=never` depois do `docker pull postgres:17`.

## Preservacao TopsWI e terceiros

- Containers de outro projeto detectados no Docker local foram apenas observados e preservados.
- Nenhum container, volume, network ou compose de TopsWI/terceiros foi parado, removido, reutilizado ou alterado.
- Nenhum `docker prune` foi executado.
- Nenhum `docker compose down` foi executado.
- Nenhum volume de outro projeto foi removido.

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

## Estado dos gates

- Docker Desktop iniciado: sim.
- Docker daemon local: OK.
- `docker pull postgres:17`: OK.
- `pg_restore -l`: OK.
- Restore bruto local: falhou.
- Banco bruto local: parcialmente populado estruturalmente, com 77 tabelas detectadas por contagem agregada.
- Banco sanitizado local: criado, mas sem tabelas restauradas.
- Sanitizacao real: nao executada.
- Validacao de ausencia de dados sensiveis: nao executada.
- Validacao SEO com dados sanitizados: nao executada.

## Confirmacoes

- Producao alterada: nao.
- VPS/producao acessada: nao.
- Banco de producao usado: nao.
- SQL em producao: nao.
- Dump novo de producao: nao.
- SQL bruto gerado/versionado: nao.
- Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real ou API externa real: nao.
- Remote, push ou commit nao autorizado: nao.
- Fase posterior iniciada: nao.
