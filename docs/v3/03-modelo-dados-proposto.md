# Modelo de dados proposto

Este modelo é uma proposta SDD. Não representa migration pronta. Nomes podem ser ajustados antes da implementação, mas as fontes de verdade e invariantes devem ser preservadas.

## Convenções técnicas globais

- PKs internas usam UUID v7 conforme ADR-011, com geração inicial pela aplicação.
- Todos os instantes usam PostgreSQL `timestamptz`.
- Aplicação e banco trabalham em UTC.
- Datas civis sem horário usam `date`.
- Valores monetários usam `numeric(12,2)` como padrão, nunca `float` ou `double`.
- Créditos usam `integer` para quantidades individuais e `bigint` para agregados/relatórios quando necessário.
- Chaves estrangeiras são explícitas.
- Exclusão em cascata não é padrão.
- Enums de negócio recebem CHECK constraint ou tabela de catálogo.
- Índices únicos devem ser definidos para identificadores naturais.
- Índices parciais devem ser usados quando a unicidade depender de registro ativo.
- Tabelas mutáveis críticas devem possuir coluna `versao` para controle de concorrência.
- Operações financeiras e importações usam idempotency key.
- Exclusão lógica somente é usada quando houver justificativa histórica, operacional ou jurídica.
- URL pública não é fonte de verdade da mídia.
- Nomes de tabelas e campos permanecem sem acentos.
- Texto funcional permanece em português do Brasil.
- Dados financeiros têm tolerância zero para divergência.

## Princípios

- Usar PostgreSQL com constraints fortes.
- Preferir IDs internos novos e mapeamento explícito para IDs legados.
- Separar dado público, privado, financeiro e operacional.
- Manter histórico imutável onde houver auditoria, financeiro, premium, moderação, importação e backup.
- Não usar URLs soltas como fonte de verdade de mídia.
- Não corrigir dados importados sem registrar pendência.

## Usuários

### `usuario`

- `id`
- `nome`
- `email_normalizado`
- `telefone_normalizado`
- `status`
- `tipo_conta`
- `email_verificado_em`
- `telefone_verificado_em`
- `criado_em`
- `atualizado_em`
- `desativado_em`
- `versao`

Regras:

- E-mail deve ser armazenado em `email_normalizado` lowercase, com unicidade.
- Telefone deve ser normalizado em E.164 quando possível.
- Duplicidades suspeitas entram no relatório de importação.
- Senha/hash não deve ser exposto em API, log ou export administrativo.

### `credencial_usuario`

- `id`
- `usuario_id`
- `senha_hash`
- `algoritmo`
- `alterada_em`
- `precisa_redefinir`

### `papel_usuario`

- `usuario_id`
- `papel`: `ADMIN`, `MODERADOR`, `COMERCIAL`, `USUARIO`
- `criado_por`
- `criado_em`

### `permissao`

Finalidade: catalogar permissões granulares usadas por backend e admin.

Campos principais:

- `id`
- `codigo`
- `descricao`
- `escopo`
- `ativo`
- `criado_em`
- `atualizado_em`

Chaves estrangeiras: nenhuma obrigatória.

Constraints:

- `codigo` único e imutável após uso.
- `ativo` obrigatório.

Índices:

- índice único em `codigo`.
- índice em `ativo`.

Retenção: manter histórico enquanto houver auditoria ou papel vinculado.

Fonte de verdade: `permissao`.

### `papel_permissao`

Finalidade: vincular papéis a permissões.

Campos principais:

- `papel`
- `permissao_id`
- `criado_por`
- `criado_em`

Chaves estrangeiras:

- `permissao_id` -> `permissao.id`.

Constraints:

- chave única composta por `papel` e `permissao_id`.
- `papel` deve pertencer ao conjunto oficial de papéis.

Índices:

- índice por `papel`.
- índice por `permissao_id`.

Retenção: manter enquanto a permissão existir; alterações relevantes devem ser auditadas.

Fonte de verdade: `papel_permissao`.

### `sessao_usuario`

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

Regras:

