# Ordem planejada das migrations V3

## Escopo

Este documento lista a ordem planejada das migrations da Fase 1D. Na Fase 1C.1, a lista era apenas planejamento. Na Fase 1D, a ordem foi materializada localmente para auditoria, sem aplicação em banco.

## Ordem planejada

| Ordem | Nome planejado | Objetivo | Tabelas afetadas | Dependências anteriores | Riscos | Validações esperadas | Cria extensão | Cria tabela | Cria índice | Cria constraint | ADR pendente | Revisão extra |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `V001__extensoes_postgresql.sql` | Preparar extensões aprovadas para UUID/busca | nenhuma tabela final | nenhuma | habilitar extensão desnecessária ou indisponível | extensão aprovada existe ou alternativa documentada | sim | não | não | não | sim, detalhes técnicos de UUID/`pg_trgm`/`unaccent` | infraestrutura |
| 2 | `V002__usuarios_autenticacao.sql` | Criar base de usuários, credenciais, papéis, permissões, sessões e tokens | `usuario`, `credencial_usuario`, `papel_usuario`, `permissao`, `papel_permissao`, `sessao_usuario`, `token_seguranca` | V001 | autenticação e dados pessoais | unicidade de e-mail, sessão opaca, token hash, `versao` | não | sim | sim | sim | não | segurança |
| 3 | `V003__localizacao.sql` | Criar catálogo de UF/cidade/bairro e vínculo de localização | `estado`, `cidade`, `bairro`, `anuncio_localizacao` | V002 | cardinalidade e normalização | FKs explícitas e unicidade por nomes normalizados | não | sim | sim | sim | não | dados/importação |
| 4 | `V004__anuncios.sql` | Criar anúncio, status histórico e documento de busca base | `anuncio`, `anuncio_status_historico`, `documento_busca_anuncio` | V002, V003 | SEO, slug e busca | slug único, status, localização e `versao` | não | sim | sim | sim | não | SEO/busca |
| 5 | `V005__midia_stories_documentos.sql` | Criar mídia canônica, vínculo de mídia, documentos privados, auditoria de acesso e stories | `arquivo_midia`, `anuncio_midia`, `documento_usuario`, `documento_usuario_acesso`, `story_anuncio` | V002, V004 | fonte concorrente de mídia, documento publicável ou story | `story_anuncio.anuncio_midia_id` único; documento privado sem publicação e com acesso auditável | não | sim | sim | sim | não | mídia/privacidade |
| 6 | `V006__moderacao.sql` | Criar revisão e decisão de moderação | `revisao_anuncio`, `anuncio_midia_revisao`, `decisao_moderacao` | V002, V004, V005 | ações de mídia inconsistentes | ações permitidas e FKs corretas para `revisao_anuncio_id` | não | sim | sim | sim | não | moderação |
| 7 | `V007__premium_creditos.sql` | Criar benefícios premium, grupos de ativação, ativações e razão/projeção de créditos | `beneficio_premium`, `beneficio_premium_opcao`, `grupo_ativacao_beneficio`, `ativacao_beneficio`, `movimento_credito`, `saldo_credito_usuario` | V002, V004 | divergência financeira | saldo coerente por direção, idempotência e vínculo auditável | não | sim | sim | sim | não | financeiro |
| 8 | `V008__financeiro_efi_historico_legado.sql` | Criar planos, pagamentos, eventos, webhooks e conciliação | `plano_credito`, `pagamento`, `pagamento_evento`, `pagamento_webhook`, `pagamento_conciliacao` | V002, V007 | pagamento/crédito duplicado | `txid`, webhook, evento por provedor e idempotency key únicos; Efí exige Pix | não | sim | sim | sim | não | financeiro/Efí mock |
| 9 | `V009__metricas.sql` | Criar eventos e agregados de visualização/clique | `evento_visualizacao`, `agregado_visualizacao_diaria`, `clique_whatsapp`, `agregado_clique_whatsapp_diario` | V004 | dado pessoal em métricas | hashes/minimização, origem desconhecida normalizada e índices por data | não | sim | sim | sim | não | privacidade |
| 10 | `V010__seo_urls_redirects.sql` | Criar URLs canônicas, metadados, redirects e conteúdo SEO | `seo_url`, `seo_metadado`, `seo_redirect`, `seo_conteudo_pagina` | V004 | quebra de SEO/canonical | unicidade de URL, redirects ativos, sitemap/robots e versão publicada única | não | sim | sim | sim | não | SEO |
| 11 | `V011__banners.sql` | Criar espaços, banners e snapshots | `banner_espaco`, `banner`, `banner_versao` | V002, V005 | banner sem mídia real em publicação | rascunho sem mídia permitido; agendado/publicado exige mídia desktop/mobile e alt text | não | sim | sim | sim | não | mídia/admin |
| 12 | `V012__comercial_suporte.sql` | Criar pipeline comercial e suporte | `comercial_contato`, `comercial_interacao`, `comercial_status`, `ticket_suporte`, `mensagem_suporte` | V002, V004, V008 | retenção e dados pessoais | FKs opcionais válidas e índices de atendimento | não | sim | sim | sim | não | LGPD/suporte |
| 13 | `V013__auditoria_outbox.sql` | Criar auditoria e outbox transacional | `auditoria_evento`, `outbox_evento` | V002 | segredo em auditoria ou evento duplicado | idempotência e ausência de payload sensível | não | sim | sim | sim | não | segurança/operação |
| 14 | `V014__backup.sql` | Criar controle de políticas, execuções, artefatos e testes de restauração | `backup_politica`, `backup_execucao`, `backup_artefato`, `backup_teste_restauracao` | V002 | backup expor segredo | artefatos com hash, retenção e criptografia sinalizada | não | sim | sim | sim | não | operação |
| 15 | `V015__importacao_staging.sql` | Criar execução, mapeamento, pendências e staging | `importacao_execucao`, `importacao_mapeamento`, `importacao_pendencia`, `stg_usuario`, `stg_anuncio`, `stg_localidade`, `stg_midia`, `stg_story`, `stg_pagamento`, `stg_credito`, `stg_premium`, `stg_url` | V002 a V014 | promover dado ruim ao domínio | chaves de origem, hash, status, pendências e idempotência | não | sim | sim | sim | não | importação/financeiro |
| 16 | `V016__indices_busca.sql` | Criar índices pesados de busca, FTS e trigram aprovados | `documento_busca_anuncio` e campos textuais aprovados | V001, V004, V015 | índice grande, lock ou plano ruim | plano local, tamanho de índice e consultas por filtro | depende de `pg_trgm`/`unaccent` | não | sim | não | sim | busca/performance |
| 17 | `V017__constraints_finais.sql` | Adicionar constraints finais que dependem de todas as tabelas | todas as tabelas críticas | V001 a V016 | constraint quebrar importação/staging inicial | FKs, CHECKs, parciais e unicidades revisadas | não | não | pode criar | sim | sim, CHECK versus catálogo | arquitetura/dados |

## Regras desta lista

- A ordem acima foi usada para criar `V001` a `V017` em `backend/src/main/resources/db/migration`.
- A materialização é local e permanece com status `AGUARDANDO_REVISAO_PRO`.
- Flyway não foi executado e nenhuma migration foi aplicada.
- A revisão Pro deve aprovar cada item antes de qualquer execução em banco local descartável.
- Dependências podem ser ajustadas na Fase 1D se a revisão técnica justificar.
- Migrations financeiras, autenticação, mídia, SEO, importação e busca exigem revisão extra.
- Decisões materializadas por prudência estão registradas em `docs/v3/61-pendencias-e-decisoes-schema-1d.md` e continuam pendentes de auditoria Pro.
