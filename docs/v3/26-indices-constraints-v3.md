# Índices e constraints V3

## Escopo

Este documento define a estratégia de índices, constraints, extensões e busca para a Fase 1C. Ele não contém SQL executável e não antecipa migrations.

## Estratégia geral de constraints

- Toda chave estrangeira relevante é explícita.
- Exclusão em cascata não é padrão.
- Enums de negócio usam CHECK constraint ou tabela de catálogo.
- Identificadores naturais recebem unicidade.
- Unicidade dependente de registro ativo usa índice parcial.
- Tabelas mutáveis críticas usam `versao`.
- Operações financeiras e importações usam idempotency key.
- Valores monetários usam `numeric`, nunca `float` ou `double`.
- Créditos usam inteiro.
- Dados financeiros têm tolerância zero para divergência.

## Constraints críticas

### Usuários e autenticação

- `usuario.email_normalizado` único quando preenchido.
- `usuario.telefone_normalizado` único quando aplicável e confiável.
- `sessao_usuario.token_sessao_hash` único.
- `sessao_usuario.expira_absoluta_em` posterior a `criada_em`.
- `sessao_usuario.expira_inatividade_em` posterior a `criada_em`.
- `token_seguranca.token_hash` único.
- Token de e-mail exige expiração, limite de tentativas e consumo único.

### Anúncios, mídia e stories

- `anuncio.slug` único globalmente para evitar reutilização indevida de URL pública histórica.
- `anuncio.preco` não negativo quando preenchido.
- `arquivo_midia.sha256` indexado para deduplicação e verificação.
- `anuncio_midia` não deve apontar para placeholder.
- `story_anuncio.anuncio_midia_id` obrigatório e único.
- `story_anuncio.fim_em` posterior a `inicio_em` quando ambas existirem.
- `banner.fim_em` posterior a `inicio_em` quando ambas existirem.

### Moderação

- `decisao_moderacao.revisao_anuncio_id` obrigatório.
- `anuncio_midia_revisao.acao` aceita somente `ADICIONAR`, `SUBSTITUIR`, `REMOVER`, `REORDENAR`.
- `ADICIONAR` exige `arquivo_midia_id` e não exige `anuncio_midia_id`.
- `SUBSTITUIR` exige `anuncio_midia_id` e `arquivo_midia_id`.
- `REMOVER` exige `anuncio_midia_id`.
- `REORDENAR` exige `anuncio_midia_id` e `ordem`.

### Créditos e financeiro

- `movimento_credito.quantidade` positivo conforme o tipo de movimento.
- `movimento_credito.saldo_depois` não negativo salvo regra formal aprovada.
- `movimento_credito.direcao` deve tornar `saldo_depois` matematicamente coerente com `saldo_antes` e `quantidade`.
- `saldo_credito_usuario.saldo_atual` não negativo salvo regra formal aprovada.
- `movimento_credito.idempotency_key` único quando preenchido.
- `ativacao_beneficio.idempotency_key` único quando preenchido.
- `pagamento.idempotency_key` único quando preenchido.
- `pagamento.txid` único quando preenchido.
- `pagamento.identificador_provedor` único por provedor quando preenchido.
- `pagamento_webhook.evento_id` único por provedor quando preenchido.
- `pagamento_evento.provedor_evento_id` único por provedor quando preenchido.
- `pagamento_webhook.payload_hash` indexado para deduplicação e auditoria.
- `pagamento_conciliacao.pagamento_id` e `pagamento_conciliacao.movimento_credito_id` formam vínculo único para pagamento aprovado.
- `pagamento.provedor` aceita `EFI`, `MERCADO_PAGO_LEGADO`, `OUTRO_LEGADO`, `DESCONHECIDO`.
- Apenas `EFI` é provedor ativo inicial.

### SEO, importação, auditoria e outbox

- `seo_url.canonical_path` único e compatível com `/sitemap.xml` e `/robots.txt`.
- `seo_conteudo_pagina` permite apenas uma versão `APROVADO` ou `PUBLICADO` por URL e chave.
- `seo_redirect.origem` única enquanto ativo.
- `importacao_mapeamento` único por execução, sistema, tabela e id de origem.
- `importacao_mapeamento.hash_origem` indexado por execução.
- `importacao_pendencia` indexada por execução, código, severidade e status.
- `auditoria_evento` não armazena segredo, token ou senha em metadados.
- `outbox_evento.idempotency_key` único quando preenchido.

## Estratégia de índices

### Busca pública

- Índice por `documento_busca_anuncio.estado_id`, `cidade_id`, `bairro_id`.
- Índice por `documento_busca_anuncio.categoria`.
- Índice composto por localização, categoria e status publicável.
- Índice de FTS em `documento_busca_anuncio.texto_busca`.
- Índice trigram em campos textuais aprovados para similaridade.
- Índice por `ranking_base` e sinais de ordenação quando necessário.