- Somente o hash do token da sessão é persistido.
- O token bruto existe apenas no cookie HttpOnly e em memória durante processamento.
- Cookie de sessão usa SameSite=Lax; Secure é obrigatório fora do ambiente local.
- Operações autenticadas que alteram estado exigem CSRF.
- JWT não é autenticação principal do navegador.
- `token_sessao_hash` deve ter índice único.
- Comparação de token deve usar mecanismo resistente a timing attack.
- Revogação deve ser auditável.
- Elevação de privilégio deve rotacionar a sessão.
- Mudança de senha pode revogar todas as sessões.
- Sessões são inicialmente armazenadas no PostgreSQL.

### `token_seguranca`

- `id`
- `usuario_id`
- `finalidade`
- `token_hash`
- `tentativas`
- `max_tentativas`
- `ip_solicitacao`
- `expira_em`
- `consumido_em`
- `criado_em`

Regras:

- Token por e-mail é armazenado somente como hash.
- Token por e-mail exige expiração, limite de tentativas e consumo único.
- Token bruto não entra em log, URL pública persistida ou export administrativo.

## Localização

### `estado`

- `id`
- `uf`
- `nome`
- `slug`
- `codigo_ibge`

### `cidade`

- `id`
- `estado_id`
- `nome`
- `slug`
- `codigo_ibge`
- `ativo_publico`

### `bairro`

- `id`
- `cidade_id`
- `nome`
- `slug`
- `ativo_publico`

### `anuncio_localizacao`

- `id`
- `anuncio_id`
- `estado_id`
- `cidade_id`
- `bairro_id`
- `endereco_publico`
- `latitude_aproximada`
- `longitude_aproximada`

Regras:

- Páginas locais devem ser geradas apenas para localidades publicáveis.
- Cidade ausente em anúncio ativo gera pendência `ANUNCIO_SEM_CIDADE`.

## Anúncios

### `anuncio`

- `id`
- `usuario_id`
- `slug`
- `titulo`
- `descricao`
- `status`: `RASCUNHO`, `PENDENTE`, `APROVADO`, `PAUSADO`, `REJEITADO`, `REMOVIDO`
- `categoria`
- `preco`
- `whatsapp_normalizado`
- `publicado_em`
- `ultima_publicacao_em`
- `criado_em`
- `atualizado_em`
- `removido_em`
- `versao`

Regras:

- `slug` deve ser único para URL pública atual.
- Slug duplicado na importação gera `SLUG_DUPLICADO`.
- Anúncio sem foto real gera `ANUNCIO_SEM_FOTO`.
- Anúncio sem preço, quando obrigatório para a regra comercial, gera `ANUNCIO_SEM_PRECO`.

### `anuncio_status_historico`

- `id`
- `anuncio_id`
- `status_anterior`
- `status_novo`
- `motivo`
- `ator_usuario_id`
- `criado_em`

### `documento_busca_anuncio`

Finalidade: materializar os campos usados pela busca pública, filtros, ordenação e ranking explicável.

Campos principais:

- `anuncio_id`
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

Chaves estrangeiras:

- `anuncio_id` -> `anuncio.id`.
- `estado_id` -> `estado.id`.
- `cidade_id` -> `cidade.id`.
- `bairro_id` -> `bairro.id`.

Constraints:

- um documento por anúncio.
- somente anúncios publicáveis devem ficar elegíveis para busca pública.

Índices:

- índice único em `anuncio_id`.
- índice por `estado_id`, `cidade_id`, `bairro_id`.
- índice FTS/trigram em `texto_busca`.
- índice por `status_publicacao` e `categoria`.

Retenção: projeção recalculável; pode ser reconstruída a partir de `anuncio`, localização, mídia e premium.

Fonte de verdade: `anuncio`, `anuncio_localizacao`, `anuncio_midia` e benefícios premium; `documento_busca_anuncio` é projeção.

Regras de busca:

- A busca inicial usa PostgreSQL FTS e trigram quando as extensões forem aprovadas.
- Ranking deve ser explicável por texto, localização, categoria, mídia válida, premium ativo e frescor.
- Produção não pode usar fallback `findAll()` para busca pública.

