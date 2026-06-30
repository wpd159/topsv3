# Relatório de validação do skeleton local

- Total de verificações: 107
- Verificações OK: 107
- Verificações com falha: 0

| Verificação | Resultado | Detalhe |
| --- | --- | --- |
| arquivo obrigatório backend/pom.xml | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/java/br/com/topsdojob/v3/TopsDoJobBackendApplication.java | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/java/br/com/topsdojob/v3/platform/health/HealthController.java | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/java/br/com/topsdojob/v3/platform/request/RequestIdContext.java | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/java/br/com/topsdojob/v3/platform/request/RequestIdFilter.java | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/java/br/com/topsdojob/v3/platform/error/ApiErrorCode.java | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/java/br/com/topsdojob/v3/platform/error/ApiErrorResponse.java | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/java/br/com/topsdojob/v3/platform/error/GlobalExceptionHandler.java | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/java/br/com/topsdojob/v3/platform/web/LocalCorsConfiguration.java | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/resources/application.yml | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/resources/application-local.yml | OK | presença no skeleton |
| arquivo obrigatório backend/src/main/resources/application-test.yml | OK | presença no skeleton |
| arquivo obrigatório frontend/package.json | OK | presença no skeleton |
| arquivo obrigatório frontend/next.config.mjs | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/layout.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/health/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/anuncios/[slug]/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/robots.ts | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/sitemap.ts | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/sobre/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/como-funciona/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/seguranca/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/anunciar/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/perguntas-frequentes/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/moderacao/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/anuncios/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/usuarios/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/midia/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/premium/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/creditos/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/financeiro/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/seo/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/banners/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/comercial/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/suporte/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/backup/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/app/admin/auditoria/page.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/public/llms.txt | OK | presença no skeleton |
| arquivo obrigatório frontend/src/lib/api/client.ts | OK | presença no skeleton |
| arquivo obrigatório frontend/src/lib/config/publicEnv.ts | OK | presença no skeleton |
| arquivo obrigatório frontend/src/lib/seo/localSeo.ts | OK | presença no skeleton |
| arquivo obrigatório frontend/src/lib/seo/schemaPlaceholders.ts | OK | presença no skeleton |
| arquivo obrigatório frontend/src/modules/admin/shell/adminModules.ts | OK | presença no skeleton |
| arquivo obrigatório frontend/src/modules/admin/shell/AdminShell.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/modules/admin/shell/AdminModuleCard.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/modules/admin/shell/AdminPlaceholderPage.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/modules/public/skeleton/PublicRouteShell.tsx | OK | presença no skeleton |
| arquivo obrigatório frontend/src/modules/public/skeleton/SeoPlaceholder.tsx | OK | presença no skeleton |
| arquivo obrigatório contracts/openapi/topsdojob-v3-local.yaml | OK | presença no skeleton |
| arquivo obrigatório infra/local/.env.local.example | OK | presença no skeleton |
| arquivo obrigatório infra/local/docker-compose.local.yml | OK | presença no skeleton |
| ausência de backend/src/main/resources/db/migration | OK | fora do escopo da Fase 1B |
| ausência de backend/src/main/java/br/com/topsdojob/v3/domain | OK | fora do escopo da Fase 1B |
| ausência de backend/src/main/java/br/com/topsdojob/v3/repository | OK | fora do escopo da Fase 1B |
| ausência de backend/src/main/java/br/com/topsdojob/v3/service | OK | fora do escopo da Fase 1B |
| nenhum SQL criado | OK | arquivos .sql encontrados: 0 |
| compose sem latest | OK | imagens Docker devem estar pinadas |
| compose usa env local | OK | Compose consome infra/local/.env.local.example |
| compose usa COMPOSE_PROJECT_NAME | OK | nome do projeto local usa variável padrão do Docker Compose |
| healthcheck PostgreSQL usa variáveis do container | OK | healthcheck acompanha POSTGRES_USER e POSTGRES_DB |
| env contém EFI_PIX_MOCK_MODE=true | OK | variável local obrigatória |
| env contém APP_ENV=local | OK | variável local obrigatória |
| env contém APP_CANONICAL_DOMAIN=http://localhost | OK | variável local obrigatória |
| env contém COMPOSE_PROJECT_NAME=topsv3-local | OK | nome local padronizado para Docker Compose |
| env não contém TOPSV3_LOCAL_PROJECT | OK | variável paralela removida |
| env local contém senha PostgreSQL | OK | variável consumida pelo Compose local |
| env local contém usuário MinIO | OK | variável consumida pelo Compose local |
| env local contém senha MinIO | OK | variável consumida pelo Compose local |
| backend usa Java 17 LTS | OK | padrão conservador da Fase 1C |
| endpoint /api/health" presente | OK | health check local obrigatório |
| endpoint /readiness" presente | OK | health check local obrigatório |
| endpoint /liveness" presente | OK | health check local obrigatório |
| HealthController responde UP | OK | status padronizado em contrato local |
| HealthController inclui requestId | OK | contrato transversal da Fase 1C.3 |
| OpenAPI documenta status UP | OK | contrato coerente com HealthController |
| OpenAPI documenta X-Request-Id | OK | request id transversal documentado |
| OpenAPI documenta ApiErrorResponse | OK | erro padronizado documentado |
| robots local bloqueia indexação | OK | ambiente local deve ficar noindex |
| sitemap local usa localUrl | OK | URLs locais devem vir do helper seguro |
| SEO local usa NEXT_PUBLIC_CANONICAL_DOMAIN | OK | canonical local usa configuração pública segura |
| SEO local força noindex | OK | páginas skeleton não devem indexar |
| frontend skeleton sem domínio de produção | OK | canonical de produção proibido no local |
| frontend skeleton sem backend de domínio | OK | Fase 1C.4 não chama API de domínio |
| frontend institucional sem chamada externa | OK | Fase 1C.5 não acessa IA ou API externa |
| llms.txt identifica o projeto | OK | guia institucional local |
| llms.txt preserva rotas públicas | OK | rotas públicas críticas |
| llms.txt sem domínio de produção | OK | guia local não deve emitir produção |
| admin pages usam metadata noindex | OK | skeletonMetadata aplica robots noindex |
| admin shell sem chamada externa | OK | admin skeleton não chama backend nem API externa |
| admin shell sem ações reais | OK | admin skeleton não expõe ação funcional |
| admin module moderacao mapeado | OK | módulo admin previsto no SDD |
| admin module anuncios mapeado | OK | módulo admin previsto no SDD |
| admin module usuarios mapeado | OK | módulo admin previsto no SDD |
| admin module midia mapeado | OK | módulo admin previsto no SDD |
| admin module premium mapeado | OK | módulo admin previsto no SDD |
| admin module creditos mapeado | OK | módulo admin previsto no SDD |
| admin module financeiro mapeado | OK | módulo admin previsto no SDD |
| admin module seo mapeado | OK | módulo admin previsto no SDD |
| admin module banners mapeado | OK | módulo admin previsto no SDD |
| admin module comercial mapeado | OK | módulo admin previsto no SDD |
| admin module suporte mapeado | OK | módulo admin previsto no SDD |
| admin module backup mapeado | OK | módulo admin previsto no SDD |
| admin module auditoria mapeado | OK | módulo admin previsto no SDD |
