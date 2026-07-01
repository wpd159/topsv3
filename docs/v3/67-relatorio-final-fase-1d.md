# Relatório final da Fase 1D

## Status

Status da entrega: `OK PARA AUDITORIA TÉCNICA LOCAL, AGUARDANDO_REVISAO_PRO`.

A Fase 1D está consolidada para revisão final das migrations. O schema ainda não está aprovado para aplicação em banco persistente e nenhuma fase dependente pode começar antes da revisão Pro.

## Escopo consolidado

Foram criadas migrations SQL versionadas em:

```text
backend/src/main/resources/db/migration/
```

Não foram criadas migrations novas na Fase 1D.5. Nenhum SQL de schema foi alterado nesta fase de fechamento.

## Ordem das migrations

| Ordem | Migration | Escopo |
| --- | --- | --- |
| 1 | `V001__extensoes_postgresql.sql` | Extensões PostgreSQL candidatas: `pg_trgm` e `unaccent`. |
| 2 | `V002__usuarios_autenticacao.sql` | Usuários, credenciais, papéis, permissões, sessões e tokens. |
| 3 | `V003__localizacao.sql` | Estados, cidades, bairros e localização de anúncio. |
| 4 | `V004__anuncios.sql` | Anúncios, histórico de status e documento de busca. |
| 5 | `V005__midia_stories_documentos.sql` | Mídia, documentos privados, auditoria de acesso e stories. |
| 6 | `V006__moderacao.sql` | Revisão de anúncios, revisão de mídia e decisões de moderação. |
| 7 | `V007__premium_creditos.sql` | Premium, ativações, créditos, ledger e saldos. |
| 8 | `V008__financeiro_efi_historico_legado.sql` | Planos, pagamentos, eventos, webhooks e conciliação. |
| 9 | `V009__metricas.sql` | Visualizações, cliques WhatsApp, agregados e verificação etária auditável, sem desbloqueio parcial. |
| 10 | `V010__seo_urls_redirects.sql` | URLs, metadados, redirects e conteúdo SEO. |
| 11 | `V011__banners.sql` | Espaços de banner, banners e versões. |
| 12 | `V012__comercial_suporte.sql` | Comercial e suporte. |
| 13 | `V013__auditoria_outbox.sql` | Auditoria e outbox. |
| 14 | `V014__backup.sql` | Política, execução, artefatos e testes de restauração. |
| 15 | `V015__importacao_staging.sql` | Controle de importação futura e staging explícito. |
| 16 | `V016__indices_busca.sql` | Índices de busca, FTS e trigram. |
| 17 | `V017__constraints_finais.sql` | Constraints finais dependentes entre tabelas. |

## Tabelas principais

Blocos principais cobertos:

- autenticação e autorização: `usuario`, `credencial_usuario`, `papel_usuario`, `permissao`, `papel_permissao`, `sessao_usuario`, `token_seguranca`;
- localização e anúncios: `estado`, `cidade`, `bairro`, `anuncio`, `anuncio_localizacao`, `anuncio_status_historico`, `documento_busca_anuncio`;
- mídia, documentos e stories: `arquivo_midia`, `anuncio_midia`, `documento_usuario`, `documento_usuario_acesso`, `story_anuncio`;
- moderação: `revisao_anuncio`, `anuncio_midia_revisao`, `decisao_moderacao`;
- premium e créditos: `beneficio_premium`, `beneficio_premium_opcao`, `grupo_ativacao_beneficio`, `ativacao_beneficio`, `movimento_credito`, `saldo_credito_usuario`;
- financeiro: `plano_credito`, `pagamento`, `pagamento_evento`, `pagamento_webhook`, `pagamento_conciliacao`;
- métricas: `evento_visualizacao`, `agregado_visualizacao_diaria`, `clique_whatsapp`, `agregado_clique_whatsapp_diario`, `evento_verificacao_etaria`;
- SEO e banners: `seo_url`, `seo_metadado`, `seo_redirect`, `seo_conteudo_pagina`, `banner_espaco`, `banner`, `banner_versao`;
- operação: `comercial_status`, `comercial_contato`, `comercial_interacao`, `ticket_suporte`, `mensagem_suporte`, `auditoria_evento`, `outbox_evento`;
- backup e importação futura: `backup_politica`, `backup_execucao`, `backup_artefato`, `backup_teste_restauracao`, `importacao_execucao`, `importacao_mapeamento`, `importacao_pendencia` e tabelas `stg_*`.

## Validações executadas

- Validação SQL estática: `27/27` verificações OK.
- Validação PostgreSQL local descartável: `OK_POSTGRES_DESCARTAVEL`.
- Método da validação real: `SQL_ORDENADO_PSQL`.
- Imagem local usada: `postgres:16`.
- Migrations aplicadas em ordem: `V001` a `V017`, todas OK.
- Resumo do schema descartável: 68 tabelas, 222 índices, 853 constraints.
- Extensões criadas no banco descartável: `pg_trgm`, `unaccent`.
- Rotas públicas e SEO local: `49/49` verificações OK.
- Codificação, arquivos proibidos e secrets: OK, com `gitleaks` pendente por binário ausente e fallback local executado.
- `git diff --check` e `git diff --cached --check`: OK.