## Mídia canônica

### `arquivo_midia`

- `id`
- `storage_provider`
- `bucket`
- `chave_objeto`
- `nome_original`
- `mime_type`
- `tamanho_bytes`
- `largura`
- `altura`
- `duracao_ms`
- `sha256`
- `etag`
- `status_arquivo`: `PENDENTE`, `VALIDO`, `QUEBRADO`, `REMOVIDO`
- `criado_em`

### `anuncio_midia`

- `id`
- `anuncio_id`
- `arquivo_midia_id`
- `tipo`: `FOTO`, `VIDEO`, `STORY`
- `finalidade`: `GALERIA`, `CAPA`, `STORY`, `VERIFICACAO`
- `ordem`
- `publicavel`
- `status_moderacao`
- `origem`
- `criado_em`
- `atualizado_em`

### `documento_usuario`

Finalidade: armazenar referência a documento privado de usuário sem misturar com galeria pública.

Campos principais:

- `id`
- `usuario_id`
- `arquivo_midia_id`
- `tipo_documento`
- `status_validacao`
- `politica_retencao`
- `retencao_ate`
- `validado_por`
- `validado_em`
- `removido_em`
- `expurgado_em`
- `criado_por`
- `criado_em`
- `atualizado_em`

Chaves estrangeiras:

- `usuario_id` -> `usuario.id`.
- `arquivo_midia_id` -> `arquivo_midia.id`.

Constraints:

- `arquivo_midia_id` obrigatório.
- documento privado não pode ser publicável como `anuncio_midia`.
- `retencao_ate` deve ser nullable e não pode ser obrigatório.
- política de retenção deve permitir `ENQUANTO_HOUVER_ANUNCIO`, `DATA_DEFINIDA`, `RETENCAO_JURIDICA` e `MANUAL`.
- quando o documento estiver validado, `validado_por` e `validado_em` devem estar preenchidos.
- quando removido ou expurgado, deve existir `removido_em` ou `expurgado_em` conforme o status.

Índices:

- índice por `usuario_id`.
- índice por `status_validacao`.
- índice por política de retenção e `retencao_ate` quando a data existir.

Retenção: documento pode ser mantido enquanto houver anúncio vinculado ou finalidade operacional legítima. Não há expurgo automático nesta fase. A decisão jurídica final de retenção fica para fase futura; qualquer acesso, remoção, expurgo ou anonimização deve ser auditado.

Fonte de verdade: `documento_usuario` para vínculo privado; `arquivo_midia` para arquivo.

Regras:

- Placeholder não entra em `arquivo_midia`.
- URL pública é derivada de `storage_provider`, `bucket` e `chave_objeto`.
- Mídia quebrada na importação gera `ANUNCIO_COM_MIDIA_QUEBRADA`.

## Stories vinculados ao anúncio

### `story_anuncio`

- `id`
- `anuncio_midia_id`
- `status`
- `inicio_em`
- `fim_em`
- `ordem`
- `criado_por`
- `criado_em`
- `atualizado_em`

Regras:

- `anuncio_midia_id` é obrigatório e único.
- `anuncio_midia.tipo` deve ser `STORY`.
- `anuncio_midia.finalidade` deve ser `STORY`.
- Anúncio e arquivo são obtidos por `anuncio_midia`.
- Story sempre pertence a um anúncio por meio de `anuncio_midia`.
- Story expirado não aparece em listagens públicas.
- Story usa `anuncio_midia.arquivo_midia_id` como fonte de arquivo.
- Não pode existir story sem vínculo canônico.
- Não pode haver duas fontes concorrentes para o mesmo story.
- `story_anuncio` não possui `anuncio_id` nem `arquivo_midia_id`.

## Moderação

### `revisao_anuncio`

- `id`
- `anuncio_id`
- `tipo`
- `status`
- `payload_solicitado`
- `criado_por`
- `criado_em`
- `finalizado_em`

### `anuncio_midia_revisao`

