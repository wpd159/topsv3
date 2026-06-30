# Validação PostgreSQL descartável - Fase 1D.3

## Status

Status da entrega: `OK PARA AUDITORIA TÉCNICA LOCAL, AGUARDANDO_REVISAO_PRO`.

A Fase 1D.3 preparou a validação local das migrations Flyway/PostgreSQL em banco descartável, mas a execução real em PostgreSQL ficou registrada como `PENDENTE_VALIDACAO_POSTGRES_LOCAL` nesta máquina.

## Escopo autorizado

Esta fase autoriza apenas validação local descartável:

- PostgreSQL local efêmero, sem volume persistente;
- Flyway somente contra banco descartável;
- remoção do container/rede ao final quando eles forem criados;
- relatório local de execução;
- correções pontuais em migrations por erro de SQL, FK, constraint ou ordem.

Nenhuma validação desta fase aprova o schema para produção. O status final permanece `AGUARDANDO_REVISAO_PRO`.

## Resultado local desta execução

Comando executado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-migrations-postgres-descartavel.ps1
```

Resultado observado:

```text
VALIDATION_RESULT=PENDENTE_VALIDACAO_POSTGRES_LOCAL
POSTGRES_EXECUTADO=False
FLYWAY_EXECUTADO=False
BANCO_DESCARTADO=False
```

Motivo registrado:

- Docker foi encontrado no PATH, mas o daemon Docker não estava iniciado/disponível.
- Flyway CLI não foi encontrado.
- Maven e Maven wrapper não estavam disponíveis para execução Flyway local.
- Nenhuma imagem foi baixada e nenhum `docker pull` foi executado.
- Nenhum container, banco, volume ou rede foi criado.

## Script criado

Arquivo criado:

```text
scripts/local/validar-migrations-postgres-descartavel.ps1
```

Comportamento esperado do script:

- localizar a raiz Git;
- validar a existência das migrations `V*.sql`;
- detectar Docker local sem iniciar serviço externo;
- detectar imagem PostgreSQL local sem baixar imagem;
- detectar Flyway CLI ou imagem Flyway local já existente;
- criar PostgreSQL descartável somente quando todos os pré-requisitos locais existirem;
- executar `flyway migrate` e `flyway validate` somente no banco descartável;
- consultar `flyway_schema_history` e resumo do schema somente no banco descartável;
- remover container e rede no `finally`;
- gerar relatório local em Markdown;
- retornar `PENDENTE_VALIDACAO_POSTGRES_LOCAL` sem falha quando faltar ferramenta local e downloads estiverem proibidos.

## Correções SQL permitidas nesta fase

### V005 - mídia, documentos e stories

Foi adicionada a constraint `anuncio_midia_story_consistencia_chk`.

Objetivo:

- garantir que `tipo = 'STORY'` use `finalidade = 'STORY'`;
- impedir mídia não story com finalidade `STORY`;
- preservar `story_anuncio` como vínculo canônico por `anuncio_midia_id`;
- manter documento privado fora de qualquer fluxo publicável.

### V008 - financeiro, Efí e histórico legado

Foram adicionadas:

- `pagamento_id_provedor_uk` em `pagamento (id, provedor)`;
- `pagamento_evento_pagamento_provedor_fk` em `pagamento_evento (pagamento_id, provedor)`.

Objetivo:

- impedir evento financeiro com `provedor` divergente do pagamento referenciado;
- manter idempotência de evento escopada por provedor;
- preservar Efí/Pix apenas como modelagem, sem integração real.

## Pendências Pro preservadas

- Executar a validação em PostgreSQL descartável quando Docker daemon e Flyway local estiverem disponíveis sem instalação ou download.
- Revisar `pg_trgm` e `unaccent`.
- Revisar o uso de schema `public`.
- Revisar todos os `CHECK` versus catálogos.
- Revisar custo dos índices, especialmente busca/FTS/trigram.
- Validar impacto das constraints finais na futura importação.
- Definir sanitizer da aplicação para `auditoria_evento.antes_json` e `auditoria_evento.depois_json`; a migration apenas cria campos e hashes, não sanitiza payload.
- Manter decisão jurídica final de retenção de documentos privados para fase futura.

## Bloqueios confirmados

- Nenhum banco persistente foi criado.
- Nenhum volume Docker persistente foi criado.
- Nenhuma migration foi aplicada nesta máquina.
- Flyway não foi executado nesta máquina.
- Nenhum dado real, seed, dump, backup ou importação foi usado.
- Nenhuma entidade JPA, repository, service, controller de domínio ou importador foi criado.
- Nenhuma produção, VPS, banco de produção, Efí real, OpenAI ou API externa foi acessada.
- Nenhum remote, push ou commit foi executado.
- Nenhuma fase dependente do schema foi iniciada.

## Complemento 1D.4

A Fase 1D.4 executou a validação real em PostgreSQL local descartável usando a imagem local `postgres:16` e o fallback `SQL_ORDENADO_PSQL`, porque Flyway CLI e imagem Flyway local não estavam disponíveis.

Resultado complementar:

- `VALIDATION_RESULT=OK_POSTGRES_DESCARTAVEL`;
- Docker Desktop foi iniciado localmente pelo script na primeira execução da Fase 1D.4;
- nenhum pull, download ou instalação foi executado;
- `V001` a `V017` aplicaram sem erro SQL;
- schema descartável inspecionado com 68 tabelas, 222 índices, 853 constraints e extensões `pg_trgm`/`unaccent`;
- container e rede descartáveis foram removidos ao final.
