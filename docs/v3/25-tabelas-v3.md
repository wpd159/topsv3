# Tabelas V3

## Escopo

Este documento lista as tabelas planejadas para o banco V3 em nível físico-conceitual. Ele não cria migration, SQL, schema executável, entidade JPA, repository ou service.

A Fase 1C.1 referencia estas tabelas para planejar a ordem de migrations da Fase 1D, sem criar arquivos SQL.

## Convenções de campos

- `id` representa PK interna UUID v7, exceto tabelas associativas ou projeções que justificarem chave composta.
- Campos terminados em `_em` são instantes `timestamptz`, salvo data civil explicitamente documentada.
- Datas civis usam `date`.
- Campos monetários usam `numeric(12,2)` como padrão.
- Créditos usam `integer`; agregados podem usar `bigint`.
- Campos de concorrência usam `versao`.
- Campos normalizados usam lowercase ou E.164 conforme o domínio.
- Campos de exclusão lógica aparecem somente quando houver justificativa.

## Usuários e autenticação

### `usuario`

Campos principais: `id`, `nome`, `email_normalizado`, `telefone_normalizado`, `status`, `tipo_conta`, `email_verificado_em`, `telefone_verificado_em`, `criado_em`, `atualizado_em`, `desativado_em`, `versao`.

Regras: `email_normalizado` é lowercase e único quando preenchido. `telefone_normalizado` usa E.164 quando possível. Duplicidade suspeita entra em pendência de importação.

### `credencial_usuario`

Campos principais: `id`, `usuario_id`, `senha_hash`, `algoritmo`, `alterada_em`, `precisa_redefinir`.

Regras: senha bruta nunca é persistida, logada ou exportada.

### `papel_usuario`

Campos principais: `usuario_id`, `papel`, `criado_por`, `criado_em`.

Regras: papéis iniciais: `ADMIN`, `MODERADOR`, `COMERCIAL`, `USUARIO`.

### `permissao`

Campos principais: `id`, `codigo`, `descricao`.

Regras: `codigo` é identificador natural único.

### `papel_permissao`

Campos principais: `papel`, `permissao_id`, `criado_em`.

Regras: vínculo único por papel e permissão.

### `sessao_usuario`

Campos obrigatórios do modelo:

- `id`
- `usuario_id`
- `token_sessao_hash`
- `dispositivo_hash`
- `ip_criacao`
- `user_agent`
- `criada_em`
- `ultimo_uso_em`
- `expira_inatividade_em`
- `expira_absoluta_em`
- `revogada_em`
- `motivo_revogacao`
- `versao`

Regras: sessão é opaca e server-side. O token bruto fica somente no cookie HttpOnly e em memória durante a requisição. `token_sessao_hash` é único. JWT não é a autenticação principal do navegador.

### `token_seguranca`

Campos principais: `id`, `usuario_id`, `tipo`, `token_hash`, `expira_em`, `tentativas`, `consumido_em`, `criado_em`.

Regras: token por e-mail usa hash, expiração, limite de tentativas e consumo único.

## Localização

### `estado`

Campos principais: `id`, `uf`, `nome`.

Regras: `uf` é único.

### `cidade`

Campos principais: `id`, `estado_id`, `nome`, `nome_normalizado`.

Regras: cidade é única por estado e nome normalizado.

### `bairro`

Campos principais: `id`, `cidade_id`, `nome`, `nome_normalizado`.

Regras: bairro é único por cidade e nome normalizado.

### `anuncio_localizacao`

Campos principais: `anuncio_id`, `estado_id`, `cidade_id`, `bairro_id`, `endereco_resumido`, `latitude`, `longitude`.

Regras: localização pública deve ser minimizada e adequada ao tipo de anúncio.

## Anúncios

### `anuncio`

Campos principais: `id`, `usuario_id`, `slug`, `titulo`, `descricao`, `status`, `categoria`, `preco`, `whatsapp_normalizado`, `publicado_em`, `ultima_publicacao_em`, `criado_em`, `atualizado_em`, `removido_em`, `versao`.

Regras: `slug` é único globalmente para evitar reutilização indevida de URL pública histórica. `preco` usa `numeric(12,2)`. Anúncio removido logicamente só permanece quando houver justificativa operacional, SEO ou auditoria.

### `anuncio_status_historico`

Campos principais: `id`, `anuncio_id`, `status_anterior`, `status_novo`, `motivo`, `ator_usuario_id`, `criado_em`.

Regras: histórico é append-only.

