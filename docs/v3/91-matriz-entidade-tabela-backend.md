# Matriz entidade-tabela do backend

## Resumo

Migrations auditadas: `V001` a `V017`.

Contagem:

- tabelas nas migrations: 68;
- records diretos criados: 52;
- tabelas `stg_*` cobertas por referencia estrutural: 9;
- tabelas pendentes de record direto: 7.

Como nao ha JPA/Spring Data JPA local, a coluna "Classe" representa record Java puro, nao entidade JPA anotada.

## Matriz

| Tabela | Migration | Classe/referencia | Status |
| --- | --- | --- | --- |
| `usuario` | `V002` | `domain.usuario.Usuario` | Record direto |
| `credencial_usuario` | `V002` | - | Pendente |
| `papel_usuario` | `V002` | `domain.usuario.PapelUsuario` | Record direto |
| `permissao` | `V002` | - | Pendente |
| `papel_permissao` | `V002` | - | Pendente |
| `sessao_usuario` | `V002` | `domain.usuario.SessaoUsuario` | Record direto |
| `token_seguranca` | `V002` | `domain.usuario.TokenSeguranca` | Record direto |
| `estado` | `V003` | `domain.localizacao.Estado` | Record direto |
| `cidade` | `V003` | `domain.localizacao.Cidade` | Record direto |
| `bairro` | `V003` | `domain.localizacao.Bairro` | Record direto |
| `anuncio_localizacao` | `V003` | `domain.localizacao.AnuncioLocalizacao` | Record direto |
| `anuncio` | `V004` | `domain.anuncio.Anuncio` | Record direto |
| `anuncio_status_historico` | `V004` | `domain.anuncio.AnuncioStatusHistorico` | Record direto |
| `documento_busca_anuncio` | `V004` | `domain.anuncio.DocumentoBuscaAnuncio` | Record direto |
| `arquivo_midia` | `V005` | `domain.midia.ArquivoMidia` | Record direto |
| `anuncio_midia` | `V005` | `domain.midia.AnuncioMidia` | Record direto |
| `documento_usuario` | `V005` | `domain.documento.DocumentoUsuario` | Record direto |
| `documento_usuario_acesso` | `V005` | `domain.documento.DocumentoUsuarioAcesso` | Record direto |
| `story_anuncio` | `V005` | `domain.midia.StoryAnuncio` | Record direto |
| `revisao_anuncio` | `V006` | `domain.moderacao.RevisaoAnuncio` | Record direto |
| `anuncio_midia_revisao` | `V006` | `domain.moderacao.AnuncioMidiaRevisao` | Record direto |
| `decisao_moderacao` | `V006` | `domain.moderacao.DecisaoModeracao` | Record direto |
| `beneficio_premium` | `V007` | `domain.premium.BeneficioPremium` | Record direto |
| `beneficio_premium_opcao` | `V007` | `domain.premium.BeneficioPremiumOpcao` | Record direto |
| `grupo_ativacao_beneficio` | `V007` | `domain.premium.GrupoAtivacaoBeneficio` | Record direto |
| `ativacao_beneficio` | `V007` | `domain.premium.AtivacaoBeneficio` | Record direto |
| `movimento_credito` | `V007` | `domain.credito.MovimentoCredito` | Record direto |
| `saldo_credito_usuario` | `V007` | `domain.credito.SaldoCreditoUsuario` | Record direto |
| `plano_credito` | `V008` | `domain.financeiro.PlanoCredito` | Record direto |
| `pagamento` | `V008` | `domain.financeiro.Pagamento` | Record direto |
| `pagamento_evento` | `V008` | `domain.financeiro.PagamentoEvento` | Record direto |
| `pagamento_webhook` | `V008` | `domain.financeiro.PagamentoWebhook` | Record direto |
| `pagamento_conciliacao` | `V008` | `domain.financeiro.PagamentoConciliacao` | Record direto |
| `evento_visualizacao` | `V009` | `domain.metrica.EventoVisualizacao` | Record direto |
| `agregado_visualizacao_diaria` | `V009` | `domain.metrica.AgregadoVisualizacaoDiaria` | Record direto |
| `clique_whatsapp` | `V009` | `domain.metrica.CliqueWhatsapp` | Record direto |
| `agregado_clique_whatsapp_diario` | `V009` | `domain.metrica.AgregadoCliqueWhatsappDiario` | Record direto |
| `evento_verificacao_etaria` | `V009` | `domain.metrica.EventoVerificacaoEtaria` | Record direto |
| `seo_url` | `V010` | `domain.seo.SeoUrl` | Record direto |
| `seo_metadado` | `V010` | `domain.seo.SeoMetadado` | Record direto |
| `seo_redirect` | `V010` | `domain.seo.SeoRedirect` | Record direto |
| `seo_conteudo_pagina` | `V010` | `domain.seo.SeoConteudoPagina` | Record direto |
| `banner_espaco` | `V011` | `domain.banner.BannerEspaco` | Record direto |
| `banner` | `V011` | `domain.banner.Banner` | Record direto |
| `banner_versao` | `V011` | `domain.banner.BannerVersao` | Record direto |
| `comercial_status` | `V012` | `domain.comercial.ComercialStatus` | Record direto |
| `comercial_contato` | `V012` | `domain.comercial.ComercialContato` | Record direto |
| `comercial_interacao` | `V012` | `domain.comercial.ComercialInteracao` | Record direto |
| `ticket_suporte` | `V012` | `domain.suporte.TicketSuporte` | Record direto |
| `mensagem_suporte` | `V012` | `domain.suporte.MensagemSuporte` | Record direto |
| `auditoria_evento` | `V013` | `domain.auditoria.AuditoriaEvento` | Record direto |
| `outbox_evento` | `V013` | `domain.auditoria.OutboxEvento` | Record direto |
| `backup_politica` | `V014` | - | Pendente |
| `backup_execucao` | `V014` | - | Pendente |
| `backup_artefato` | `V014` | - | Pendente |
| `backup_teste_restauracao` | `V014` | - | Pendente |
| `importacao_execucao` | `V015` | `domain.importacao.ImportacaoExecucao` | Record direto |
| `importacao_mapeamento` | `V015` | `domain.importacao.ImportacaoMapeamento` | Record direto |
| `importacao_pendencia` | `V015` | `domain.importacao.ImportacaoPendencia` | Record direto |
| `stg_usuario` | `V015` | `domain.importacao.StagingImportacaoReferencia` | Referencia estrutural |
| `stg_anuncio` | `V015` | `domain.importacao.StagingImportacaoReferencia` | Referencia estrutural |
| `stg_localidade` | `V015` | `domain.importacao.StagingImportacaoReferencia` | Referencia estrutural |
| `stg_midia` | `V015` | `domain.importacao.StagingImportacaoReferencia` | Referencia estrutural |
| `stg_story` | `V015` | `domain.importacao.StagingImportacaoReferencia` | Referencia estrutural |
| `stg_pagamento` | `V015` | `domain.importacao.StagingImportacaoReferencia` | Referencia estrutural |
| `stg_credito` | `V015` | `domain.importacao.StagingImportacaoReferencia` | Referencia estrutural |
| `stg_premium` | `V015` | `domain.importacao.StagingImportacaoReferencia` | Referencia estrutural |
| `stg_url` | `V015` | `domain.importacao.StagingImportacaoReferencia` | Referencia estrutural |

