# SDD - Indice de rastreabilidade

Este indice conecta os temas centrais do SDD aos documentos, contratos, scripts e evidencias locais existentes.

## Documentos centrais

- SDD central: `docs/v3/SDD.md`
- Decisoes consolidadas: `docs/v3/SDD-decisoes-consolidadas.md`
- Pendencias e gates: `docs/v3/SDD-pendencias-gates.md`
- Plano de fases: `docs/v3/11-plano-execucao-fases.md`

## Arquitetura local

- Ambiente local: `docs/v3/18-fase-1a-ambiente-local.md`
- Runbook local: `docs/v3/19-runbook-local.md`
- Skeleton backend/frontend: `docs/v3/21-skeleton-fase-1b.md`
- Build local: `docs/v3/98-build-local-backend-frontend.md`
- Checklist build: `docs/v3/99-checklist-build-local.md`

## Contratos e padroes transversais

- Erros, paginacao e filtros: `docs/v3/32-padroes-api-erros-paginacao.md`
- Logs, auditoria e observabilidade: `docs/v3/33-padroes-logs-auditoria-observabilidade.md`
- Checklist transversal: `docs/v3/34-checklist-padroes-transversais.md`
- OpenAPI local: `contracts/openapi/topsdojob-v3-local.yaml`

## Rotas publicas e SEO

- Rotas publicas skeleton: `docs/v3/35-rotas-publicas-preservadas.md`
- SEO local: `docs/v3/36-seo-local-seguro.md`
- SEO prioridade central: `docs/v3/SEO-prioridade-central-v3.md`
- Mapa de preservacao de URLs: `docs/v3/SEO-mapa-preservacao-urls.md`
- Plano cidade/bairro: `docs/v3/SEO-plano-cidade-bairro.md`
- Checklist SEO cutover: `docs/v3/SEO-checklist-cutover.md`
- Baseline Search Console: `docs/v3/SEO-baseline-search-console.md`
- Inventario SEO sanitizado: `docs/v3/SEO-inventario-producao-sanitizado.md`
- Mapa preservacao URLs V3: `docs/v3/SEO-mapa-preservacao-urls-v3.md`
- Plano 301: `docs/v3/SEO-plano-redirecionamentos-301.md`
- Canonical/sitemap/robots cutover: `docs/v3/SEO-canonical-sitemap-robots-cutover.md`
- Template Search Console: `docs/v3/SEO-baseline-search-console-template.md`
- Matriz risco trafego: `docs/v3/SEO-matriz-risco-perda-trafego.md`
- Cidades prioritarias: `docs/v3/SEO-cidades-prioritarias.md`
- Validacao de rotas e SEO: `docs/v3/51-validacao-rotas-publicas-seo-local.md`
- Checklist rotas/SEO: `docs/v3/52-checklist-validacao-rotas-seo.md`
- Script: `scripts/local/validar-rotas-publicas-seo-local.ps1`
- Script SEO publico: `scripts/local/validar-seo-publico-local.ps1`
- Script inventario SEO publico: `scripts/local/seo-inventario-producao-publica.ps1`
- Script validacao mapa SEO: `scripts/local/validar-mapa-preservacao-seo-local.ps1`

## Visual e mobile

- Preservacao visual: `docs/v3/48-preservacao-visual-atual.md`
- Inventario visual: `docs/v3/49-inventario-visual-atual.md`
- Checklist visual: `docs/v3/50-checklist-preservacao-visual.md`
- Diretriz mobile: `docs/v3/161-diretriz-ui-mobile-bloco-21.md`
- Checklist mobile: `docs/v3/162-checklist-validacao-mobile-bloco-21.md`
- Script: `scripts/local/validar-ui-mobile-estatica.ps1`
- Evidencias Bloco 21: `docs/v3/evidencias/bloco-21/`
- Evidencias Bloco 26.1: `docs/v3/evidencias/bloco-26-1/`

## Banco e migrations

- Relatorio Fase 1D: `docs/v3/58-fase-1d-migrations-flyway.md`
- Matriz de tabelas: `docs/v3/59-matriz-tabelas-migrations.md`
- Checklist auditoria Pro: `docs/v3/60-checklist-auditoria-pro-migrations.md`
- Pendencias schema: `docs/v3/61-pendencias-e-decisoes-schema-1d.md`
- Relatorio final Fase 1D: `docs/v3/67-relatorio-final-fase-1d.md`
- Checklist final Fase 1D: `docs/v3/68-checklist-final-fase-1d.md`
- Script estatico: `scripts/local/validar-migrations-sql-estatico.ps1`
- Script descartavel: `scripts/local/validar-migrations-postgres-descartavel.ps1`

## Importador

- Estrutura importador: `docs/v3/69-fase-2a-importador-saneador-estrutura.md`
- Pacote de entrada: `docs/v3/72-pacote-entrada-importacao.md`
- Dicionario: `docs/v3/75-dicionario-campos-importacao.md`
- Saneamento: `docs/v3/78-regras-saneamento-importacao.md`
- Plano e dry-run: `docs/v3/81-plano-execucao-importacao.md`
- Gate fonte real: `docs/v3/84-gate-fonte-real-autorizada-importacao.md`
- Dossie transicao: `docs/v3/87-dossie-transicao-revisao-pro.md`
- Script: `scripts/local/validar-fonte-importacao-local.ps1`

## Backend, JPA e API publica

- Dominio base: `docs/v3/90-bloco-3-backend-dominio-base.md`
- Matriz entidade/tabela: `docs/v3/91-matriz-entidade-tabela-backend.md`
- Persistencia JPA: `docs/v3/94-bloco-4-persistencia-jpa-base.md`
- API publica: `docs/v3/100-bloco-5-api-publica-minima.md`
- Frontend publico/API local: `docs/v3/104-bloco-6-frontend-api-publica-local.md`
- E2E local descartavel: `docs/v3/108-bloco-7-e2e-local-descartavel.md`