### `documento_busca_anuncio`

Campos principais: `anuncio_id`, `texto_busca`, `estado_id`, `cidade_id`, `bairro_id`, `categoria`, `preco`, `status_publicacao`, `tem_midia_valida`, `beneficios_ranking_json`, `ranking_base`, `atualizado_em`.

Regras: é projeção reconstruível. Produção não pode usar fallback `findAll()` para busca pública.

## Mídia

### `arquivo_midia`

Campos principais: `id`, `storage_provider`, `bucket`, `chave_objeto`, `nome_original`, `mime_type`, `tamanho_bytes`, `largura`, `altura`, `duracao_ms`, `sha256`, `etag`, `status_arquivo`, `criado_em`.

Regras: URL pública é derivada. Placeholder não entra como mídia real.

### `anuncio_midia`

Campos principais: `id`, `anuncio_id`, `arquivo_midia_id`, `tipo`, `finalidade`, `ordem`, `status`, `criado_em`, `atualizado_em`.

Regras: vínculo canônico entre anúncio e arquivo.

### `documento_usuario`

Campos principais: `id`, `usuario_id`, `arquivo_midia_id`, `tipo`, `status`, `politica_retencao`, `retencao_ate`, `criado_em`, `atualizado_em`, `validado_por`, `validado_em`, `removido_em`, `expurgado_em`.

Regras: documento privado não é publicável nem exposto em API pública. `retencao_ate` é nullable; a política de retenção pode ser `ENQUANTO_HOUVER_ANUNCIO`, `DATA_DEFINIDA`, `RETENCAO_JURIDICA` ou `MANUAL`. Quando validado, deve registrar `validado_por` e `validado_em`. Quando removido ou expurgado, deve registrar o marco temporal correspondente. Acesso deve ser auditado e não há expurgo automático nesta fase.

### `documento_usuario_acesso`

Campos principais: `id`, `documento_usuario_id`, `ator_usuario_id`, `finalidade`, `resultado`, `request_id`, `ip_hash`, `user_agent_hash`, `acessado_em`, `criado_em`.

Regras: trilha de auditoria para acesso a documento privado; não armazena conteúdo do documento.

## Stories

### `story_anuncio`

Campos obrigatórios:

- `id`
- `anuncio_midia_id`
- `status`
- `inicio_em`
- `fim_em`
- `ordem`
- `criado_por`
- `criado_em`
- `atualizado_em`

Campos proibidos nesta tabela:

- `anuncio_id`
- `arquivo_midia_id`

Regras: `anuncio_midia_id` é obrigatório e único. Story pertence ao anúncio por meio de `anuncio_midia`; o arquivo vem de `anuncio_midia.arquivo_midia_id`.

## Moderação

### `revisao_anuncio`

Campos principais: `id`, `anuncio_id`, `tipo`, `status`, `payload_solicitado`, `criado_por`, `criado_em`, `finalizado_em`.

### `anuncio_midia_revisao`

Campos principais: `id`, `revisao_anuncio_id`, `anuncio_midia_id`, `arquivo_midia_id`, `acao`, `status`, `ordem`, `motivo`, `criado_em`, `atualizado_em`.

Ações permitidas:

- `ADICIONAR`
- `SUBSTITUIR`
- `REMOVER`
- `REORDENAR`

### `decisao_moderacao`

Campos principais: `id`, `revisao_anuncio_id`, `decisao`, `motivo`, `ator_usuario_id`, `ip`, `criado_em`.

Campo proibido nesta tabela:

- `revisao_id`

Regras: decisão sempre aponta para `revisao_anuncio_id`.

## Premium e créditos

### `beneficio_premium`

Campos principais: `id`, `codigo`, `nome`, `descricao`, `ativo`, `criado_em`.

### `beneficio_premium_opcao`

Campos principais: `id`, `beneficio_id`, `duracao_dias`, `custo_creditos`, `preco_referencia`, `versao_regra`, `ativo`.

### `ativacao_beneficio`

Campos principais: `id`, `beneficio_id`, `opcao_id`, `usuario_id`, `anuncio_id`, `origem`, `ator_usuario_id`, `inicio_em`, `fim_em`, `status`, `custo_creditos_snapshot`, `preco_snapshot`, `idempotency_key`, `revogada_em`, `motivo_revogacao`, `criado_em`.

### `grupo_ativacao_beneficio`

