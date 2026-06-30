# Matriz de tabelas por migration

## Escopo

Esta matriz registra a cobertura física gerada na Fase 1D para auditoria. Ela não aprova aplicação das migrations e não substitui revisão Pro.

## Totais

- Migrations SQL: 17.
- Tabelas planejadas/criadas nas migrations: 68.
- Extensões candidatas: 2.
- Migrations aplicadas: 0.
- Bancos acessados: 0.

## Matriz

| Migration | Objetos principais | Total de tabelas | Revisão extra |
| --- | --- | ---: | --- |
| `V001__extensoes_postgresql.sql` | `pg_trgm`, `unaccent` | 0 | Infraestrutura e busca. |
| `V002__usuarios_autenticacao.sql` | `usuario`, `credencial_usuario`, `papel_usuario`, `permissao`, `papel_permissao`, `sessao_usuario`, `token_seguranca` | 7 | Segurança e RBAC futuro. |
| `V003__localizacao.sql` | `estado`, `cidade`, `bairro`, `anuncio_localizacao` | 4 | SEO local e importação. |
| `V004__anuncios.sql` | `anuncio`, `anuncio_status_historico`, `documento_busca_anuncio` | 3 | SEO, moderação e busca. |
| `V005__midia_stories_documentos.sql` | `arquivo_midia`, `anuncio_midia`, `documento_usuario`, `documento_usuario_acesso`, `story_anuncio` | 5 | Mídia, privacidade, retenção e storage. |
| `V006__moderacao.sql` | `revisao_anuncio`, `anuncio_midia_revisao`, `decisao_moderacao` | 3 | Moderação e classificação de conteúdo. |
| `V007__premium_creditos.sql` | `beneficio_premium`, `beneficio_premium_opcao`, `grupo_ativacao_beneficio`, `ativacao_beneficio`, `movimento_credito`, `saldo_credito_usuario` | 6 | Financeiro, Premium, pacotes/campanhas e créditos. |
| `V008__financeiro_efi_historico_legado.sql` | `plano_credito`, `pagamento`, `pagamento_evento`, `pagamento_webhook`, `pagamento_conciliacao` | 5 | Pix/Efí modelado, histórico legado e conciliação. |
| `V009__metricas.sql` | `evento_visualizacao`, `agregado_visualizacao_diaria`, `clique_whatsapp`, `agregado_clique_whatsapp_diario`, `evento_verificacao_etaria` | 5 | Privacidade, métricas e age gate. |
| `V010__seo_urls_redirects.sql` | `seo_url`, `seo_metadado`, `seo_redirect`, `seo_conteudo_pagina` | 4 | SEO, rotas públicas e conteúdo institucional. |
| `V011__banners.sql` | `banner_espaco`, `banner`, `banner_versao` | 3 | Banners, mídia e rollback futuro. |
| `V012__comercial_suporte.sql` | `comercial_status`, `comercial_contato`, `comercial_interacao`, `ticket_suporte`, `mensagem_suporte` | 5 | Aquisição, atendimento e LGPD. |
| `V013__auditoria_outbox.sql` | `auditoria_evento`, `outbox_evento` | 2 | Auditoria, observabilidade e eventos técnicos. |
| `V014__backup.sql` | `backup_politica`, `backup_execucao`, `backup_artefato`, `backup_teste_restauracao` | 4 | Operação, restore e retenção. |
| `V015__importacao_staging.sql` | `importacao_execucao`, `importacao_mapeamento`, `importacao_pendencia`, `stg_usuario`, `stg_anuncio`, `stg_localidade`, `stg_midia`, `stg_story`, `stg_pagamento`, `stg_credito`, `stg_premium`, `stg_url` | 12 | Importação futura; não executa ETL. |
| `V016__indices_busca.sql` | Índices de busca, filtros, trigram e FTS | 0 | Performance, custo e locks. |
| `V017__constraints_finais.sql` | FKs e constraints finais dependentes | 0 | Integridade cruzada e rotas proibidas. |

## Pontos de preservação

- `/anuncios/[slug]` permanece o contrato público da página de anúncio.
- Slug público de anúncio é globalmente único nesta revisão de auditoria.
- `/acompanhantes/[uf]/[cidade]` e `/acompanhantes/[uf]/[cidade]/[bairro]` são suportadas por localização normalizada.
- Consistência de localização é reforçada por FKs compostas em anúncio e documento de busca.
- Métricas de visualização e clique WhatsApp foram modeladas como base preservável/migrável/reimplementável.
- Agregados de métricas aceitam origem desconhecida por chaves normalizadas, sem forçar origem nula em PK composta.
- Documento privado possui retenção nullable por política, validação explícita, remoção/expurgo registrados e acesso auditável.
- Premium foi modelado como benefício aditivo, sem redução do gratuito por limite diário de contato.
- Ativações Premium podem ser agrupadas por pacote, campanha ou cortesia sem regra funcional nesta fase.
- Pix/Efí foi modelado apenas como estrutura futura, sem integração real.
- Staging de importação é explícito por tabela e não promove dado automaticamente para tabelas finais.
- A Fase 2A criou apenas estrutura Java local para importador saneador, sem alterar migrations, sem SQL novo e sem acessar banco.