Finalidade: registrar mídias propostas, alteradas ou removidas dentro de uma revisão de anúncio.

Campos principais:

- `id`
- `revisao_anuncio_id`
- `anuncio_midia_id`
- `arquivo_midia_id`
- `acao`: `ADICIONAR`, `SUBSTITUIR`, `REMOVER`, `REORDENAR`
- `status`
- `ordem`
- `motivo`
- `criado_em`
- `atualizado_em`

Chaves estrangeiras:

- `revisao_anuncio_id` -> `revisao_anuncio.id`.
- `anuncio_midia_id` -> `anuncio_midia.id`, quando a ação operar sobre mídia existente.
- `arquivo_midia_id` -> `arquivo_midia.id`, quando a ação adicionar ou substituir arquivo.

Constraints:

- `revisao_anuncio_id` obrigatório.
- ação `ADICIONAR` exige `arquivo_midia_id` e não exige `anuncio_midia_id`.
- ação `SUBSTITUIR` exige `anuncio_midia_id` e `arquivo_midia_id`.
- ação `REMOVER` exige `anuncio_midia_id`.
- ação `REORDENAR` exige `anuncio_midia_id` e `ordem`.
- nenhuma proposta altera diretamente `anuncio_midia` antes da aprovação.

Índices:

- índice por `revisao_anuncio_id`.
- índice por `anuncio_midia_id`.
- índice por `status`.

Retenção: manter junto ao histórico de moderação enquanto houver obrigação de auditoria.

Fonte de verdade: `anuncio_midia_revisao` para proposta; `anuncio_midia` permanece fonte canônica após aprovação.

### `decisao_moderacao`

- `id`
- `revisao_anuncio_id`
- `decisao`: `APROVAR`, `REJEITAR`, `SOLICITAR_AJUSTE`
- `motivo`
- `ator_usuario_id`
- `ip`
- `criado_em`

Chaves estrangeiras:

- `revisao_anuncio_id` -> `revisao_anuncio.id`.

## Benefícios premium

### `beneficio_premium`

- `id`
- `codigo`
- `nome`
- `descricao`
- `ativo`
- `criado_em`

Códigos obrigatórios:

- `ANUNCIO_TOPO`
- `POSICAO_GARANTIDA_TOP20`

### `beneficio_premium_opcao`

- `id`
- `beneficio_id`
- `duracao_dias`
- `custo_creditos`
- `preco_referencia`
- `versao_regra`
- `ativo`

### `ativacao_beneficio`

- `id`
- `beneficio_id`
- `opcao_id`
- `usuario_id`
- `anuncio_id`
- `origem`: `COMPRA`, `CREDITO`, `CORTESIA`, `CAMPANHA`, `ADMIN`, `IMPORTACAO`
- `ator_usuario_id`
- `inicio_em`
- `fim_em`
- `status`
- `custo_creditos_snapshot`
- `preco_snapshot`
- `idempotency_key`
- `revogada_em`
- `motivo_revogacao`
- `criado_em`

Regras:

- Ativação premium inconsistente na importação gera `PREMIUM_INCONSISTENTE`.
- Benefício que afeta ranking deve ser explícito.

## Razão de créditos

### `movimento_credito`

- `id`
- `usuario_id`
- `tipo`: `ENTRADA`, `SAIDA`, `AJUSTE`, `ESTORNO`
- `quantidade`
- `saldo_antes`
- `saldo_depois`
- `origem`
- `referencia_tipo`
- `referencia_id`
- `idempotency_key`
- `ator_usuario_id`
- `observacao`
- `criado_em`

### `saldo_credito_usuario`

- `usuario_id`
- `saldo_atual`
- `atualizado_em`
- `versao`

Regras:

- `movimento_credito` é a fonte contábil.
- `saldo_credito_usuario` é projeção.
- Saldo negativo é proibido, salvo regra formal de crédito aprovado.
- Divergência gera `CREDITO_INCONSISTENTE`.

## Pagamentos

### `plano_credito`