Campos principais: `id`, `tipo`, `origem`, `usuario_id`, `anuncio_id`, `ator_usuario_id`, `campanha_codigo`, `validade_inicio_em`, `validade_fim_em`, `status`, `idempotency_key`, `criado_em`, `atualizado_em`.

Regras: agrupa múltiplas ativações por pacote, campanha, cortesia, admin ou importação.

### `movimento_credito`

Campos principais: `id`, `usuario_id`, `tipo`, `direcao`, `quantidade`, `saldo_antes`, `saldo_depois`, `origem`, `referencia_tipo`, `referencia_id`, `idempotency_key`, `ator_usuario_id`, `observacao`, `criado_em`.

Regras: é a razão de créditos. `direcao` define soma ou subtração e o CHECK de saldo deve impedir divergência matemática.

### `saldo_credito_usuario`

Campos principais: `usuario_id`, `saldo_atual`, `atualizado_em`, `versao`.

Regras: é projeção. Saldo negativo é proibido salvo regra formal documentada.

## Financeiro Efí e legado

### `plano_credito`

Campos principais: `id`, `codigo`, `nome`, `quantidade_creditos`, `valor`, `moeda`, `ativo`, `criado_em`, `atualizado_em`.

### `pagamento`

Campos principais: `id`, `usuario_id`, `plano_credito_id`, `provedor`, `metodo`, `txid`, `identificador_provedor`, `valor`, `moeda`, `quantidade_creditos`, `status_interno`, `status_provedor`, `expiracao_em`, `aprovado_em`, `cancelado_em`, `creditado_em`, `idempotency_key`, `criado_em`, `atualizado_em`.

Valores de `provedor`:

- `EFI`
- `MERCADO_PAGO_LEGADO`
- `OUTRO_LEGADO`
- `DESCONHECIDO`

Regras: Efí é o único provedor ativo inicial. Mercado Pago é histórico/legado, não integração ativa.

### `pagamento_evento`

Campos principais: `id`, `pagamento_id`, `provedor`, `provedor_evento_id`, `tipo_evento`, `payload_hash`, `status_provedor`, `recebido_em`, `processado_em`, `resultado`.

Regras: `provedor_evento_id` é único por provedor, não global.

### `pagamento_webhook`

Campos principais: `id`, `provedor`, `evento_id`, `txid`, `payload_hash`, `origem_ip_hash`, `validacao_resultado`, `recebido_em`, `processado_em`, `resultado`, `erro_resumido`, `tentativas`.

### `pagamento_conciliacao`

Campos principais: `id`, `pagamento_id`, `movimento_credito_id`, `origem`, `status`, `valor_confirmado`, `creditos_confirmados`, `aprovado_em`, `creditado_em`, `criado_em`.

## Métricas, SEO, banners, comercial e suporte

Tabelas finais:

- `evento_visualizacao`
- `agregado_visualizacao_diaria`
- `clique_whatsapp`
- `agregado_clique_whatsapp_diario`
- `seo_url`
- `seo_metadado`
- `seo_redirect`
- `banner_espaco`
- `banner`
- `banner_versao`
- `comercial_contato`
- `comercial_interacao`
- `comercial_status`
- `ticket_suporte`
- `mensagem_suporte`

Regras: esses módulos seguem chaves estrangeiras explícitas, status por CHECK ou catálogo, índices por consulta crítica e retenção conforme auditoria, LGPD, SEO e operação.

## Auditoria, backup e outbox

Tabelas finais:

- `auditoria_evento`
- `backup_politica`
- `backup_execucao`
- `backup_artefato`
- `backup_teste_restauracao`
- `outbox_evento`

Regras: auditoria e backup não armazenam secrets. Outbox usa idempotência e não substitui o estado canônico.

## Importação e staging

Tabelas finais de controle:

- `importacao_execucao`
- `importacao_mapeamento`
- `importacao_pendencia`

Tabelas staging:

- `stg_usuario`
- `stg_anuncio`
- `stg_localidade`
- `stg_midia`
- `stg_story`
- `stg_pagamento`
- `stg_credito`
- `stg_premium`
- `stg_url`

Campos comuns de staging:

- `id`
- `execucao_id`
- `sistema_origem`
- `tabela_origem`
- `id_origem`
- `hash_origem`
- `payload_normalizado_json`
- `status`
- `pendencia_codigo`
- `entidade_v3_id`
- `criado_em`
- `processado_em`

Regras: staging preserva chaves de origem, hash, execução, pendências, idempotência, reconciliação e relatório. Supabase é origem temporária de importação, não dependência V3.
