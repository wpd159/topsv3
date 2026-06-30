# Blueprint do banco V3

## Escopo da Fase 1C

A Fase 1C fecha o desenho físico-conceitual do banco V3. Este documento não é migration, não é SQL executável e não cria schema físico. A Fase 1D será responsável por transformar este desenho em migrations Flyway revisadas.

Produção permanece intocada. Nenhuma conexão externa, banco real, Efí real ou importador é usado nesta fase.

## Decisões fundamentais

- PKs internas: UUID v7, geradas inicialmente pela aplicação, com compatibilidade futura para apoio do banco.
- Instantes: PostgreSQL `timestamptz`.
- Fuso operacional: aplicação e banco em UTC.
- Datas civis sem horário: `date`.
- Valores monetários: `numeric(12,2)` como padrão; exceção exige justificativa antes da migration.
- Créditos: `integer` para quantidade individual; `bigint` para agregados e relatórios quando necessário.
- E-mail case-insensitive: `email_normalizado` em lowercase com unicidade.
- Telefone: `telefone_normalizado` em E.164 quando possível.
- Concorrência: coluna `versao` em tabelas mutáveis críticas.
- Exclusão lógica: somente quando houver justificativa histórica, jurídica ou operacional.
- Dados financeiros: tolerância zero para divergência.
- URL pública não é fonte de verdade de mídia.
- Nomes de tabelas e campos permanecem sem acentos.
- Texto funcional permanece em português do Brasil.

## Módulos e tabelas

### Usuários e autenticação

Tabelas finais:

- `usuario`
- `credencial_usuario`
- `papel_usuario`
- `permissao`
- `papel_permissao`
- `sessao_usuario`
- `token_seguranca`

Diretriz: autenticação do navegador segue ADR-007, com sessão server-side opaca, cookie HttpOnly, Secure fora do local, SameSite=Lax e CSRF para operações autenticadas que alteram estado. JWT não é a autenticação principal do navegador.

### Anúncios

Tabelas finais:

- `anuncio`
- `anuncio_status_historico`
- `documento_busca_anuncio`

Diretriz: `anuncio` é a fonte transacional do anúncio. `documento_busca_anuncio` é projeção reconstruível para busca, filtros e ranking explicável.

### Localização

Tabelas finais:

- `estado`
- `cidade`
- `bairro`
- `anuncio_localizacao`

Diretriz: localização tem chaves estrangeiras explícitas e deve permitir filtro por UF, cidade, bairro e categoria sem fallback de busca geral.

### Mídia

Tabelas finais:

- `arquivo_midia`
- `anuncio_midia`
- `documento_usuario`
- `documento_usuario_acesso`

Diretriz: mídia canônica é identificada por provider, bucket, chave de objeto, metadados e hashes. URL pública é derivada e não é fonte de verdade. Documento privado nunca é publicável, possui política de retenção própria e acesso auditável.

### Stories

Tabela final:

- `story_anuncio`

Diretriz: story usa `story_anuncio.anuncio_midia_id` obrigatório e único. `story_anuncio` não possui `anuncio_id` nem `arquivo_midia_id`; anúncio e arquivo são obtidos por `anuncio_midia`.

### Moderação

Tabelas finais:

- `revisao_anuncio`
- `anuncio_midia_revisao`
- `decisao_moderacao`

Diretriz: `decisao_moderacao` usa `revisao_anuncio_id`. `anuncio_midia_revisao.acao` aceita somente `ADICIONAR`, `SUBSTITUIR`, `REMOVER` e `REORDENAR`.

### Premium

Tabelas finais:

- `beneficio_premium`
- `beneficio_premium_opcao`
- `ativacao_beneficio`

Diretriz: ativações premium usam idempotência e vínculo auditável com usuário, anúncio, benefício, custo em créditos e janela de vigência.

### Créditos

Tabelas finais:

- `movimento_credito`
- `saldo_credito_usuario`

Diretriz: `movimento_credito` é razão financeira; `saldo_credito_usuario` é projeção. Saldo negativo é proibido salvo regra formal documentada.

### Financeiro Efí e histórico legado

Tabelas finais:

- `plano_credito`
- `pagamento`
- `pagamento_evento`
- `pagamento_webhook`
- `pagamento_conciliacao`

Diretriz: Efí é o único provedor ativo inicial. `pagamento.provedor` aceita `EFI`, `MERCADO_PAGO_LEGADO`, `OUTRO_LEGADO` e `DESCONHECIDO`. Mercado Pago é histórico/legado, não integração ativa.

### Métricas

Tabelas finais:

- `evento_visualizacao`
- `agregado_visualizacao_diaria`
- `clique_whatsapp`
- `agregado_clique_whatsapp_diario`

Diretriz: eventos brutos e agregados devem separar dado operacional, dado pessoal minimizado e projeções de relatório.

### SEO

Tabelas finais:

- `seo_url`
- `seo_metadado`
- `seo_redirect`

Diretriz: URL canônica, redirecionamentos e metadados precisam de unicidade e histórico suficiente para evitar quebra de SEO.

### Banners

Tabelas finais:

- `banner_espaco`
- `banner`
- `banner_versao`

Diretriz: banner publicado exige mídia real desktop/mobile, alt text e janela válida quando houver agendamento.

### Comercial

Tabelas finais:

- `comercial_contato`
- `comercial_interacao`
- `comercial_status`

Diretriz: dados comerciais devem respeitar retenção, minimização e anonimização quando aplicável.

### Suporte

Tabelas finais:

- `ticket_suporte`
- `mensagem_suporte`