- `id`
- `codigo`
- `nome`
- `quantidade_creditos`
- `valor`
- `moeda`
- `ativo`
- `criado_em`
- `atualizado_em`

### `pagamento`

- `id`
- `usuario_id`
- `plano_credito_id`
- `provedor`: `EFI`, `MERCADO_PAGO_LEGADO`, `OUTRO_LEGADO`, `DESCONHECIDO`
- `metodo`: `PIX`, `LEGADO`, `DESCONHECIDO`
- `txid`
- `identificador_provedor`
- `valor`
- `moeda`
- `quantidade_creditos`
- `status_interno`
- `status_provedor`
- `expiracao_em`
- `aprovado_em`
- `cancelado_em`
- `creditado_em`
- `idempotency_key`
- `criado_em`
- `atualizado_em`

### `pagamento_evento`

- `id`
- `pagamento_id`
- `provedor`
- `provedor_evento_id`
- `tipo_evento`
- `payload_hash`
- `status_provedor`
- `recebido_em`
- `processado_em`
- `resultado`

### `pagamento_webhook`

- `id`
- `provedor`
- `evento_id`
- `txid`
- `payload_hash`
- `origem_ip_hash`
- `validacao_resultado`
- `recebido_em`
- `processado_em`
- `resultado`
- `erro_resumido`
- `tentativas`

### `pagamento_conciliacao`

- `id`
- `pagamento_id`
- `movimento_credito_id`
- `origem`: `WEBHOOK`, `CONSULTA_ATIVA`, `IMPORTACAO`, `AJUSTE_ADMIN`
- `status`
- `valor_confirmado`
- `creditos_confirmados`
- `aprovado_em`
- `creditado_em`
- `criado_em`

Regras:

- `txid`, `idempotency_key` e identificador de evento do provedor, quando disponível, devem ter unicidade.
- A referência entre pagamento aprovado e `movimento_credito` deve ser única.
- Webhook e consulta ativa devem ser idempotentes.
- Pagamento aprovado deve gerar um único movimento de crédito.
- Saldo projetado não prova pagamento.
- O payload bruto do provedor não deve ser armazenado indiscriminadamente; preferir campos normalizados, `payload_hash` e retenção curta/protegida quando indispensável.
- CPF e dados financeiros pessoais devem ser minimizados, mascarados e excluídos de logs.
- Efí é o único provedor ativo inicial.
- Para `EFI`, o método ativo inicial é `PIX`.
- `MERCADO_PAGO_LEGADO`, `OUTRO_LEGADO` e `DESCONHECIDO` existem apenas para histórico, auditoria e reconciliação de importação.
- Mercado Pago não é integração ativa; registros legados devem ser classificados na importação, sem conversão para Efí.

## Métricas de visualização

### `evento_visualizacao`

- `id`
- `anuncio_id`
- `usuario_id`
- `ip_hash`
- `user_agent_hash`
- `origem`
- `janela_deduplicacao`
- `criado_em`

### `agregado_visualizacao_diaria`

- `anuncio_id`
- `data`
- `total_visualizacoes`
- `total_unicas_estimadas`

## Cliques no WhatsApp

### `clique_whatsapp`

- `id`
- `anuncio_id`
- `usuario_id`
- `ip_hash`
- `user_agent_hash`
- `origem`
- `criado_em`

### `agregado_clique_whatsapp_diario`

- `anuncio_id`
- `data`
- `total_cliques`
- `total_unicos_estimados`

## Comercial

### `comercial_contato`

Finalidade: registrar contatos e oportunidades comerciais sem alterar entidades públicas de forma destrutiva.

Campos principais:

- `id`
- `usuario_id`
- `anuncio_id`
- `nome_contato`
- `telefone_normalizado`
- `email_normalizado`
- `status_id`
- `responsavel_usuario_id`
- `origem`
- `observacao_resumida`
- `criado_em`
- `atualizado_em`

Chaves estrangeiras:

- `usuario_id` -> `usuario.id`, quando houver conta vinculada.
- `anuncio_id` -> `anuncio.id`, quando houver anúncio vinculado.
- `status_id` -> `comercial_status.id`.
- `responsavel_usuario_id` -> `usuario.id`.