### Usuários

- Índice único em `email_normalizado`.
- Índice único ou parcial em `telefone_normalizado`, quando aplicável.
- Índice por `status`.
- Índice por `criado_em`.

### Anúncios

- Índice único em `slug`.
- Índice por `usuario_id`.
- Índice por `status`, `categoria`, `publicado_em`.
- Índice por `removido_em` quando houver exclusão lógica.

### Mídia e stories

- Índice por `arquivo_midia.storage_provider`, `bucket`, `chave_objeto`.
- Índice por `arquivo_midia.sha256`.
- Índice por `anuncio_midia.anuncio_id`, `tipo`, `finalidade`, `ordem`.
- Índice único em `story_anuncio.anuncio_midia_id`.
- Índice por `story_anuncio.status`, `inicio_em`, `fim_em`, `ordem`.

### Financeiro e créditos

- Índice por `pagamento.usuario_id`.
- Índice por `pagamento.status_interno`, `provedor`, `criado_em`.
- Índice único em `pagamento.txid`, quando preenchido.
- Índice único de idempotência de pagamento.
- Índice por `pagamento_webhook.provedor`, `evento_id`, `txid`.
- Índice por `movimento_credito.usuario_id`, `criado_em`.
- Índice por `saldo_credito_usuario.usuario_id`.

### Auditoria, suporte, comercial, backup e outbox

- Índice por `auditoria_evento.ator_usuario_id`, `criado_em`.
- Índice por `auditoria_evento.recurso_tipo`, `recurso_id`.
- Índice por `ticket_suporte.status`, `prioridade`, `responsavel_usuario_id`.
- Índice por `comercial_contato.status_id` e `responsavel_usuario_id`.
- Índice por `backup_execucao.status`, `iniciado_em`.
- Índice por `outbox_evento.status`, `proxima_tentativa_em`.

### Importação e staging

- Índice por `execucao_id` em todas as tabelas staging.
- Índice por `sistema_origem`, `tabela_origem`, `id_origem`.
- Índice por `hash_origem`.
- Índice por `status` e `pendencia_codigo`.
- Índice por `entidade_v3_id` quando houver promoção para tabela final.

## Extensões PostgreSQL candidatas

| Extensão | Finalidade | Módulo que usa | Fase que precisa | Risco | Alternativa |
| --- | --- | --- | --- | --- | --- |
| `pg_trgm` | Similaridade textual e autocomplete | busca, comercial, importação | Fase 1D se busca for migrada junto | Aumento de tamanho de índice e custo de escrita | FTS puro e filtros prefixados |
| `unaccent` | Normalização textual de busca sem acento | busca e importação | Fase 1D se aprovado | Ranking pode ficar menos previsível | Normalização na aplicação e campos dedicados |
| extensão de UUID | Apoio futuro a geração/validação UUID | todos | Fase 1D se banco gerar UUID | Dependência operacional de extensão | UUID v7 gerado apenas na aplicação |

## Busca com PostgreSQL

### Documento de busca

`documento_busca_anuncio` concentra:

- `texto_busca`
- `estado_id`
- `cidade_id`
- `bairro_id`
- `categoria`
- `preco`
- `status_publicacao`
- `tem_midia_valida`
- `beneficios_ranking_json`
- `ranking_base`
- `atualizado_em`

### Estratégia

- FTS para termos principais e ordenação por relevância.
- Trigram para similaridade, erros de digitação e autocomplete quando `pg_trgm` for aprovado.
- `unaccent` somente se a decisão de busca aprovar normalização sem acento no banco.
- Filtros obrigatórios por UF, cidade, bairro, categoria, status publicável e mídia válida.
- Ranking explicável por texto, localização, categoria, mídia válida, premium ativo e frescor.
- Produção não pode usar fallback `findAll()` para busca pública.

## Staging e importação

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

Cada tabela staging deve possuir chaves de origem, hash de origem, execução de importação, status, pendência, vínculo opcional com entidade V3 e datas de criação/processamento.

Regras:

- importação é idempotente por execução e chave de origem;
- hash de origem detecta divergência entre reprocessamentos;
- pendências impedem promoção automática;
- reconciliação financeira é obrigatória para pagamentos, créditos e premium;
- relatório final lista importados, pendentes, rejeitados e reconciliados.

## Bloqueios para a Fase 1D

- Confirmar se `pg_trgm` será habilitado na migration inicial.
- Confirmar se `unaccent` será habilitado ou substituído por normalização na aplicação.
- Confirmar a estratégia prática de UUID v7 para Java 17 e PostgreSQL.
- Revisar nomes finais de enums que virarão CHECK ou catálogo.
- Revisar tamanho e seletividade dos índices de busca antes de criar migrations.

Esses bloqueios são registrados como decisões pré-1D em `docs/v3/30-decisoes-pre-fase-1d.md`.
