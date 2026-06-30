# Decisões pendentes e ADRs

## Objetivo

Registrar decisões arquiteturais e pendências que bloqueiam fases futuras da V3.

Status possíveis:

- `APROVADO`
- `PROPOSTO`
- `DECISAO_PENDENTE`
- `REVISAO_NECESSARIA`

## ADR-001 — monolito modular

Contexto: a V3 precisa reduzir risco operacional e manter fronteiras claras de domínio.

Decisão: adotar monolito modular em Spring Boot, com módulos por domínio e banco PostgreSQL.

Alternativas: microserviços desde o início; monolito sem fronteiras; reaproveitamento direto do legado.

Consequência: menor complexidade operacional inicial, exigindo disciplina de módulos, contratos internos e testes.

Fase bloqueada: Fase 1B.

Status: `APROVADO`.

Responsável: arquitetura técnica.

Prazo limite: antes da Fase 1B.

## ADR-002 — PostgreSQL

Contexto: a V3 exige consistência transacional, busca, constraints e auditoria.

Decisão: usar PostgreSQL como banco relacional principal.

Alternativas: manter banco legado sem saneamento; usar banco NoSQL como fonte principal; múltiplos bancos por domínio.

Consequência: simplifica consistência, migrations, busca e conciliação financeira.

Fase bloqueada: Fase 1C.

Status: `APROVADO`.

Responsável: arquitetura técnica.

Prazo limite: antes da Fase 1C.

## ADR-003 — Flyway como ferramenta de migrations

Contexto: o legado teve risco em migrations, execução de startup, ordenação e mudanças pouco reproduzíveis.

Decisão:

- usar Flyway;
- usar migrations SQL explícitas;
- versionar migrations por Git;
- usar PostgreSQL como alvo principal;
- tratar migrations como imutáveis depois de aplicadas fora do ambiente local descartável;
- não usar migration automática por ORM;
- não usar executor próprio;
- não realizar alteração manual de schema em produção;
- executar migrations fora do startup operacional comum da aplicação.

Alternativas: Liquibase; executor próprio; migrations automáticas por ORM; scripts manuais.

Consequência: migrations ficam simples, revisáveis e previsíveis; mudanças complexas devem ser quebradas em SQL explícito e revisado.

Fase bloqueada: Fase 1D.

Status: `APROVADO`.

Responsável: arquitetura técnica.

Prazo limite: antes da Fase 1D.

## ADR-004 — modelo canônico de stories

Contexto: stories não podem ter fonte concorrente para anúncio e arquivo.

Decisão: `story_anuncio` usa `anuncio_midia_id` obrigatório e único. `anuncio_midia.tipo` e `anuncio_midia.finalidade` devem ser `STORY`. `anuncio_id` e `arquivo_midia_id` não pertencem a `story_anuncio`.

Alternativas: story apontar diretamente para `anuncio` e `arquivo_midia`; story guardar URL solta; story ser apenas tipo de `anuncio_midia`.

Consequência: elimina duplicidade de fonte, preserva vínculo canônico e mantém regras de mídia/moderação em `anuncio_midia`.

Fase bloqueada: Fase 1C.

Status: `APROVADO`.

Responsável: arquitetura de dados.

Prazo limite: antes da Fase 1C.

## ADR-005 — histórico de provedores legados

Contexto: Efí é o único provedor ativo inicial, mas registros legados podem incluir Mercado Pago e provedores desconhecidos.

Decisão: adotar a opção B. `pagamento.provedor` aceita `EFI`, `MERCADO_PAGO_LEGADO`, `OUTRO_LEGADO` e `DESCONHECIDO`. Apenas `EFI` pode ter integração ativa na V3 inicial. Para `EFI`, o método ativo inicial é `PIX`; métodos legados podem ser preservados somente para auditoria.

Alternativas: manter Mercado Pago somente em staging/importação; criar integração Mercado Pago ativa; converter registros Mercado Pago para Efí.

Consequência: histórico e auditoria são preservados em `pagamento`, sem reativar Mercado Pago nem criar dependência nova.

Fase bloqueada: Fase 2.

Status: `APROVADO`.

Responsável: arquitetura financeira.