Constraints:

- ao menos um meio de contato deve existir quando não houver `usuario_id`.
- status obrigatório.

Índices:

- índice por `status_id`.
- índice por `responsavel_usuario_id`.
- índice por telefone/e-mail normalizados.

Retenção: conforme política comercial e LGPD; dados pessoais devem poder ser anonimizados quando aplicável.

Fonte de verdade: `comercial_contato`.

### `comercial_interacao`

Finalidade: registrar interações comerciais, retornos e observações auditáveis.

Campos principais:

- `id`
- `comercial_contato_id`
- `ator_usuario_id`
- `tipo`
- `resumo`
- `proximo_retorno_em`
- `criado_em`

Chaves estrangeiras:

- `comercial_contato_id` -> `comercial_contato.id`.
- `ator_usuario_id` -> `usuario.id`.

Constraints:

- `comercial_contato_id` obrigatório.
- `tipo` obrigatório.

Índices:

- índice por `comercial_contato_id`.
- índice por `ator_usuario_id`.
- índice por `proximo_retorno_em`.

Retenção: manter enquanto houver interesse comercial legítimo ou obrigação de auditoria.

Fonte de verdade: `comercial_interacao`.

### `comercial_status`

Finalidade: controlar etapas do relacionamento comercial.

Campos principais:

- `id`
- `codigo`
- `nome`
- `ordem`
- `ativo`
- `criado_em`
- `atualizado_em`

Chaves estrangeiras: nenhuma obrigatória.

Constraints:

- `codigo` único.
- `ordem` obrigatória.

Índices:

- índice único em `codigo`.
- índice por `ativo` e `ordem`.

Retenção: manter status usados historicamente; inativar em vez de excluir.

Fonte de verdade: `comercial_status`.

## Suporte

### `ticket_suporte`

Finalidade: registrar solicitações de suporte vinculadas a usuário, anúncio, pagamento ou moderação.

Campos principais:

- `id`
- `usuario_id`
- `anuncio_id`
- `pagamento_id`
- `revisao_anuncio_id`
- `status`
- `prioridade`
- `assunto`
- `responsavel_usuario_id`
- `criado_em`
- `atualizado_em`
- `fechado_em`

Chaves estrangeiras:

- `usuario_id` -> `usuario.id`.
- `anuncio_id` -> `anuncio.id`.
- `pagamento_id` -> `pagamento.id`.
- `revisao_anuncio_id` -> `revisao_anuncio.id`.
- `responsavel_usuario_id` -> `usuario.id`.

Constraints:

- `status`, `prioridade` e `assunto` obrigatórios.
- ticket fechado exige `fechado_em`.

Índices:

- índice por `usuario_id`.
- índice por `status` e `prioridade`.
- índice por `responsavel_usuario_id`.

Retenção: conforme política de suporte, LGPD e auditoria financeira/moderação quando aplicável.

Fonte de verdade: `ticket_suporte`.

### `mensagem_suporte`

Finalidade: registrar mensagens do ticket, anexos e histórico de atendimento.

Campos principais:

- `id`
- `ticket_suporte_id`
- `autor_usuario_id`
- `tipo_autor`
- `mensagem`
- `anexo_arquivo_midia_id`
- `criado_em`
- `removido_em`

Chaves estrangeiras:

- `ticket_suporte_id` -> `ticket_suporte.id`.
- `autor_usuario_id` -> `usuario.id`.
- `anexo_arquivo_midia_id` -> `arquivo_midia.id`.

Constraints:

- `ticket_suporte_id` obrigatório.
- mensagem removida deve preservar rastro auditável.

Índices:

- índice por `ticket_suporte_id` e `criado_em`.
- índice por `autor_usuario_id`.

Retenção: seguir retenção do ticket; anexos privados devem respeitar política de documentos.

Fonte de verdade: `mensagem_suporte`.

## SEO

### `seo_url`