Diretriz: suporte mantém vínculos com usuário, anúncio, pagamento e moderação quando necessário para auditoria.

### Auditoria

Tabelas finais:

- `auditoria_evento`

Diretriz: ações administrativas, financeiras, premium, backup e segurança são auditáveis. Senhas, hashes, tokens e segredos não entram em metadados.

### Backup

Tabelas finais:

- `backup_politica`
- `backup_execucao`
- `backup_artefato`
- `backup_teste_restauracao`

Diretriz: backup registra política, execução, artefatos e testes de restauração. Backup comum não deve expor secrets.

### Importação e staging

Tabelas finais de controle:

- `importacao_execucao`
- `importacao_mapeamento`
- `importacao_pendencia`

Tabelas staging planejadas:

- `stg_usuario`
- `stg_anuncio`
- `stg_localidade`
- `stg_midia`
- `stg_story`
- `stg_pagamento`
- `stg_credito`
- `stg_premium`
- `stg_url`

Diretriz: Supabase é apenas origem temporária de importação, não dependência V3. Staging preserva chave de origem, hash de origem, execução, pendências, idempotência, reconciliação e relatório.

### Outbox

Tabela final:

- `outbox_evento`

Diretriz: outbox registra eventos transacionais internos para processamento assíncrono local/futuro, com idempotência e tentativas controladas.

## Separação de responsabilidades

- Tabelas finais representam o estado canônico da V3.
- Tabelas staging recebem dados importados sem promover automaticamente para o domínio final.
- Tabelas de auditoria registram decisões, ações e trilhas operacionais.
- Tabelas de backup registram políticas, execuções, artefatos e restauração.
- Tabelas de importação registram execução, mapeamento, pendência e reconciliação.
- Outbox registra eventos de integração interna, não substitui a fonte de verdade do domínio.

## Extensões PostgreSQL candidatas

| Extensão | Finalidade | Módulos | Fase que precisa | Risco | Alternativa |
| --- | --- | --- | --- | --- | --- |
| `pg_trgm` | Busca textual aproximada, filtros com similaridade e suporte a autocomplete | anúncios, comercial, suporte, importação | Fase 1D ou fase de busca pública | Índices grandes e custo de escrita | FTS puro, normalização adicional e filtros por prefixo |
| `unaccent` | Remover acentos em busca e matching de importação | busca pública, importação, comercial | Fase 1D se aprovado para busca | Pode alterar expectativa linguística e ranking | Normalização na aplicação, campos normalizados dedicados |
| extensão de UUID | Gerar ou validar UUID no banco se a estratégia evoluir | todos os módulos | Fase 1D se o banco passar a gerar UUID | Dependência de extensão específica e compatibilidade | Geração UUID v7 exclusivamente pela aplicação |

## Estratégia de constraints

- Chaves estrangeiras explícitas em todos os vínculos de domínio.
- Exclusão em cascata não é padrão; quando necessária, deve ser justificada antes da migration.
- Enums de negócio usam CHECK constraint ou tabela de catálogo.
- Identificadores naturais usam índices únicos.
- Unicidade dependente de registro ativo usa índice parcial.
- Datas com janela exigem `fim_em` posterior a `inicio_em` quando aplicável.
- Saldos e quantidades financeiras exigem constraints de não negatividade quando aplicável.
- Webhooks, pagamentos, importações e ativações usam idempotency key.

## Estratégia de índices

- Índices únicos para `slug`, `email_normalizado`, `telefone_normalizado` quando aplicável, `txid`, evento de webhook e idempotência financeira.
- Índices por UF/cidade/bairro/categoria para busca pública.
- Índices por status e janela temporal para anúncios, stories, banners, premium, pagamento e outbox.
- Índices de auditoria por ator, recurso, ação e data.
- Índices de importação por execução, origem, hash e status.
- Índices de créditos por usuário e data.
- Índices de SEO por URL canônica, slug e destino de redirect.

## Busca inicial

A busca inicial usa PostgreSQL, combinando FTS e trigram quando aprovados. `documento_busca_anuncio` contém `texto_busca`, localização, categoria, preço, status publicável, mídia válida, benefícios de ranking, `ranking_base` e `atualizado_em`.

Ranking deve ser explicável por sinais documentados: texto, localização, categoria, mídia válida, premium ativo e frescor operacional. Produção não pode usar fallback `findAll()` para busca pública.

## Exclusão lógica e retenção

Exclusão lógica não é padrão. Ela é aceita para histórico público, auditoria, financeiro, moderação, importação, suporte, backup e entidades com necessidade operacional de restauração. Quando não houver justificativa, usar estado de domínio, anonimização ou exclusão física controlada.

## Versionamento e concorrência

Tabelas mutáveis críticas usam `versao`, especialmente `usuario`, `anuncio`, `sessao_usuario`, `saldo_credito_usuario`, `banner`, itens comerciais e entidades com atualização administrativa concorrente. Históricos e razões imutáveis não devem ser reescritos; correções entram por novo evento, estorno, ajuste ou snapshot.

## Preparação 1C.1

A Fase 1C.1 adiciona documentos preparatórios para transformar este blueprint em migrations Flyway na Fase 1D. Essa preparação não cria SQL, não acessa banco e não inicia a Fase 1D.

Entradas preparatórias:

- `docs/v3/28-plano-migrations-flyway.md`
- `docs/v3/29-ordem-migrations-v3.md`
- `docs/v3/30-decisoes-pre-fase-1d.md`
- `docs/v3/31-checklist-pre-fase-1d.md`