## Idade, midia, metricas e WhatsApp

- Metricas e WhatsApp: `docs/v3/112-bloco-8-metricas-publicas-whatsapp-local.md`
- Confirmacao de idade: `docs/v3/116-bloco-9-confirmacao-idade-local.md`
- UX conteudo bloqueado: `docs/v3/120-bloco-10-ux-conteudo-bloqueado.md`
- Midia/CDN segura: `docs/v3/126-bloco-11-midia-publica-cdn-segura.md`

## Admin, RBAC e moderacao

- Auth admin: `docs/v3/129-bloco-12-auth-admin-local.md`
- Hardening auth: `docs/v3/133-bloco-13-hardening-auth-admin.md`
- Admin read-only: `docs/v3/137-bloco-14-admin-readonly-local.md`
- Admin detalhado: `docs/v3/141-bloco-15-admin-readonly-detalhado.md`
- Moderacao minima: `docs/v3/145-bloco-16-moderacao-funcional-local.md`
- Hardening moderacao: `docs/v3/149-bloco-16-1-hardening-moderacao.md`
- Ajustes e remessa: `docs/v3/152-bloco-17-solicitar-ajuste-remeter-revisao.md`
- Correcao ajuste: `docs/v3/155-bloco-17-1-correcao-ajuste.md`

## Outbox e comunicacoes

- Outbox read-only: `docs/v3/156-bloco-18-outbox-readonly-preview.md`
- Simulacao outbox: `docs/v3/157-bloco-19-simulacao-outbox-local.md`
- Hardening outbox: `docs/v3/158-bloco-19-1-hardening-outbox-local.md`
- Templates e preview: `docs/v3/159-bloco-20-templates-preview-outbox.md`

## Financeiro, Premium e desempenho

- Premium: `docs/v3/164-bloco-22-premium-beneficios-local.md`
- Creditos: `docs/v3/167-bloco-23-creditos-ledger-readonly.md`
- Pagamentos: `docs/v3/170-bloco-24-pagamentos-readonly-local.md`
- Desempenho: `docs/v3/174-bloco-25-prova-resultado-readonly.md`

## Anuncie gratis

- Funil local: `docs/v3/178-bloco-26-funil-anuncie-gratis-local.md`
- Politica cadastro gratis: `docs/v3/179-politica-cadastro-anuncio-gratis.md`
- Checklist Bloco 26: `docs/v3/180-checklist-bloco-26-anuncie-gratis.md`
- Correcao visual e SDD mestre: `docs/v3/181-bloco-26-1-correcao-visual-sdd.md`
- Wizard, SEO e Premium: `docs/v3/182-bloco-26-2-wizard-seo-premium.md`
- Checklist Bloco 26.2: `docs/v3/183-checklist-bloco-26-2-wizard-seo-premium.md`
- Relatorio Bloco 27: `docs/v3/184-bloco-27-seo-publico-cidade-bairro.md`
- Checklist Bloco 27: `docs/v3/185-checklist-bloco-27-seo-publico-cidade-bairro.md`
- Relatorio Bloco 27.1: `docs/v3/186-bloco-27-1-correcao-visual-seo.md`
- Checklist Bloco 27.1: `docs/v3/187-checklist-bloco-27-1-correcao-visual-seo.md`
- Relatorio Bloco 28: `docs/v3/188-bloco-28-inventario-seo-preservacao.md`
- Checklist Bloco 28: `docs/v3/189-checklist-bloco-28-inventario-seo-preservacao.md`
- Auditoria SEO publica de producao: `docs/v3/SEO-auditoria-producao-publica.md`
- Padroes de metadata publica: `docs/v3/SEO-padroes-metadata-publica.md`
- Linkagem interna V3: `docs/v3/SEO-linkagem-interna-v3.md`
- Evidencias Bloco 26: `docs/v3/evidencias/bloco-26/`
- Evidencias Bloco 26.1: `docs/v3/evidencias/bloco-26-1/`
- Evidencias Bloco 26.2: `docs/v3/evidencias/bloco-26-2/`
- Evidencias Bloco 27: `docs/v3/evidencias/bloco-27/`
- Evidencias Bloco 27.1: `docs/v3/evidencias/bloco-27-1/`
- Evidencias Bloco 28: `docs/v3/evidencias/bloco-28/`

## Validacoes principais

- Toolchain: `scripts/local/diagnosticar-toolchain-local.ps1`
- Build: `scripts/local/validar-build-local.ps1`
- E2E descartavel: `scripts/local/validar-e2e-local-descartavel.ps1`
- API publica: `scripts/local/validar-api-publica-local.ps1`
- Persistencia JPA estatica: `scripts/local/validar-persistencia-jpa-estatica.ps1`
- UI mobile estatica: `scripts/local/validar-ui-mobile-estatica.ps1`
- SEO publico local: `scripts/local/validar-seo-publico-local.ps1`
- Mapa preservacao SEO: `scripts/local/validar-mapa-preservacao-seo-local.ps1`
- Layout publico renderizado: `scripts/local/validar-layout-publico-renderizado.ps1`
- Codificacao: `scripts/security/verificar-codificacao.ps1`
- Arquivos proibidos: `scripts/security/verificar-arquivos-proibidos.ps1`
- Secrets: `scripts/security/verificar-segredos.ps1`

## Pacotes

- Inventario inicial: `scripts/entrega/criar-inventario-inicial.ps1`
- Pacote de revisao: `scripts/entrega/criar-pacote-revisao.ps1`
- ZIPs finais ficam fora do repositorio, na Area de Trabalho.