Prazo limite: antes da Fase 2.

## ADR-006 — storage definitivo

Contexto: a V3 precisa escolher storage canônico para mídia pública, documentos privados e variantes.

Decisão: DECISAO_PENDENTE. R2 é candidato, Supabase permanece apenas como origem temporária/importação.

Alternativas: R2 como storage definitivo; storage local apenas para desenvolvimento; outro provedor compatível S3; manter Supabase como permanente.

Consequência: enquanto pendente, a Fase 1A pode usar storage local, mas Fase 2 e Fase 4 precisam da decisão para manifesto, URLs, CDN e retenção.

Fase bloqueada: Fase 2.

Status: `DECISAO_PENDENTE`.

Responsável: infraestrutura e arquitetura.

Prazo limite: antes da Fase 2.

## ADR-007 — autenticação e sessão

Contexto: o legado tem riscos em JWT/cookies, CSRF, 2FA, armazenamento de tokens e revogação.

Decisão:

- usar autenticação web baseada em sessão server-side;
- usar sessão opaca gerenciada pelo backend;
- usar cookie `HttpOnly`;
- usar cookie `Secure` fora do ambiente local;
- usar `SameSite=Lax` por padrão;
- não usar token no `localStorage` para autenticação do navegador;
- permitir revogação por usuário, dispositivo e administrador;
- armazenar sessões inicialmente no PostgreSQL;
- preferir frontend/backend em mesma origem por proxy;
- habilitar CSRF em operações autenticadas que alteram estado;
- restringir CORS por ambiente;
- armazenar senha com hash forte;
- usar resposta neutra contra enumeração de contas;
- rotacionar sessão depois do login e da elevação de privilégio;
- definir expiração absoluta e expiração por inatividade;
- auditar login, logout, falha relevante e revogação;
- exigir senha mais token por e-mail para ADMIN, MODERADOR e COMERCIAL na versão inicial;
- armazenar token por e-mail apenas como hash, com expiração, tentativas e consumo único;
- manter TOTP/passkey como evolução futura;
- não usar JWT como mecanismo principal de autenticação do navegador;
- definir contrato separado para tokens técnicos futuros.

Alternativas: JWT curto com refresh revogável; sessão híbrida; provedor externo; reaproveitar legado.

Consequência: autenticação do navegador fica revogável e protegida contra exposição de token no frontend, com custo operacional inicial no backend e PostgreSQL.

Fase bloqueada: Fase 1B.

Status: `APROVADO`.

Responsável: segurança e backend.

Prazo limite: antes da Fase 1B.

## ADR-008 — RPO/RTO e retenção

Contexto: backup, privacidade, auditoria e incidentes exigem números objetivos.

Decisão: DECISAO_PENDENTE para RPO, RTO e prazos de retenção por domínio.

Alternativas: política única para todos os dados; política por domínio; retenção mínima até revisão jurídica.

Consequência: bloqueia produção e define backup, restauração, logs, documentos privados e auditoria.

Fase bloqueada: Fase 8.

Status: `DECISAO_PENDENTE`.

Responsável: operação, segurança e jurídico.

Prazo limite: antes da Fase 8.

## ADR-009 — thresholds de performance

Contexto: critérios genéricos de performance, erro e estabilidade precisam virar números mensuráveis.

Decisão: DECISAO_PENDENTE para limites de latência, disponibilidade, erro, carga, Core Web Vitals, duração de smoke test e janela de estabilização.

Alternativas: usar metas conservadoras iniciais; basear em métricas atuais do legado; contratar validação externa.

Consequência: gates de Fase 7, Fase 9 e Fase 10 devem referenciar este ADR até os números finais serem aprovados.

Fase bloqueada: Fase 7.

Status: `DECISAO_PENDENTE`.

Responsável: arquitetura, produto e operação.

Prazo limite: antes da Fase 7.

## ADR-010 — estratégia de promoção para VPS

Contexto: a V3 precisa promover local -> staging -> homologação -> produção com isolamento e rollback.

Decisão: usar fluxo `LOCAL -> TESTES LOCAIS -> STAGING ISOLADO -> HOMOLOGAÇÃO -> PRODUÇÃO`, com artefato reproduzível e staging autenticado/noindex.