- `id`
- `tipo`
- `url_path`
- `canonical_url`
- `entidade_tipo`
- `entidade_id`
- `indexavel`
- `status_http_esperado`
- `inclui_sitemap`
- `ultima_validacao_em`

### `seo_metadado`

Finalidade: centralizar título, descrição, canonical auxiliar, Open Graph e metadados indexáveis por entidade.

Campos principais:

- `id`
- `seo_url_id`
- `entidade_tipo`
- `entidade_id`
- `titulo`
- `descricao`
- `og_titulo`
- `og_descricao`
- `og_imagem_midia_id`
- `json_ld_hash`
- `atualizado_em`

Chaves estrangeiras:

- `seo_url_id` -> `seo_url.id`.
- `og_imagem_midia_id` -> `arquivo_midia.id`, quando aplicável.

Constraints:

- `seo_url_id` único quando o metadado estiver vinculado a URL canônica.
- `titulo` e `descricao` obrigatórios para página indexável.

Índices:

- índice por `seo_url_id`.
- índice por `entidade_tipo` e `entidade_id`.

Retenção: manter enquanto a URL existir; alterações críticas devem gerar auditoria.

Fonte de verdade: `seo_metadado`.

### `seo_redirect`

- `id`
- `origem_path`
- `destino_path`
- `status_code`
- `motivo`
- `ativo`
- `criado_em`

## Banners

### `banner_espaco`

Finalidade: definir slots de banner e dimensões obrigatórias por dispositivo.

Campos principais:

- `id`
- `codigo`
- `nome`
- `largura_desktop`
- `altura_desktop`
- `largura_mobile`
- `altura_mobile`
- `ativo`
- `criado_em`
- `atualizado_em`

Chaves estrangeiras: nenhuma obrigatória.

Constraints:

- `codigo` único.
- dimensões devem ser positivas.

Índices:

- índice único em `codigo`.
- índice por `ativo`.

Retenção: manter enquanto houver banner ou histórico vinculado.

Fonte de verdade: `banner_espaco`.

### `banner`

Finalidade: representar banner administrável publicado ou agendado.

Campos principais:

- `id`
- `banner_espaco_id`
- `titulo`
- `subtitulo`
- `texto_botao`
- `url_destino`
- `alt_text_desktop`
- `alt_text_mobile`
- `arquivo_desktop_id`
- `arquivo_mobile_id`
- `status`
- `inicio_em`
- `fim_em`
- `ordem`
- `versao`
- `criado_por`
- `atualizado_por`
- `criado_em`
- `atualizado_em`

Chaves estrangeiras:

- `banner_espaco_id` -> `banner_espaco.id`.
- `arquivo_desktop_id` -> `arquivo_midia.id`.
- `arquivo_mobile_id` -> `arquivo_midia.id`.
- `criado_por` -> `usuario.id`.
- `atualizado_por` -> `usuario.id`.

Constraints:

- arquivos desktop e mobile obrigatórios para publicação.
- `alt_text_desktop` e `alt_text_mobile` obrigatórios para publicação.
- janela `fim_em` deve ser posterior a `inicio_em`, quando ambas existirem.

Índices:

- índice por `banner_espaco_id` e `status`.
- índice por janela `inicio_em`, `fim_em`.
- índice por `ordem`.

Retenção: manter histórico publicado e versões enquanto houver auditoria operacional.

Fonte de verdade: `banner`.

### `banner_versao`

Finalidade: preservar snapshots de alteração e permitir rollback administrativo.

Campos principais:

- `id`
- `banner_id`
- `snapshot_json`
- `criado_por`
- `criado_em`

Chaves estrangeiras:

- `banner_id` -> `banner.id`.
- `criado_por` -> `usuario.id`.

Constraints:

- `snapshot_json` obrigatório.
- cada snapshot deve apontar para um banner existente.

Índices:

- índice por `banner_id` e `criado_em`.

Retenção: manter pelo período de auditoria de conteúdo e operação.

Fonte de verdade: `banner_versao` para histórico; `banner` para estado atual.

## Auditoria

### `outbox_evento`

