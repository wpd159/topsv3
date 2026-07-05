# Bloco 29.1 - restore, sanitizacao e validacao com gate seguro

## Objetivo

Concluir o caminho operacional seguro iniciado no Bloco 29, com restore local isolado do backup autorizado, banco bruto local, banco sanitizado local, sanitizacao real e validacao SEO com dados sanitizados.

## Resultado desta execucao

Status:

```text
BLOQUEADO_PENDENTE_CLIENTE_POSTGRES_COMPATIVEL
```

O SHA-256 do backup autorizado foi conferido antes de qualquer tentativa de restore e bateu com o valor esperado:

```text
ca1ab4d33e8ac9f484f9c5cf61589014189407c960d1a296cf4b984817f9cee9
```

O cliente PostgreSQL compativel nao esta disponivel localmente. A maquina possui apenas imagem local `postgres:16` para PostgreSQL, que nao le o dump em custom format 1.16.

## Decisoes de seguranca

- nao foi executado `docker pull`;
- nao foi instalado PostgreSQL;
- nao foi usada VPS/producao como executor;
- nao foi convertido dump para SQL bruto;
- nao foi impresso conteudo do backup;
- nao foi conectada a V3 ao banco bruto;
- nao foi versionado backup, dump, SQL bruto, midia real, documento real, slug real bruto, payload financeiro, token, certificado ou segredo.

## Scripts preparados

- `scripts/local/diagnosticar-cliente-postgres-compativel-local.ps1`;
- `scripts/local/producao-restore-local-isolado.ps1`;
- `scripts/local/producao-sanitizar-db-local.ps1`;
- `scripts/local/validar-dados-producao-sanitizados-local.ps1`;
- `scripts/local/validar-seo-com-dados-sanitizados-local.ps1`.

O restore completo so deve executar quando uma imagem/cliente PostgreSQL 17.x compativel ja existir localmente. A sugestao manual documentada e `docker pull postgres:17`, mas esse comando nao foi executado automaticamente.

## Gates pendentes

- cliente PostgreSQL 17.x local autorizado;
- `pg_restore -l` compativel;
- restore local isolado;
- banco bruto local;
- banco sanitizado local;
- sanitizacao real;
- validacao de ausencia de dados sensiveis;
- validacao SEO com dados sanitizados;
- classificacao das 45 URLs desconhecidas do Bloco 28 sem lista bruta versionada.
