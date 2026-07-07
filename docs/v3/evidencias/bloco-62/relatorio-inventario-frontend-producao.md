# Relatorio - Inventario do frontend de producao

## Fonte

- Caminho: `C:\clone\topsdojob-frontend`
- Uso neste bloco: somente leitura
- Estado observado: repositorio Git com alteracoes locais preexistentes em tres arquivos
- Remotes observados: existem remotes configurados no clone
- Acao executada no clone: leitura e inventario, sem alteracao

## Stack identificada

- Next.js 15
- React 19
- TypeScript
- Tailwind CSS 4
- Poppins
- Radix UI
- Heroicons
- lucide-react
- framer-motion
- componentes UI proprios em `src/components/ui/**`

## Rotas publicas relevantes

- `src/app/page.tsx`
- `src/app/(public-routes)/acompanhantes/page.tsx`
- `src/app/(public-routes)/acompanhantes/[estado]/page.tsx`
- `src/app/(public-routes)/acompanhantes/[estado]/[cidade]/page.tsx`
- `src/app/(public-routes)/acompanhantes/[estado]/[cidade]/[bairro]/page.tsx`
- `src/app/(public-routes)/anuncios/page.tsx`
- `src/app/(public-routes)/anuncios/[slug]/page.tsx`
- `src/app/(public-routes)/anuncios/[slug]/componentes/**`
- `src/app/(public-routes)/registrar/page.tsx`
- rotas institucionais, legais, blog, FAQ, creditos e contato

## Shell, home e layout

Arquivos fonte para preservacao visual:

- `src/app/layout.tsx`
- `src/app/globals.css`
- `src/components/layout/header.tsx`
- `src/components/layout/header-wrapper.tsx`
- `src/components/layout/hero.tsx`
- `src/components/layout/categoria-section.tsx`
- `src/components/layout/categoria-card.tsx`
- `src/components/layout/footer-public.tsx`

Padroes observados:

- fundo branco predominante;
- header branco com CTA forte;
- logo como sinal visual de primeira tela;
- containers largos e centralizados;
- tipografia Poppins;
- paleta com rosa principal da marca;
- cards densos e menos documentais que a V3 atual.

## Cards, grids, listagens e detalhe

Arquivos fonte:

- `src/components/anuncios/anuncio-card.tsx`
- `src/components/anuncios/anuncios-grid.tsx`
- `src/components/anuncios/anuncio-filters.tsx`
- `src/components/anuncios/gallery-modal.tsx`
- `src/app/(public-routes)/anuncios/[slug]/anuncio-detalhes.tsx`
- `src/app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx`
- `src/app/(public-routes)/anuncios/[slug]/componentes/main-content.tsx`
- `src/app/(public-routes)/anuncios/[slug]/componentes/sidebar.tsx`

Preservar visualmente:

- midia no topo do card;
- densidade de grid;
- hierarquia de titulo, localizacao, preco e CTA;
- placeholders seguros;
- detalhe com blocos e sidebar;
- comportamento mobile sem scroll horizontal.

Adaptar obrigatoriamente:

- clique WhatsApp para decisao do backend V3;
- classificacao LIVRE/BLOQUEADO para contrato V3;
- midia para DTO publico V3;
- sem expor storage key, bucket, provider, hash, URL privada ou documento privado.

## Wizard /anunciar

Arquivos fonte:

- `src/app/(private-routes)/anunciar/page.tsx`
- `src/app/(private-routes)/anunciar/wizard/page.tsx`
- `src/features/anuncio-wizard/anuncio-wizard.tsx`
- `src/features/anuncio-wizard/components/**`
- `src/features/anuncio-wizard/hooks/**`
- `src/features/anuncio-wizard/store/**`

Preservar:

- estrutura de etapas;
- progresso;
- hierarquia visual;
- botoes;
- revisao;
- experiencia mobile.

Nao copiar sem adaptacao:

- auth antigo;
- stores ligados a endpoints antigos;
- upload real;
- KYC real;
- pagamento/Premium;
- autopublicacao;
- qualquer envio real.

## SEO, canonical, robots e sitemap

Fontes:

- `src/lib/seo/**`
- `src/app/robots.ts`
- `src/app/sitemap.ts`
- metadata em `src/app/layout.tsx`
- metadata em rotas publicas

Risco identificado:

- canonical e metadata de producao devem ser reparametrizados para HML/local antes de qualquer deploy.
- sitemap do clone consulta endpoints e conteudos reais/antigos; deve ser refeito com adapters V3.

## Assets

Assets candidatos:

- `public/logo-finallllll.webp`
- `public/bg-top.jpg`
- `public/icone-sem-foto.png`
- `public/cards/*.jpg`
- imagens institucionais selecionadas

Cuidados:

- nao copiar assets grandes ou sensiveis sem triagem;
- nao versionar midia real de anuncio;
- nao copiar URLs privadas, storage keys ou buckets reais;
- manter placeholders seguros.

## Chamadas de API encontradas

Padrao principal observado: `process.env.NEXT_PUBLIC_API_URL`.

Categorias:

- auth/login/register/me;
- anuncios publicos;
- cidades e bairros ativos;
- clique WhatsApp;
- blog/SEO/sitemap;
- admin/painel;
- upload e midia;
- monetizacao/Premium/creditos;
- integrações externas de localidade.

Decisao:

As chamadas servem para mapear comportamento, mas nao devem ser copiadas diretamente. A migracao deve trocar chamadas por adapters V3 em `src/lib/api/**`.

## Trechos que nao devem ser copiados

- scroll lock e manipulacoes de `document.body.style.overflow`;
- botoes flutuantes indevidos;
- auth antigo;
- upload real;
- pagamento, checkout, Pix/Efi;
- webhooks;
- endpoints de producao;
- integrações externas sem decisao;
- storage/CDN reais;
- canonical de producao em HML;
- estados antigos de classificacao incompatíveis com LIVRE/BLOQUEADO.