Finalidade: registrar eventos transacionais para processamento assíncrono confiável.

Campos principais:

- `id`
- `tipo_evento`
- `agregado_tipo`
- `agregado_id`
- `payload_json`
- `idempotency_key`
- `status`
- `tentativas`
- `proxima_tentativa_em`
- `criado_em`
- `processado_em`
- `erro_resumido`

Chaves estrangeiras: dependem do `agregado_tipo`; a correlação deve ser validada pela camada de domínio.

Constraints:

- `idempotency_key` única por tipo de evento quando aplicável.
- `status` obrigatório.
- `payload_json` não deve conter segredo ou dado pessoal desnecessário.

Índices:

- índice por `status` e `proxima_tentativa_em`.
- índice por `agregado_tipo` e `agregado_id`.
- índice único por `idempotency_key`, quando preenchida.

Retenção: manter eventos processados pelo período operacional definido; falhas devem permanecer até tratamento ou arquivamento auditado.

Fonte de verdade: `outbox_evento` para fila transacional; entidades de domínio continuam sendo a fonte do estado de negócio.

### `auditoria_evento`

- `id`
- `ator_usuario_id`
- `acao`
- `recurso_tipo`
- `recurso_id`
- `resultado`
- `ip`
- `user_agent`
- `antes_hash`
- `depois_hash`
- `metadados_json`
- `criado_em`

Regras:

- Não registrar senha, hash, token ou segredo em `metadados_json`.
- Ações administrativas, financeiras, premium, backup e segurança são auditáveis.

## Backup

### `backup_politica`

- `id`
- `nome`
- `tipo`
- `cron`
- `retencao_dias`
- `destino`
- `ativo`
- `criado_por`

### `backup_execucao`

- `id`
- `politica_id`
- `tipo`
- `status`
- `iniciado_em`
- `finalizado_em`
- `tamanho_total`
- `checksum_manifesto`
- `erro_resumido`
- `solicitado_por`

### `backup_artefato`

- `id`
- `execucao_id`
- `tipo`: `BANCO`, `MIDIA`, `CONFIGURACAO`, `SEO`, `RELEASE`, `LOG`, `MANIFESTO`
- `storage_provider`
- `bucket`
- `chave_objeto`
- `tamanho_bytes`
- `sha256`
- `criptografado`

### `backup_teste_restauracao`

- `id`
- `backup_execucao_id`
- `ambiente_destino`
- `status`
- `validacoes_json`
- `executado_por`
- `iniciado_em`
- `finalizado_em`

## Importação

### `importacao_execucao`

- `id`
- `nome`
- `snapshot_ref`
- `status`
- `iniciado_em`
- `finalizado_em`
- `relatorio_ref`
- `criado_por`

### `importacao_mapeamento`

- `id`
- `execucao_id`
- `sistema_origem`
- `tabela_origem`
- `id_origem`
- `entidade_v3_tipo`
- `entidade_v3_id`
- `hash_origem`
- `status`

### `importacao_pendencia`

- `id`
- `execucao_id`
- `codigo`
- `entidade_origem_tipo`
- `entidade_origem_id`
- `severidade`
- `descricao`
- `resolvido_em`
- `resolvido_por`

Códigos obrigatórios estão definidos no documento `05-importador-saneador.md`.

## Staging de importação

As tabelas staging não são fonte canônica da V3. Elas isolam dados importados até validação, reconciliação e promoção controlada.

Tabelas planejadas:

- `stg_usuario`
- `stg_anuncio`
- `stg_localidade`
- `stg_midia`
- `stg_story`
- `stg_pagamento`
- `stg_credito`
- `stg_premium`
- `stg_url`

Campos comuns:

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

Regras:

- Supabase é origem temporária de importação, não dependência V3.
- Cada registro staging preserva chave de origem, hash de origem e execução.
- Importação deve ser idempotente por execução e origem.
- Pendências impedem promoção automática.
- Reconciliação financeira é obrigatória para pagamentos, créditos e premium.
- Relatório separa importados, pendentes, rejeitados e reconciliados.
