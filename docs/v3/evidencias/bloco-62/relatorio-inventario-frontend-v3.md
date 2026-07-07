# Relatorio - Inventario do frontend V3 atual

## Fonte

- Caminho: `C:\topsv3\frontend`
- Uso neste bloco: auditoria local
- Papel futuro: fonte de contratos, adapters, testes, configuracao HML e componentes locais seguros

## Stack identificada

- Next.js 15
- React 19
- TypeScript
- ESLint
- CSS global proprio
- Sem Tailwind/Radix como base visual atual

## Rotas V3 relevantes

- `/`
- `/acompanhantes/[uf]/[cidade]`
- `/acompanhantes/[uf]/[cidade]/[bairro]`
- `/anuncios/[slug]`
- `/anunciar`
- `/admin`
- `/health`
- `/robots.txt`
- `/sitemap.xml`
- paginas institucionais locais

## Contratos e adapters reutilizaveis

Arquivos principais:

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

Pontos fortes:

- chamadas V3 ja centralizadas;
- endpoints locais `/api/public/**` e `/api/admin/**`;
- contratos com flags de seguranca sintetica;
- separacao de DTO publico;
- SEO local/HML documentado;
- validadores sinteticos ja existem.

## Componentes publicos atuais

Componentes observados:

- `src/modules/public/components/PublicSiteHeader.tsx`
- `src/modules/public/components/PublicHomeHero.tsx`
- `src/modules/public/components/PublicAnuncioCard.tsx`
- `src/modules/public/components/PublicAnuncioGrid.tsx`
- `src/modules/public/components/PublicAnuncioDetalhe.tsx`
- `src/modules/public/components/PublicMidiaPlaceholder.tsx`
- `src/modules/public/components/PublicAnunciarWizard.tsx`
- `src/modules/public/skeleton/**`

Decisao:

Esses componentes podem servir como ponte temporaria e como referencia de integracao V3, mas nao devem continuar como base visual principal.

## Admin e validadores

Reaproveitar:

- componentes admin locais ja ligados aos contratos V3;
- validadores `scripts/local/*`;
- checks de dados sinteticos;
- checks de render publico;
- checks de wizard;
- checks de admin/moderacao;
- checks de age gate, midia, auth, observabilidade e preflight.

Nao misturar:

- auth antigo do clone com RBAC V3;
- admin antigo do clone com auditoria V3 sem fase propria;
- dados reais ou producao.

## Configuracao HML reutilizavel

Arquivos e pastas:

- `.github/workflows/deploy-hml.yml`
- `deploy/hml/docker-compose.yml`
- `deploy/hml/nginx-v3-esle-cloud.conf`
- `deploy/hml/hml.env.example`
- `scripts/deploy/validar-deploy-hml-local.ps1`

Cuidados:

- manter noindex/nofollow;
- preservar exclusoes sensiveis;
- nao expor `.env`, certificados, logs brutos, dumps ou backups;
- nao executar push ou deploy neste bloco.

## Lacunas do frontend V3 atual

- visual publico ainda nao atinge paridade com producao;
- base de componentes nasceu como skeleton tecnico;
- home, listagens e detalhe nao reproduzem integralmente densidade e comportamento da producao;
- wizard nao replica o UX real completo;
- dependencias visuais da producao ainda nao foram adotadas como base;
- ajustes por aproximacao geram alto custo incremental.

## Conclusao

O frontend V3 atual deve ser preservado como camada de contrato e seguranca, mas a base visual/publica deve migrar para a base real do clone. A troca deve ser faseada e sempre mediada por adapters V3.
