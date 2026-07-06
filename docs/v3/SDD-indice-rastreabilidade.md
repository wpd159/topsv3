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
- Protocolo copia sanitizada: `docs/v3/PRODUCAO-copia-sanitizada-protocolo.md`
- Sanitizacao de dados: `docs/v3/PRODUCAO-sanitizacao-dados.md`
- Restore local/staging: `docs/v3/PRODUCAO-restore-local-staging.md`
- SEO com dados sanitizados: `docs/v3/SEO-validacao-com-dados-sanitizados.md`
- Matriz campos sanitizados: `docs/v3/DADOS-sanitizados-matriz-campos.md`
- Riscos privacidade: `docs/v3/DADOS-producao-riscos-privacidade.md`

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
- Script Flyway real local: `scripts/local/validar-flyway-real-local.ps1`

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
- Relatorio Bloco 29: `docs/v3/190-bloco-29-restore-sanitizado-producao.md`
- Checklist Bloco 29: `docs/v3/191-checklist-bloco-29-restore-sanitizado-producao.md`
- Relatorio Bloco 29.1: `docs/v3/192-bloco-29-1-restore-sanitizacao-validacao.md`
- Checklist Bloco 29.1: `docs/v3/193-checklist-bloco-29-1-restore-sanitizacao-validacao.md`
- Relatorio Bloco 29.2: `docs/v3/194-bloco-29-2-restore-sanitizacao-executado.md`
- Checklist Bloco 29.2: `docs/v3/195-checklist-bloco-29-2-restore-sanitizacao-executado.md`
- Relatorio Bloco 29.3: `docs/v3/196-bloco-29-3-restore-sanitizacao-docker-local.md`
- Checklist Bloco 29.3: `docs/v3/197-checklist-bloco-29-3-restore-sanitizacao-docker-local.md`
- Relatorio Bloco 29.4: `docs/v3/198-bloco-29-4-diagnostico-restore-raw.md`
- Checklist Bloco 29.4: `docs/v3/199-checklist-bloco-29-4-diagnostico-restore-raw.md`
- Relatorio Bloco 29.5: `docs/v3/200-bloco-29-5-restore-quarentena-sem-postdata.md`
- Checklist Bloco 29.5: `docs/v3/201-checklist-bloco-29-5-restore-quarentena-sem-postdata.md`
- Relatorio Bloco 29.6: `docs/v3/202-bloco-29-6-consolidacao-diagnostico-quarentena.md`
- Checklist Bloco 29.6: `docs/v3/203-checklist-bloco-29-6-consolidacao-diagnostico-quarentena.md`
- Relatorio Bloco 30: `docs/v3/204-bloco-30-retomada-sem-dados-reais.md`
- Checklist Bloco 30: `docs/v3/205-checklist-bloco-30-retomada-sem-dados-reais.md`
- Base sintetica local: `docs/v3/DADOS-sinteticos-local.md`
- Fixture sintetica local: `backend/src/test/resources/fixtures/v3-dados-sinteticos.json`
- Checkpoint Blocos 29 a 29.6: `36b94c6`
- Auditoria SEO publica de producao: `docs/v3/SEO-auditoria-producao-publica.md`
- Padroes de metadata publica: `docs/v3/SEO-padroes-metadata-publica.md`
- Linkagem interna V3: `docs/v3/SEO-linkagem-interna-v3.md`
- Evidencias Bloco 26: `docs/v3/evidencias/bloco-26/`
- Evidencias Bloco 26.1: `docs/v3/evidencias/bloco-26-1/`
- Evidencias Bloco 26.2: `docs/v3/evidencias/bloco-26-2/`
- Evidencias Bloco 27: `docs/v3/evidencias/bloco-27/`
- Evidencias Bloco 27.1: `docs/v3/evidencias/bloco-27-1/`
- Evidencias Bloco 28: `docs/v3/evidencias/bloco-28/`
- Evidencias Bloco 29: `docs/v3/evidencias/bloco-29/`
- Evidencias Bloco 29.1: `docs/v3/evidencias/bloco-29-1/`
- Evidencias Bloco 29.2: `docs/v3/evidencias/bloco-29-2/`
- Evidencias Bloco 29.3: `docs/v3/evidencias/bloco-29-3/`
- Evidencias Bloco 29.4: `docs/v3/evidencias/bloco-29-4/`
- Evidencias Bloco 29.5: `docs/v3/evidencias/bloco-29-5/`
- Evidencias Bloco 29.6: `docs/v3/evidencias/bloco-29-6/`
- Evidencias Bloco 30: `docs/v3/evidencias/bloco-30/`
- Relatorio Bloco 31: `docs/v3/206-bloco-31-validacao-sintetica-api-seo.md`
- Checklist Bloco 31: `docs/v3/207-checklist-bloco-31-validacao-sintetica-api-seo.md`
- Evidencias Bloco 31: `docs/v3/evidencias/bloco-31/`
- Relatorio Bloco 31.1: `docs/v3/208-bloco-31-1-hardening-validadores-sinteticos.md`
- Checklist Bloco 31.1: `docs/v3/209-checklist-bloco-31-1-hardening-validadores-sinteticos.md`
- Evidencias Bloco 31.1: `docs/v3/evidencias/bloco-31-1/`
- E2E sintetico: `scripts/local/validar-e2e-sintetico-local.ps1`
- API sintetica: `scripts/local/validar-api-publica-sintetica-local.ps1`
- SEO sintetico: `scripts/local/validar-seo-sintetico-local.ps1`
- Checkpoint Bloco 30: `2acc60b`
- Relatorio Bloco 32: `docs/v3/210-bloco-32-auditoria-renderizada-publica-sintetica.md`
- Checklist Bloco 32: `docs/v3/211-checklist-bloco-32-auditoria-renderizada-publica-sintetica.md`
- Evidencias Bloco 32: `docs/v3/evidencias/bloco-32/`
- Relatorio Bloco 32.1: `docs/v3/212-bloco-32-1-correcao-texto-tecnico-publico.md`
- Checklist Bloco 32.1: `docs/v3/213-checklist-bloco-32-1-correcao-texto-tecnico-publico.md`
- Evidencias Bloco 32.1: `docs/v3/evidencias/bloco-32-1/`
- Auditoria renderizada sintetica: `scripts/local/validar-publico-renderizado-sintetico-local.ps1`
- Checkpoint Blocos 31/31.1: `790188b`
- Checkpoint Bloco 32.1: `f6189f0`
- Relatorio Bloco 33: `docs/v3/214-bloco-33-validacao-wizard-anunciar-sintetico.md`
- Checklist Bloco 33: `docs/v3/215-checklist-bloco-33-validacao-wizard-anunciar-sintetico.md`
- Evidencias Bloco 33: `docs/v3/evidencias/bloco-33/`
- Wizard Anuncie gratis sintetico: `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`
- Relatorio Bloco 33.1: `docs/v3/216-bloco-33-1-correcao-textual-wizard.md`
- Checklist Bloco 33.1: `docs/v3/217-checklist-bloco-33-1-correcao-textual-wizard.md`
- Evidencias Bloco 33.1: `docs/v3/evidencias/bloco-33-1/`
- Checkpoint Blocos 33/33.1: `b7f5f98`
- Relatorio Bloco 34: `docs/v3/218-bloco-34-paridade-wizard-producao.md`
- Checklist Bloco 34: `docs/v3/219-checklist-bloco-34-paridade-wizard-producao.md`
- Evidencias Bloco 34: `docs/v3/evidencias/bloco-34/`
- Prints producao Bloco 34: `docs/v3/evidencias/bloco-34/prints/producao/`
- Prints V3 local Bloco 34: `docs/v3/evidencias/bloco-34/prints/v3-local/`
- Checkpoint Bloco 34 corrigido: `9b677ea`
- Relatorio Bloco 35: `docs/v3/220-bloco-35-admin-moderacao-sintetica.md`
- Checklist Bloco 35: `docs/v3/221-checklist-bloco-35-admin-moderacao-sintetica.md`
- Evidencias Bloco 35: `docs/v3/evidencias/bloco-35/`
- Prints admin/moderacao Bloco 35: `docs/v3/evidencias/bloco-35/prints/`
- Validador admin/moderacao sintetica: `scripts/local/validar-admin-moderacao-sintetica-local.ps1`
- Checkpoint Bloco 35: `00e1a02`
- Relatorio Bloco 36: `docs/v3/222-bloco-36-premium-beneficios-sintetico.md`
- Checklist Bloco 36: `docs/v3/223-checklist-bloco-36-premium-beneficios-sintetico.md`
- Evidencias Bloco 36: `docs/v3/evidencias/bloco-36/`
- Prints Premium/beneficios Bloco 36: `docs/v3/evidencias/bloco-36/prints/`
- Validador Premium/beneficios sintetico: `scripts/local/validar-premium-beneficios-sintetico-local.ps1`
- Checkpoint Bloco 36: `e031ea3`
- Relatorio Bloco 37: `docs/v3/224-bloco-37-limpeza-copy-visivel-sintetica.md`
- Checklist Bloco 37: `docs/v3/225-checklist-bloco-37-limpeza-copy-visivel-sintetica.md`
- Evidencias Bloco 37: `docs/v3/evidencias/bloco-37/`
- Relatorio de limpeza de copy: `docs/v3/evidencias/bloco-37/relatorio-limpeza-copy-visivel.md`
- Validadores de copy renderizada: `scripts/local/validar-publico-renderizado-sintetico-local.ps1`, `scripts/local/validar-admin-moderacao-sintetica-local.ps1`, `scripts/local/validar-premium-beneficios-sintetico-local.ps1`, `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`
- Checkpoint Bloco 37 corrigido: `a6f431f`
- Relatorio Bloco 38: `docs/v3/226-bloco-38-polimento-status-publico.md`
- Checklist Bloco 38: `docs/v3/227-checklist-bloco-38-polimento-status-publico.md`
- Evidencias Bloco 38: `docs/v3/evidencias/bloco-38/`
- Checkpoint Bloco 38: `587e2df`
- Relatorio Bloco 39: `docs/v3/228-bloco-39-agegate-whatsapp-sintetico.md`
- Checklist Bloco 39: `docs/v3/229-checklist-bloco-39-agegate-whatsapp-sintetico.md`
- Evidencias Bloco 39: `docs/v3/evidencias/bloco-39/`
- Validador Age Gate/WhatsApp sintetico: `scripts/local/validar-agegate-whatsapp-sintetico-local.ps1`
- Checkpoint Bloco 39: `a3e92c0`
- Relatorio Bloco 40: `docs/v3/230-bloco-40-midia-publica-sintetica.md`
- Checklist Bloco 40: `docs/v3/231-checklist-bloco-40-midia-publica-sintetica.md`
- Evidencias Bloco 40: `docs/v3/evidencias/bloco-40/`
- Validador midia publica sintetica: `scripts/local/validar-midia-publica-sintetica-local.ps1`
- Relatorio Bloco 41: `docs/v3/232-bloco-41-consolidacao-mvp-local-sintetico.md`
- Checklist Bloco 41: `docs/v3/233-checklist-bloco-41-consolidacao-mvp-local-sintetico.md`
- Evidencias Bloco 41: `docs/v3/evidencias/bloco-41/`
- Checkpoint Bloco 40 corrigido: `f67880a`
- Relatorio Bloco 42: `docs/v3/234-bloco-42-prontidao-homologacao-cutover.md`
- Checklist Bloco 42: `docs/v3/235-checklist-bloco-42-prontidao-homologacao-cutover.md`
- Matriz de prontidao: `docs/v3/HOMOLOGACAO-matriz-prontidao.md`
- Ordem dos proximos blocos: `docs/v3/HOMOLOGACAO-ordem-proximos-blocos.md`
- Evidencias Bloco 42: `docs/v3/evidencias/bloco-42/`
- Checkpoint Bloco 41: `46ed655`
- Relatorio Bloco 43: `docs/v3/236-bloco-43-gitleaks-toolchain.md`
- Checklist Bloco 43: `docs/v3/237-checklist-bloco-43-gitleaks-toolchain.md`
- Evidencias Bloco 43: `docs/v3/evidencias/bloco-43/`
- Checkpoint Bloco 42: `09ffcd3`
- Controle de pacote com objetivo obrigatorio: `scripts/entrega/criar-pacote-revisao.ps1`
- Relatorio Bloco 44: `docs/v3/238-bloco-44-gitleaks-real.md`
- Checklist Bloco 44: `docs/v3/239-checklist-bloco-44-gitleaks-real.md`
- Evidencias Bloco 44: `docs/v3/evidencias/bloco-44/`
- Checkpoint Bloco 43: `71404a3`
- Scanner de segredos com PATH recarregado: `scripts/security/verificar-segredos.ps1`
- Relatorio Bloco 45: `docs/v3/240-bloco-45-flyway-real-local.md`
- Checklist Bloco 45: `docs/v3/241-checklist-bloco-45-flyway-real-local.md`
- Evidencias Bloco 45: `docs/v3/evidencias/bloco-45/`
- Checkpoint Bloco 44: `0603a59`
- Validador Flyway real local: `scripts/local/validar-flyway-real-local.ps1`
- Relatorio Bloco 46: `docs/v3/242-bloco-46-flyway-real-instalacao-validacao.md`
- Checklist Bloco 46: `docs/v3/243-checklist-bloco-46-flyway-real-instalacao-validacao.md`
- Evidencias Bloco 46: `docs/v3/evidencias/bloco-46/`
- Checkpoint Bloco 45: `282802d`
- Relatorio Bloco 47: `docs/v3/244-bloco-47-flyway-docker-local.md`
- Checklist Bloco 47: `docs/v3/245-checklist-bloco-47-flyway-docker-local.md`
- Evidencias Bloco 47: `docs/v3/evidencias/bloco-47/`
- Checkpoint Bloco 46: `220c2ba`
- Validador Auth/RBAC/CSRF local: `scripts/local/validar-auth-rbac-csrf-local.ps1`
- Relatorio Bloco 48: `docs/v3/246-bloco-48-auth-rbac-csrf-local.md`
- Checklist Bloco 48: `docs/v3/247-checklist-bloco-48-auth-rbac-csrf-local.md`
- Evidencias Bloco 48: `docs/v3/evidencias/bloco-48/`
- Checkpoint Bloco 47: `7791d11`
- Relatorio de polimento de status publico: `docs/v3/evidencias/bloco-38/relatorio-polimento-status-publico.md`
- Validadores de status publico: `scripts/local/validar-publico-renderizado-sintetico-local.ps1`, `scripts/local/validar-premium-beneficios-sintetico-local.ps1`

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
- Admin/moderacao sintetica: `scripts/local/validar-admin-moderacao-sintetica-local.ps1`
- Localizar backup autorizado: `scripts/local/producao-localizar-backup-autorizado.ps1`
- Restore local isolado: `scripts/local/producao-restore-local-isolado.ps1`
- Sanitizar DB local: `scripts/local/producao-sanitizar-db-local.ps1`
- Validar dados sanitizados: `scripts/local/validar-dados-producao-sanitizados-local.ps1`
- Validar SEO com dados sanitizados: `scripts/local/validar-seo-com-dados-sanitizados-local.ps1`
- Gerar dados sinteticos V3: `scripts/local/gerar-dados-sinteticos-v3-local.ps1`
- Validar dados sinteticos V3: `scripts/local/validar-dados-sinteticos-v3-local.ps1`
- Validar wizard Anuncie gratis sintetico: `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`
- Validar Premium/beneficios sintetico: `scripts/local/validar-premium-beneficios-sintetico-local.ps1`
- Validar midia publica sintetica: `scripts/local/validar-midia-publica-sintetica-local.ps1`
- Validar Flyway real local: `scripts/local/validar-flyway-real-local.ps1`
- Validar copy renderizada publica/admin: `scripts/local/validar-publico-renderizado-sintetico-local.ps1`, `scripts/local/validar-admin-moderacao-sintetica-local.ps1`
- Codificacao: `scripts/security/verificar-codificacao.ps1`
- Arquivos proibidos: `scripts/security/verificar-arquivos-proibidos.ps1`
- Secrets: `scripts/security/verificar-segredos.ps1`

## Pacotes

- Inventario inicial: `scripts/entrega/criar-inventario-inicial.ps1`
- Pacote de revisao: `scripts/entrega/criar-pacote-revisao.ps1`
- ZIPs finais ficam fora do repositorio, na Area de Trabalho.
