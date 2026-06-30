# Decisões pré-Fase 1D

## Escopo

Este documento registra decisões e pendências que devem orientar a criação das migrations Flyway na Fase 1D. Ele não cria SQL, schema, tabela ou migration.

## Decisões e pendências

### UUID v7 em Java 17 e PostgreSQL

Status: `DECISAO_PENDENTE_FASE_1D`.

Decisão já aprovada no ADR-011: PKs internas usam UUID v7. Detalhe pendente: biblioteca/implementação concreta de UUID v7 em Java 17 e estratégia de validação no PostgreSQL.

Diretriz para 1D: gerar UUID v7 inicialmente pela aplicação. Banco deve armazenar como `uuid`. Se houver extensão útil e aprovada, documentar antes de habilitar.

### Geração inicial de UUID

Status: `APROVADO_PARA_1D`.

UUID será gerado inicialmente pela aplicação. O banco não deve depender de função própria para criar PK na primeira migration, salvo revisão explícita.

### Extensão PostgreSQL para UUID

Status: `DECISAO_PENDENTE_FASE_1D`.

A Fase 1D deve decidir se alguma extensão será habilitada para apoio futuro a UUID. Alternativa padrão: sem extensão UUID inicial, mantendo geração na aplicação.

### `pg_trgm`

Status: `DECISAO_PENDENTE_FASE_1D`.

`pg_trgm` é candidato para busca textual aproximada e autocomplete. A Fase 1D deve revisar custo de índice, consultas esperadas e se a busca inicial precisa dele no baseline.

### `unaccent`

Status: `DECISAO_PENDENTE_FASE_1D`.

`unaccent` é candidato para busca sem acento. A Fase 1D deve decidir se normalização será feita no banco ou em campos normalizados pela aplicação.

### CHECK constraint versus tabela de catálogo

Status: `DECISAO_PENDENTE_FASE_1D`.

Diretriz inicial: enums pequenos, estáveis e puramente técnicos podem usar CHECK. Catálogos com administração, tradução, ordenação, ativação/inativação ou regra comercial devem usar tabela de catálogo.

Itens que exigem revisão: status de anúncio, status financeiro, papéis/permissões, categoria, origem de crédito, status de importação e benefícios premium.

### Nomes definitivos de tabelas críticas

Status: `APROVADO_PARA_1D`.

Nomes críticos aprovados para materialização inicial: `usuario`, `sessao_usuario`, `anuncio`, `anuncio_midia`, `story_anuncio`, `revisao_anuncio`, `anuncio_midia_revisao`, `decisao_moderacao`, `movimento_credito`, `saldo_credito_usuario`, `pagamento`, `pagamento_evento`, `pagamento_webhook`, `pagamento_conciliacao`, `documento_busca_anuncio`, `outbox_evento`, `auditoria_evento`.

### Política de schema

Status: `DECISAO_PENDENTE_FASE_1D`.

Opção conservadora inicial: `public` único para reduzir complexidade da Fase 1D. Alternativa: schemas separados por domínio, auditoria, staging e backup. A Fase 1D deve decidir antes de criar V001/V002.

### Índices FTS/trigram

Status: `DECISAO_PENDENTE_FASE_1D`.

Diretriz: criar índices básicos seletivos primeiro e deixar índices pesados de busca para migration própria, planejada como `V016__indices_busca.sql`, após decisão sobre `pg_trgm` e `unaccent`.

### Constraints financeiras de tolerância zero

Status: `APROVADO_PARA_1D`.

Migrations financeiras devem proteger idempotência, `txid`, eventos de webhook, vínculo entre pagamento aprovado e movimento de crédito, saldo não negativo quando aplicável e unicidade de conciliação.

### Separação entre staging e tabelas finais

Status: `APROVADO_PARA_1D`.

Staging deve ficar separado das tabelas finais por prefixo `stg_` e controle de execução. Tabelas staging não são fonte canônica e não promovem dados automaticamente.

### Outbox

Status: `APROVADO_PARA_1D`.

`outbox_evento` será tabela transacional interna para eventos assíncronos, com idempotência, status, tentativas e próxima tentativa. Outbox não substitui a fonte de verdade do domínio.

### Auditoria

Status: `APROVADO_PARA_1D`.

`auditoria_evento` deve existir para ações administrativas, financeiras, premium, backup e segurança. Metadados não podem conter senha, token, segredo, certificado ou payload financeiro integral.

### Backup

Status: `APROVADO_PARA_1D`.

Tabelas de backup registram política, execução, artefato e teste de restauração. Backup real e restore não são executados nesta fase.

### Importação

Status: `APROVADO_PARA_1D`.

Importação usa `importacao_execucao`, `importacao_mapeamento`, `importacao_pendencia` e tabelas `stg_`. Supabase é origem temporária de importação, não dependência V3.

## Pendências fortes para revisão na Fase 1D

- Implementação concreta de UUID v7 em Java 17.
- Decisão final sobre extensão UUID no PostgreSQL.
- Decisão final sobre `pg_trgm`.
- Decisão final sobre `unaccent`.
- Definição final de CHECK versus catálogo por enum crítico.
- Decisão final sobre `public` único versus schemas separados.
- Revisão de índices pesados de busca e risco de lock.
- Revisão financeira antes de criar `pagamento`, créditos e conciliação.

## Registro pós-materialização da Fase 1D

As migrations `V001` a `V017` foram materializadas localmente para auditoria, sem aplicação em banco e sem execução Flyway. As decisões tomadas para viabilizar o SQL revisável estão registradas em `docs/v3/61-pendencias-e-decisoes-schema-1d.md`.

Essas decisões permanecem com status de auditoria: não autorizam execução em banco, importador, entidades JPA, repositories, services de domínio, controllers de domínio ou fases dependentes do schema.
