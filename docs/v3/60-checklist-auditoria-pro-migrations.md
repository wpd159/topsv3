# Checklist de auditoria Pro das migrations

Status da entrega: `OK PARA AUDITORIA, AGUARDANDO_REVISAO_PRO`.

## Checklist executado

- [x] Inventário inicial criado fora do repositório.
- [x] Documentos de banco, SEO, Premium, métricas, segurança, rotas públicas, admin shell e fases lidos antes da escrita SQL.
- [x] Ordem documentada V001 a V017 conferida.
- [x] Migrations criadas em `backend/src/main/resources/db/migration`.
- [x] Nenhuma migration aplicada.
- [x] Flyway não executado.
- [x] Banco não acessado.
- [x] Docker não subido.
- [x] Nenhum dado real criado.
- [x] Nenhum seed real criado.
- [x] Nenhum secret, senha, token, chave, certificado ou credencial criado.
- [x] Nenhuma URL real de produção ou API criada em migrations.
- [x] UUID, timestamps, valores monetários e créditos revisados estaticamente.
- [x] Constraints e CHECKs revisados estaticamente.
- [x] Índices revisados estaticamente.
- [x] Anúncios revisados quanto a slug, status, localização e moderação.
- [x] Slug de anúncio revisado como unicidade global.
- [x] Localização revisada com FKs compostas para estado/cidade/bairro.
- [x] Mídia revisada sem foto real, arquivo real ou URL pública como fonte de verdade.
- [x] Documento privado revisado com `retencao_ate` nullable, política de retenção, validação explícita, remoção/expurgo registrados e acesso auditável.
- [x] Premium preservado como regra existente e modelado de forma aditiva.
- [x] Gratuito sem limite diário de cliques, contatos ou WhatsApp.
- [x] Créditos modelados com ledger, idempotência e rastreabilidade.
- [x] Ledger de créditos revisado com coerência de saldo por direção.
- [x] Pix/Efí apenas modelado, sem integração.
- [x] Pagamentos revisados com Efí/Pix, legado sem fluxo ativo e eventos escopados por provedor.
- [x] Métricas de produção tratadas como preserváveis, migráveis ou reimplementáveis com equivalência funcional.
- [x] Agregados de métricas revisados para origem desconhecida.
- [x] SEO e rotas públicas preservadas.
- [x] `/sitemap.xml` e `/robots.txt` aceitos nas constraints SEO.
- [x] Auditoria, admin e RBAC tratados como base estrutural, sem autenticação funcional.
- [x] Auditoria revisada para snapshots sanitizados e hashes.
- [x] Staging revisado sem `LIKE INCLUDING ALL`.
- [x] Nenhuma entidade JPA criada.
- [x] Nenhum repository criado.
- [x] Nenhum service de domínio criado.
- [x] Nenhum controller de domínio criado.
- [x] Nenhum importador criado.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.
- [x] Validador SQL estático criado e executado.
- [x] Validador PostgreSQL descartável da Fase 1D.3 criado.
- [x] Validação PostgreSQL descartável registrada como `PENDENTE_VALIDACAO_POSTGRES_LOCAL` nesta máquina por Docker daemon/Flyway indisponíveis sem instalação ou download.
- [x] Validação real da Fase 1D.4 executada em PostgreSQL local descartável.
- [x] Docker Desktop local iniciado pelo script quando o daemon estava indisponível.
- [x] Imagem local `postgres:16` usada sem pull.
- [x] Fallback `SQL_ORDENADO_PSQL` usado porque Flyway não estava disponível localmente.
- [x] Migrations `V001` a `V017` aplicadas em ordem sem erro SQL.
- [x] Schema descartável validado com 68 tabelas, 222 índices, 853 constraints e extensões `pg_trgm`/`unaccent`.
- [x] Fase 1D.5 consolidada sem migration nova e sem alteração de SQL de schema.
- [x] Relatório final da Fase 1D criado.
- [x] Checklist final da Fase 1D criado.
- [x] Exit codes do validador PostgreSQL padronizados: `0` sucesso, `1` falha real, `2` pendência operacional.
- [x] `V005` revisada com consistência entre `anuncio_midia.tipo` e `anuncio_midia.finalidade` para stories.
- [x] `V008` revisada com FK composta entre evento financeiro e pagamento para impedir divergência de provedor.
- [x] ZIP final criado e validado.

## Checklist obrigatório para revisão Pro futura

- [ ] Revisar `V001` e decidir se `pg_trgm` e `unaccent` devem permanecer no baseline.
- [ ] Revisar se `public` único permanece adequado ou se serão usados schemas separados.
- [ ] Revisar CHECK versus tabela de catálogo para enums críticos.
- [ ] Revisar todos os índices de `V016` com plano local e estimativa de custo antes de aplicar fora de banco descartável.
- [ ] Revisar constraints finais de `V017` contra importação futura.
- [ ] Revisar estratégia definitiva de reserva/histórico de slug antes de importação.
- [ ] Revisar equivalência dos agregados de métricas com dados atuais.
- [ ] Revisar modelagem financeira antes de qualquer implementação funcional.
- [ ] Revisar política de retenção/minimização de métricas, IP e user-agent.
- [ ] Revisar decisão jurídica final de retenção de documentos privados.
- [ ] Revisar storage de mídia antes de importação ou upload real.
- [ ] Revisar equivalência das métricas atuais antes do importador.
- [ ] Autorizar explicitamente qualquer execução de Flyway em banco local descartável.
- [ ] Executar `flyway migrate/validate` real quando Flyway local estiver disponível sem download.
- [ ] Validar sanitizer futuro da aplicação para `auditoria_evento.antes_json` e `auditoria_evento.depois_json`.
- [ ] Aprovar explicitamente o schema antes de importador, backend de domínio ou fase dependente.

## Bloqueios até aprovação

- [ ] Não aplicar migrations.
- [ ] Não iniciar Fase 1E, Fase 2 ou fase dependente do schema.
- [ ] Não criar entidades JPA de domínio.
- [ ] Não criar repositories de domínio.
- [ ] Não criar services de negócio.
- [ ] Não criar controllers de domínio.
- [ ] Não criar importador.
- [ ] Não criar autenticação funcional.
- [ ] Não criar integração Pix/Efí funcional.
- [ ] Não acessar produção, VPS, banco de produção, Efí real, OpenAI ou API externa.
