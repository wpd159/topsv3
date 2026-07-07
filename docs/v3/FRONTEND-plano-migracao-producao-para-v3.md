# FRONTEND - Plano de migracao do frontend de producao para a V3

## Decisao base

A V3 deve usar `C:\clone\topsdojob-frontend` como base visual e funcional do frontend publico, preservando a experiencia real de producao e substituindo gradualmente as integracoes por adapters seguros da V3.

O frontend atual de `C:\topsv3\frontend` deixa de ser a base visual principal. Ele permanece como fonte de contratos locais, tipos, validadores, rotas ja integradas ao backend V3, configuracao HML, documentacao e testes sinteticos.

## Principios

- Preservar visual e UX atual da producao.
- Adaptar integracoes para o backend V3, sem reaproveitar endpoints reais antigos.
- Manter noindex/nofollow em HML ate decisao explicita.
- Manter dados sinteticos locais durante a migracao.
- Nao copiar credenciais, `.env`, dumps, logs brutos, storage keys, tokens ou payloads sensiveis.
- Nao reintroduzir scroll lock, carrossel automatico, botao flutuante indevido ou dependencia de producao.
- Nao executar upload real, Pix/Efi real, webhook real, checkout real ou WhatsApp real.

## Fonte visual principal

Pasta base: `C:\clone\topsdojob-frontend`

Partes candidatas a preservacao:

- `src/app/layout.tsx`
- `src/app/globals.css`
- `src/app/page.tsx`
- `src/app/(public-routes)/acompanhantes/**`
- `src/app/(public-routes)/anuncios/**`
- `src/app/(private-routes)/anunciar/**`
- `src/components/layout/header.tsx`
- `src/components/layout/header-wrapper.tsx`
- `src/components/layout/hero.tsx`
- `src/components/layout/categoria-section.tsx`
- `src/components/layout/categoria-card.tsx`
- `src/components/layout/footer-public.tsx`
- `src/components/anuncios/anuncio-card.tsx`
- `src/components/anuncios/anuncios-grid.tsx`
- `src/components/anuncios/anuncio-filters.tsx`
- `src/components/anuncios/gallery-modal.tsx`
- `src/features/anuncio-wizard/**`
- `src/components/ui/**`
- `src/lib/seo/**`
- `src/app/robots.ts`
- `src/app/sitemap.ts`
- assets publicos selecionados de `public/`, como logo, fundo, placeholder e cards.

## Partes da V3 que devem ser reaproveitadas

Pasta base: `C:\topsv3\frontend`

- `src/lib/api/client.ts`
- `src/lib/api/publicApi.ts`
- `src/lib/api/publicTypes.ts`
- `src/lib/api/adminAuthApi.ts`
- `src/lib/api/adminAuthTypes.ts`
- `src/lib/api/adminReadonlyApi.ts`
- `src/lib/api/adminReadonlyTypes.ts`
- `src/lib/config/publicEnv.ts`
- `src/lib/seo/localSeo.ts`
- `src/lib/seo/publicSeo.ts`
- `src/lib/seo/schemaPlaceholders.ts`
- rotas locais `/health`, `/robots.txt` e `/sitemap.xml` com politica HML adequada;
- validadores em `scripts/local/*`;
- configuracao HML em `.github/workflows/deploy-hml.yml` e `deploy/hml/**`;
- documentacao e evidencias em `docs/v3/**`;
- componentes admin V3 apenas se continuarem fora da migracao publica inicial.

## Fases propostas

### Fase 63 - Preparacao de migracao sem troca

- Congelar contratos publicos V3.
- Gerar matriz rota por rota entre clone e V3.
- Levantar dependencias visuais necessarias.
- Definir quais dependencias entram na V3 e quais serao substituidas.
- Confirmar politica HML noindex/nofollow.

### Fase 64 - Base visual shell

- Incorporar assets, tokens globais, fonte, header, footer, layout e estilos base.
- Nao conectar APIs reais.
- Manter adapters V3 e dados sinteticos.
- Validar home desktop/mobile.

### Fase 65 - Rotas publicas e SEO local/HML

- Migrar estrutura de rotas publicas preservando URLs.
- Adaptar canonical, robots, sitemap e metadata para HML/local sem indexacao.
- Remover dependencias diretas de dominio de producao.
- Validar SEO sintetico.

### Fase 66 - Cards, grids, filtros e listagens

- Migrar card, grid, listagem cidade, listagem bairro e filtros.
- Adaptar chamadas para `/api/public/**` da V3.
- Preservar densidade visual da producao.
- Garantir mobile sem scroll horizontal.

### Fase 67 - Detalhe do anuncio, midia e WhatsApp

- Migrar layout do detalhe do anuncio.
- Adaptar midia para DTO publico V3.
- Garantir que classificacao LIVRE/BLOQUEADO venha do backend.
- Garantir que WhatsApp seja liberado ou bloqueado pela decisao do backend V3.
- Nao expor storage key, bucket, provider, hash, URL privada ou documento privado.

### Fase 68 - Wizard /anunciar

- Migrar UX do wizard de producao.
- Trocar stores e APIs reais por fluxo sintetico/local V3.
- Manter upload real, pagamento, Premium obrigatorio e autopublicacao desativados ate autorizacao futura.
- Validar desktop/mobile e copy publica.

### Fase 69 - Admin e areas privadas

- Decidir se admin V3 atual permanece separado ou se sera reestilizado com base no clone.
- Nao misturar auth antigo com RBAC V3.
- Validar CSRF, cookies, logs e auditoria antes de HML navegavel.

### Fase 70 - HML visual e funcional controlado

- Publicar somente apos validacao local.
- Manter noindex/nofollow.
- Validar rotas publicas, age gate, WhatsApp, midia, wizard e admin sinteticos.
- Pro/humano obrigatorio antes de dados reais, importacao real, Pix/Efi, webhook, upload real ou cutover.

## Gates antes de homologacao navegavel

- Dependencias visuais aprovadas.
- Sem uso de endpoints reais antigos.
- Sem dados reais.
- Sem storage real.
- Sem upload real.
- Sem Pix/Efi real.
- Sem webhook real.
- Sem credencial versionada.
- Sem canonical apontando para producao em HML.
- Sem scroll lock ou elemento mobile flutuante indevido.

## Resultado esperado

Ao fim da migracao visual, a V3 deve parecer e se comportar como a producao nas rotas publicas principais, mas com integracoes controladas pelo backend V3, dados sinteticos em HML e gates claros antes de qualquer uso real.
