# Matriz JPA - tabela - repository

## Resumo

- Tabelas nas migrations `V001` a `V017`: 68.
- Entidades JPA criadas no Bloco 4: 41.
- Entidades endurecidas no Bloco 4.1: 41.
- Repositories minimos criados: 10.
- Tabelas pendentes ou deliberadamente nao mapeadas: 27.

## Matriz

| Tabela | Entidade JPA | Repository | Status |
| --- | --- | --- | --- |
| `usuario` | `UsuarioEntity` | `UsuarioRepository` | Mapeada |
| `credencial_usuario` | `CredencialUsuarioEntity` | - | Mapeada sem repository funcional |
| `papel_usuario` | `PapelUsuarioEntity` | - | Mapeada sem repository funcional |
| `estado` | `EstadoEntity` | - | Mapeada sem repository funcional |
| `cidade` | `CidadeEntity` | - | Mapeada sem repository funcional |
| `bairro` | `BairroEntity` | - | Mapeada sem repository funcional |
| `anuncio` | `AnuncioEntity` | `AnuncioRepository` | Mapeada |
| `anuncio_localizacao` | `AnuncioLocalizacaoEntity` | - | Mapeada sem repository funcional |
| `documento_busca_anuncio` | `DocumentoBuscaAnuncioEntity` | - | Mapeada sem repository funcional |
| `arquivo_midia` | `ArquivoMidiaEntity` | `ArquivoMidiaRepository` | Mapeada |
| `anuncio_midia` | `AnuncioMidiaEntity` | - | Mapeada sem repository funcional |
| `story_anuncio` | `StoryAnuncioEntity` | - | Mapeada sem repository funcional |
| `documento_usuario` | `DocumentoUsuarioEntity` | `DocumentoUsuarioRepository` | Mapeada |
| `documento_usuario_acesso` | `DocumentoUsuarioAcessoEntity` | - | Mapeada sem repository funcional |
| `revisao_anuncio` | `RevisaoAnuncioEntity` | - | Mapeada sem repository funcional |
| `anuncio_midia_revisao` | `AnuncioMidiaRevisaoEntity` | - | Mapeada sem repository funcional |
| `decisao_moderacao` | `DecisaoModeracaoEntity` | - | Mapeada sem repository funcional |
| `beneficio_premium` | `BeneficioPremiumEntity` | - | Mapeada sem repository funcional |
| `grupo_ativacao_beneficio` | `GrupoAtivacaoBeneficioEntity` | - | Mapeada sem repository funcional |
| `ativacao_beneficio` | `AtivacaoBeneficioEntity` | `AtivacaoBeneficioRepository` | Mapeada |
| `movimento_credito` | `MovimentoCreditoEntity` | `MovimentoCreditoRepository` | Mapeada |
| `pagamento` | `PagamentoEntity` | `PagamentoRepository` | Mapeada |
| `pagamento_evento` | `PagamentoEventoEntity` | - | Mapeada sem repository funcional |
| `pagamento_conciliacao` | `PagamentoConciliacaoEntity` | - | Mapeada sem repository funcional |
| `evento_visualizacao` | `EventoVisualizacaoEntity` | - | Mapeada sem repository funcional |
| `clique_whatsapp` | `CliqueWhatsappEntity` | - | Mapeada sem repository funcional |
| `agregado_visualizacao_diaria` | `AgregadoVisualizacaoDiariaEntity` | - | Mapeada sem repository funcional |
| `agregado_clique_whatsapp_diario` | `AgregadoCliqueWhatsappDiarioEntity` | - | Mapeada sem repository funcional |
| `seo_url` | `SeoUrlEntity` | `SeoUrlRepository` | Mapeada |
| `seo_conteudo_pagina` | `SeoConteudoPaginaEntity` | - | Mapeada sem repository funcional |
| `seo_redirect` | `SeoRedirectEntity` | - | Mapeada sem repository funcional |
| `banner` | `BannerEntity` | `BannerRepository` | Mapeada |
| `auditoria_evento` | `AuditoriaEventoEntity` | `AuditoriaEventoRepository` | Mapeada |
| `outbox_evento` | `OutboxEventoEntity` | - | Mapeada sem repository funcional |
| `comercial_contato` | `ComercialContatoEntity` | - | Mapeada sem repository funcional |
| `comercial_interacao` | `ComercialInteracaoEntity` | - | Mapeada sem repository funcional |
| `ticket_suporte` | `TicketSuporteEntity` | - | Mapeada sem repository funcional |
| `backup_politica` | `BackupPoliticaEntity` | - | Mapeada sem repository funcional |
| `backup_execucao` | `BackupExecucaoEntity` | - | Mapeada sem repository funcional |
| `backup_artefato` | `BackupArtefatoEntity` | - | Mapeada sem repository funcional |
| `backup_teste_restauracao` | `BackupTesteRestauracaoEntity` | - | Mapeada sem repository funcional |

## Tabelas pendentes ou nao mapeadas

| Tabela | Motivo |
| --- | --- |
| `permissao` | RBAC funcional futuro; sem controller/service nesta fase |
| `papel_permissao` | RBAC funcional futuro; sem repository nesta fase |
| `sessao_usuario` | autenticacao/sessao funcional futura |
| `token_seguranca` | fluxo de autenticacao futuro |
| `anuncio_status_historico` | historico sem repository minimo nesta fase |
| `beneficio_premium_opcao` | regra comercial versionada futura |
| `saldo_credito_usuario` | projecao de saldo depende de regra transacional futura |
| `plano_credito` | catalogo financeiro sem seed nesta fase |
| `pagamento_webhook` | webhook real continua bloqueado |
| `evento_verificacao_etaria` | auditoria futura de verificacao etaria, sem desbloqueio parcial por visitante |
| `seo_metadado` | metadados publicaveis dependem de revisao de SEO futura |
| `banner_espaco` | catalogo sem seed nesta fase |
| `banner_versao` | historico/rollback funcional futuro |
| `comercial_status` | catalogo sem seed nesta fase |
| `mensagem_suporte` | conteudo de suporte funcional futuro |
| `importacao_execucao` | importador real bloqueado |
| `importacao_mapeamento` | importador real bloqueado |
| `importacao_pendencia` | importador real bloqueado |
| `stg_usuario` | staging fora de repositories funcionais |
| `stg_anuncio` | staging fora de repositories funcionais |
| `stg_localidade` | staging fora de repositories funcionais |
| `stg_midia` | staging fora de repositories funcionais |
| `stg_story` | staging fora de repositories funcionais |
| `stg_pagamento` | staging fora de repositories funcionais |
| `stg_credito` | staging fora de repositories funcionais |
| `stg_premium` | staging fora de repositories funcionais |
| `stg_url` | staging fora de repositories funcionais |

## Observacoes

As entidades nao implementam regra de negocio. Campos de FK foram mantidos como `UUID`, para evitar associacoes JPA incorretas antes de revisao de uso real.

No Bloco 4.1, todas as entidades mapeadas receberam construtor JPA protegido explicito e getters publicos. Os repositories permaneceram minimos, sem query customizada, sem query nativa e sem corpo funcional.

Campos `jsonb` foram anotados com `@JdbcTypeCode(SqlTypes.JSON)` sem dependencia nova. A validacao runtime permanece pendente por falta de Maven/wrapper local.

Campos `BigDecimal` foram alinhados a precisao fisica ja definida nas migrations: `numeric(12,2)` para dinheiro, `numeric(9,6)` para coordenadas e `numeric(10,4)` para ranking.