## Enums por pacote

| Pacote | Arquivo | Escopo |
| --- | --- | --- |
| `shared` | `ClassificacaoConteudo` | classificacao binaria `LIVRE`/`BLOQUEADO` de anuncio e midia |
| `usuario` | `UsuarioTipos` | status, tipo de conta, papel e tipo de token |
| `anuncio` | `AnuncioTipos` | status do anuncio, moderacao e publicacao de busca |
| `midia` | `MidiaTipos` | arquivo, tipo/finalidade/status de midia e story |
| `documento` | `DocumentoTipos` | tipo/status/politica de retencao e acesso auditado |
| `moderacao` | `ModeracaoTipos` | revisao, acao de midia e decisao |
| `premium` | `PremiumTipos` | escopo, origem e status de beneficio |
| `credito` | `CreditoTipos` | tipo, direcao e origem do movimento |
| `financeiro` | `FinanceiroTipos` | provedor, metodo, status, webhook e conciliacao |
| `metrica` | `MetricaTipos` | dispositivo e verificacao etaria |
| `seo` | `SeoTipos` | URL, robots, metadado e conteudo |
| `banner` | `BannerTipos` | status de banner |
| `auditoria` | `AuditoriaTipos` | origem, resultado e outbox |
| `comercial` | `ComercialTipos` | origem de contato e tipo de interacao |
| `suporte` | `SuporteTipos` | ticket e mensagem |
| `importacao` | `ImportacaoDominioTipos` | execucao, mapeamento, pendencia e staging |

## Pendencias para fase futura

Pendencias tecnicas:

- `PENDENTE_JPA_JAKARTA_PERSISTENCE`;
- `PENDENTE_REPOSITORIES_SPRING_DATA_JPA`;
- mapeamento JPA de relacionamentos;
- decisao de tipo de JSON para `jsonb`;
- repositories e consultas;
- testes de persistencia contra PostgreSQL local descartavel.

Pendencias de cobertura direta:

- tabelas de credencial e permissao;
- tabelas de backup;
- records individuais para cada `stg_*`, caso a revisao Pro decida que a referencia estrutural unica nao e suficiente.

## Complemento Bloco 4

A matriz JPA passa a ser mantida em `docs/v3/95-matriz-jpa-tabela-repository.md`. O Bloco 4 mapeia 41 tabelas com entidades JPA separadas dos records de dominio e deixa 27 tabelas pendentes ou deliberadamente fora da camada funcional.