Alternativas: deploy direto em produção; staging público; build manual na VPS; ambiente único compartilhado.

Consequência: reduz risco de vazamento, indexação indevida e divergência entre ambientes.

Fase bloqueada: Fase 1A.

Status: `APROVADO`.

Responsável: infraestrutura e arquitetura.

Prazo limite: antes da Fase 1A.

## ADR-011 — identificadores e tipos fundamentais

Contexto: o modelo de dados precisa fechar tipos fundamentais antes do desenho físico do banco V3, sem antecipar migrations nesta fase.

Decisão: APROVADO.

- PKs internas usam UUID v7. A geração começa na aplicação, com compatibilidade futura para geração ou validação no banco quando a extensão/estratégia PostgreSQL estiver aprovada.
- Valores monetários usam `numeric(12,2)` como padrão. Campos que precisarem de precisão maior devem justificar a exceção no desenho físico antes da migration.
- Créditos usam `integer` para quantidade individual e `bigint` para agregados, relatórios ou somatórios que possam exceder o limite operacional seguro.
- E-mail case-insensitive é tratado por `email_normalizado` em lowercase, com unicidade explícita.
- Telefone usa `telefone_normalizado` em E.164 quando possível; quando a origem não permitir normalização confiável, o registro vira pendência de importação em vez de receber valor falso.
- Todos os instantes usam `timestamptz`; aplicação e banco trabalham em UTC.
- Datas civis sem horário usam `date`.
- Tabelas mutáveis críticas possuem coluna `versao` para concorrência otimista.
- Exclusão lógica só é usada quando houver justificativa histórica, jurídica ou operacional. Caso contrário, o desenho deve preferir estado, arquivamento, anonimização ou exclusão física controlada.
- Dados financeiros têm tolerância zero para divergência: saldo projetado não prova pagamento e toda movimentação financeira precisa ser reconciliável.

Justificativa:

- UUID v7 preserva identificadores opacos e reduz vazamento de cardinalidade, mantendo melhor localidade temporal que UUID v4.
- `numeric(12,2)` evita erro de ponto flutuante e atende o domínio monetário inicial; exceções ficam explícitas.
- Créditos inteiros evitam arredondamento e simplificam a razão financeira.
- Campos normalizados tornam unicidade e busca mais previsíveis sem depender de variação de collation.
- `versao` reduz perda de atualização em entidades mutáveis sem exigir lock pessimista como padrão.

Alternativas: UUID; bigint sequencial; ULID; catálogo próprio de identificadores; `numeric` com precisão fixa por domínio; tipos inteiros diferentes para créditos.

Consequência: a Fase 1C pode fechar o desenho físico-conceitual do banco, índices e constraints planejadas. A criação executável do schema permanece bloqueada até a Fase 1D.

Fase bloqueada: nenhuma. A Fase 1D permanece bloqueada até revisão do blueprint, tabelas, índices e constraints.

Status: `APROVADO`.

Responsável: arquitetura de dados.

Prazo limite: concluído na Fase 1C.

## Registro pré-Fase 1D — migrations Flyway

Contexto: a Fase 1D criará migrations reais, mas a preparação precisa separar decisões já aprovadas de pendências técnicas que ainda exigem revisão.

Decisão: a Fase 1C.1 registra plano, ordem planejada e decisões pré-1D em documentos próprios, sem criar SQL. A Fase 1D deve usar esses documentos como entrada obrigatória.

Documentos:

- `docs/v3/28-plano-migrations-flyway.md`
- `docs/v3/29-ordem-migrations-v3.md`
- `docs/v3/30-decisoes-pre-fase-1d.md`
- `docs/v3/31-checklist-pre-fase-1d.md`

Pendências declaradas para a Fase 1D:

- implementação concreta de UUID v7 em Java 17;
- extensão UUID no PostgreSQL, se houver;
- aprovação de `pg_trgm`;
- aprovação de `unaccent`;
- CHECK constraint versus tabela de catálogo por enum crítico;
- `public` único versus schemas separados.

Status: `PREPARACAO_CONCLUIDA_SEM_SQL`.
