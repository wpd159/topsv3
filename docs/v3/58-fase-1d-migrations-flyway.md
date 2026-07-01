# Fase 1D - migrations Flyway PostgreSQL

## Status

Status da entrega: `OK PARA AUDITORIA, AGUARDANDO_REVISAO_PRO`.

As migrations foram geradas localmente para auditoria técnica. Elas não estão aprovadas para aplicação, não foram executadas por Flyway nesta máquina e não foram aplicadas em nenhum banco.

## Escopo executado

A Fase 1D materializa o primeiro conjunto de migrations Flyway da V3 em:

```text
backend/src/main/resources/db/migration/
```

O objetivo é permitir revisão Pro do schema físico antes de qualquer execução. Esta fase não cria aplicação de domínio, não conecta banco, não sobe Docker, não executa SQL e não inicia importador.

## Migrations criadas

| Migration | Escopo | Observação de auditoria |
| --- | --- | --- |
| `V001__extensoes_postgresql.sql` | Extensões PostgreSQL candidatas | Usa apenas `CREATE EXTENSION IF NOT EXISTS` para `pg_trgm` e `unaccent`; depende de revisão Pro antes de aplicação. |
| `V002__usuarios_autenticacao.sql` | Usuários, credenciais, papéis, permissões, sessões e tokens | Modelagem estrutural, sem login funcional, sem usuário real e sem segredo. |
| `V003__localizacao.sql` | Estado, cidade, bairro e vínculo de localização | Suporta rotas públicas por UF/cidade/bairro; FK final para anúncio fica em `V017`. |
| `V004__anuncios.sql` | Anúncio, histórico de status e documento de busca | Preserva `/anuncios/[slug]` como contrato público; não cria anúncio real. |
| `V005__midia_stories_documentos.sql` | Mídia, vínculo de mídia, documentos privados, auditoria de acesso e stories | Story referencia mídia canônica; documento privado nunca é publicável e não usa URL real como fonte de mídia. |
| `V006__moderacao.sql` | Revisão de anúncio, revisão de mídia e decisão de moderação | Inclui base para classificação e revisão futura, sem fluxo funcional. |
| `V007__premium_creditos.sql` | Benefícios premium, grupos de ativação, ledger e saldo | Premium é aditivo; ledger possui coerência matemática por direção. |
| `V008__financeiro_efi_historico_legado.sql` | Planos, pagamentos, eventos, webhook e conciliação | Apenas modelagem; Efí exige Pix e eventos têm idempotência escopada por provedor. |
| `V009__metricas.sql` | Visualizações, cliques WhatsApp, agregados e verificação etária auditável | Agregados usam `id` e chaves normalizadas para origem desconhecida; não há desbloqueio parcial por visitante. |
| `V010__seo_urls_redirects.sql` | URLs, metadados, redirects e conteúdo SEO | Aceita `/sitemap.xml` e `/robots.txt`, bloqueia rotas alternativas e evita múltiplas versões publicadas. |
| `V011__banners.sql` | Espaços, banners e versões | Rascunho pode existir sem mídia; agendado/publicado exige arquivos e texto alternativo. |
| `V012__comercial_suporte.sql` | Comercial e suporte | Estrutura futura para aquisição e atendimento, sem regra funcional. |
| `V013__auditoria_outbox.sql` | Auditoria e outbox | Snapshots devem ser sanitizados; hashes podem substituir JSON livre quando houver risco. |
| `V014__backup.sql` | Política, execução, artefato e teste de restauração | Tipos de artefato incluem banco, mídia, configuração, SEO, release, log e manifesto. |
| `V015__importacao_staging.sql` | Execução, mapeamento, pendências e staging | Staging explícito por tabela, sem `LIKE INCLUDING ALL`; não importa dados. |
| `V016__indices_busca.sql` | Índices de busca, FTS e trigram | Índices pesados isolados para revisão de custo e plano local. |
| `V017__constraints_finais.sql` | Constraints finais dependentes | Fecha FKs e restrições cruzadas que dependem de tabelas anteriores. |