## Exit codes do validador PostgreSQL

O script `scripts/local/validar-migrations-postgres-descartavel.ps1` deve retornar:

- `0` somente quando `VALIDATION_RESULT=OK_POSTGRES_DESCARTAVEL`;
- `1` quando houver falha real de migration, SQL, ordem, FK, constraint, índice ou extensão;
- `2` quando houver pendência operacional, como Docker indisponível, imagem PostgreSQL local ausente ou ferramenta local exigida indisponível.

O caso validado nesta fase continua usando `SQL_ORDENADO_PSQL` e retorna `0`.

## Pendências Pro

- Revisar e aprovar o schema antes de qualquer fase dependente.
- Executar `flyway migrate/validate` real quando Flyway CLI ou imagem Flyway estiverem disponíveis localmente sem instalação ou download.
- Avaliar custo e necessidade de `pg_trgm`, `unaccent`, FTS e índices pesados.
- Validar `CHECK` versus catálogos administráveis.
- Validar impacto das constraints finais na futura importação.
- Definir sanitizer e política de retenção para `auditoria_evento.antes_json` e `auditoria_evento.depois_json`.
- Confirmar estratégia final de retenção de documentos privados.
- Revisar modelagem financeira, idempotência, txid, webhook e conciliação.

## Riscos residuais

- A validação com `SQL_ORDENADO_PSQL` confirma execução PostgreSQL real, mas não grava `flyway_schema_history`.
- O custo de índices e extensões precisa de análise Pro antes de aplicação fora de banco descartável.
- O schema ainda não foi confrontado com dados legados reais.
- Sanitização de auditoria depende de implementação futura da aplicação.
- Importação futura pode exigir decisões adicionais para qualidade, deduplicação e compatibilidade de dados.

## Proibições antes da aprovação final

- Não aplicar migrations em banco persistente.
- Não acessar produção, VPS, banco de produção, Efí real, OpenAI ou API externa.
- Não usar dados reais, dump, seed ou importação.
- Não criar entidade JPA, repository, service, controller de domínio ou importador.
- Não iniciar backend de domínio, painel funcional ou Fase 2.
- Não configurar remote, executar push ou commit por esta fase.

## Regra de avanço

Antes da aprovação Pro, a Fase 1D serve apenas como pacote técnico de auditoria. O importador, o backend de domínio, entidades JPA, repositories, services, controllers e qualquer fase dependente do schema continuam bloqueados.

## Complemento 2A

A Fase 2A iniciou apenas a estrutura local do importador saneador, sem alterar o schema fechado na Fase 1D. Nenhuma migration nova foi criada, nenhum SQL foi alterado, nenhum banco foi acessado e nenhuma importação real foi iniciada.

O schema segue `AGUARDANDO_REVISAO_PRO`; a estrutura 2A serve somente para preparar contratos, relatórios, manifesto de mídia, mapa legado -> V3 e mapa URL atual -> V3.

## Complemento 2G

A Fase 2G consolidou este estado no dossie de transicao para revisao Pro. A consolidacao nao aprova o schema, nao executa Flyway real, nao aplica banco persistente, nao cria nova migration e nao altera SQL.

Os gates mantidos antes de fase dependente critica sao: revisao Pro do schema, Flyway real local quando disponivel sem instalacao/download ou decisao formal equivalente, fonte real autorizada fora do repositorio, dry-run real aprovado e ausencia de pendencia bloqueante.

## Complemento Bloco 3

O Bloco 3 criou domínio Java puro para espelhar o schema das migrations sem alterar SQL e sem aplicar banco. Como JPA/Spring Data JPA não estão disponíveis no backend local, não foram criadas entidades JPA anotadas nem repositories.

O schema segue `AGUARDANDO_REVISAO_PRO`. Os marcadores `PENDENTE_JPA_JAKARTA_PERSISTENCE` e `PENDENTE_REPOSITORIES_SPRING_DATA_JPA` continuam bloqueando mapeamento JPA e repositories até decisão futura.

## Complemento Bloco 4

O Bloco 4 cria persistencia JPA base local sobre o schema `V001` a `V017`, sem alterar SQL e sem aplicar banco. O schema permanece `AGUARDANDO_REVISAO_PRO`; a persistencia criada serve como base de codigo local e nao aprova migracao, importador, endpoint ou execucao em banco persistente.

Maven/build local fica pendente quando dependencias JPA ainda nao estiverem em cache local sem download.

## Complemento Bloco 5

O Bloco 5 usa a base JPA apenas para criar API publica minima de leitura. Nenhuma migration nova foi criada, nenhum SQL foi alterado, nenhum Flyway foi executado e nenhum banco foi acessado.

O schema segue `AGUARDANDO_REVISAO_PRO`; os endpoints publicos sao codigo local de leitura e nao aprovam aplicacao de banco persistente, importador real ou fase de dados reais.