## Decisões materializadas para auditoria

- O schema usa o namespace padrão `public` nesta revisão inicial.
- UUID v7 continua previsto como geração pela aplicação; as tabelas usam `uuid` sem default aleatório em PKs de domínio.
- `pg_trgm` e `unaccent` foram materializados como candidatos em `V001`, mas permanecem dependentes de aprovação Pro antes de aplicação.
- Enums técnicos usam `CHECK` nesta versão de auditoria; catálogos administráveis continuam como ponto de revisão.
- Campos financeiros usam `numeric` ou inteiros, nunca tipos aproximados.
- Campos de IP/user-agent usam hash quando necessários para privacidade.
- Mídia usa referência abstrata de storage, não URL pública como fonte de verdade.
- Documento de usuário pode ser mantido enquanto houver anúncio vinculado ou finalidade operacional legítima; `retencao_ate` é nullable e expurgo automático não foi criado nesta fase.
- `anuncio_midia` possui consistência explícita para stories: `tipo = 'STORY'` exige `finalidade = 'STORY'`, e mídia não story não usa finalidade `STORY`.
- `pagamento_evento` referencia `pagamento` por `(pagamento_id, provedor)` para impedir divergência de provedor entre evento e pagamento.
- Slug público de anúncio é globalmente único para evitar reutilização indevida e preservar histórico SEO.
- Localização de anúncio e documento de busca usa FKs compostas para impedir combinação inconsistente de estado, cidade e bairro.
- Tabela legada com nome de Mercado Pago não define provedor; o importador futuro deve classificar por evidências.

## Bloqueios preservados

- Nenhuma migration foi aplicada.
- Flyway não foi executado.
- Nenhum banco foi acessado, iniciado ou criado.
- Nenhuma entidade JPA, repository, service de domínio, controller de domínio ou importador foi criado.
- Nenhum dado real, seed real, foto real, conteúdo explícito, certificado, token, senha ou credencial foi criado.
- Nenhuma API externa, Efí real, OpenAI, VPS, produção, remote, push ou commit foi usado.

## Validação permitida nesta fase

A validação estática e textual permanece obrigatória:

```powershell
.\scripts\local\validar-migrations-sql-estatico.ps1
.\scripts\security\verificar-codificacao.ps1
.\scripts\security\verificar-arquivos-proibidos.ps1
.\scripts\security\verificar-segredos.ps1
.\scripts\local\validar-rotas-publicas-seo-local.ps1
git diff --check
git diff --cached --check
```

A Fase 1D.3 adiciona um validador local descartável:

```powershell
.\scripts\local\validar-migrations-postgres-descartavel.ps1
```

Esse script só pode usar Docker/Flyway se as ferramentas e imagens já existirem localmente. Ele não baixa dependências, não executa `docker pull`, não usa `docker compose up`, não cria volume persistente e não acessa produção. Nesta execução, a validação real ficou `PENDENTE_VALIDACAO_POSTGRES_LOCAL` porque o Docker daemon estava indisponível e Flyway CLI não foi encontrado.

Build com download de dependências, banco persistente, importador, backend de domínio e execução fora de PostgreSQL descartável continuam bloqueados até autorização futura.

## Fechamento 1D.5

A Fase 1D.5 consolidou o pacote final de auditoria:

- migrations `V001` a `V017` permanecem como baseline de auditoria;
- validação SQL estática passou com `27/27` verificações;
- validação em PostgreSQL 16 descartável passou com `OK_POSTGRES_DESCARTAVEL`;
- método usado: `SQL_ORDENADO_PSQL`;
- Flyway real continua pendente porque CLI/imagem Flyway não estavam disponíveis localmente sem instalação ou download;
- nenhuma migration nova foi criada;
- nenhum SQL de schema foi alterado;
- schema continua `AGUARDANDO_REVISAO_PRO` antes de importador, backend de domínio ou fase dependente.
