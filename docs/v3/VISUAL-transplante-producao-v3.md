# Visual - Transplante da producao para V3

## Fonte visual

Fonte local usada em leitura:

- `C:\clone\topsdojob-frontend`

A raiz `C:\clone` contem o subprojeto de frontend, mas nao foi tratada como repositorio Git valido pelo comando `git status`. O subprojeto `topsdojob-frontend` foi identificado como frontend de producao atual e usado como referencia visual.

## Stack identificada

- Next.js 15.
- React 19.
- Tailwind CSS 4.
- Poppins como fonte base.
- Componentes Radix, Heroicons, lucide-react e componentes UI locais.
- Layout publico com header, footer, hero, categorias, grid de anuncios, cards, detalhe de anuncio e wizard.

## Arquivos fonte principais no clone

- `src/app/globals.css`
- `src/app/layout.tsx`
- `src/app/page.tsx`
- `src/components/layout/header.tsx`
- `src/components/layout/header-wrapper.tsx`
- `src/components/layout/hero.tsx`
- `src/components/layout/categoria-section.tsx`
- `src/components/layout/categoria-card.tsx`
- `src/components/layout/footer-public.tsx`
- `src/components/anuncios/anuncio-card.tsx`
- `src/components/anuncios/anuncios-grid.tsx`
- `src/app/(public-routes)/acompanhantes/[estado]/[cidade]/page.tsx`
- `src/app/(public-routes)/acompanhantes/[estado]/[cidade]/[bairro]/page.tsx`
- `src/app/(public-routes)/anuncios/[slug]/anuncio-detalhes.tsx`
- `src/app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx`
- `src/app/(public-routes)/anuncios/[slug]/componentes/main-content.tsx`
- `src/app/(public-routes)/anuncios/[slug]/componentes/sidebar.tsx`
- `src/features/anuncio-wizard/anuncio-wizard.tsx`
- `src/features/anuncio-wizard/components/wizard-step-perfil.tsx`
- `src/features/anuncio-wizard/components/wizard-step-localizacao.tsx`
- `src/features/anuncio-wizard/components/wizard-step-servicos.tsx`
- `src/features/anuncio-wizard/components/wizard-step-fotos.tsx`
- `src/features/anuncio-wizard/components/wizard-final-review.tsx`

## Tokens visuais observados

- Fundo publico branco.
- Fonte base Poppins.
- Rosa principal `#FC1EAD`.
- Hover principal `#e01a9a`.
- Contraste rosa `#B30078`.
- Bordas claras em torno de `#eadfe5`.
- Containers amplos com conteudo centralizado.
- Header branco com navegacao simples e CTA forte.
- Cards densos, com midia no topo e informacoes compactas.
- CTAs retangulares simples, sem animacao chamativa.

## Pode ser copiado ou adaptado

- Tokens de cor e espacamento.
- Estrutura visual do header publico.
- Densidade dos cards.
- Hierarquia de home e listagens.
- Proporcao de containers.
- Placeholder seguro de midia, desde que sem storage real.

## Deve ser reimplementado na arquitetura V3

- Fetches publicos, DTOs e mapeamentos.
- Age gate/WhatsApp conforme decisao do backend V3.
- Wizard `/anunciar` sem stores, upload real ou pagamento.
- Admin visual quando houver bloco especifico.
- Footer, caso seja retomado, sem botao fixo/flutuante automatico.

## Nao deve ser copiado

- Scroll lock, `html[data-scroll-locked]` e qualquer uso de `document.body.style.overflow`.
- Botao fixo/flutuante de voltar ao topo sem bloco especifico.
- Auth/modais da producao.
- Fetches contra APIs reais.
- Configuracoes com dominio real para ambiente local.
- Midia real, URLs privadas, storage keys, buckets ou assets sensiveis.
- Trechos de compliance que dependam de regra antiga ou estados nao binarios.

## Primeira adaptacao aplicada

Na V3, a primeira adaptacao ficou limitada a:

- `frontend/src/app/globals.css`;
- `frontend/src/modules/public/components/PublicSiteHeader.tsx`.

Foram ajustados tokens de cor, container publico, suavizacao de fonte, header publico, CTA principal e estados de hover.

## Ordem recomendada dos proximos blocos

1. Revisar visualmente header/logo e wizard apos o Bloco 60.
2. Ajustar footer publico sem elemento flutuante.
3. Validar mobile completo em home, cidade, bairro, detalhe e `/anunciar`.
4. Revisao humana/Pro antes de homologacao/cutover.

## Adaptacoes posteriores registradas

- Bloco 59: cards, grids, placeholders e detalhe publico do anuncio.
- Bloco 60: logo real local em `/logo.webp`, header publico com imagem e wizard `/anunciar` com progresso/card/botoes mais proximos da producao.

## Riscos

- SEO/rotas: preservar `/anuncios/[slug]`, `/acompanhantes/[uf]/[cidade]`, `/acompanhantes/[uf]/[cidade]/[bairro]` e `/anunciar`.
- Mobile: evitar horizontal scroll, elementos absolutos sem justificativa e altura instavel.
- Scroll lock: nao reintroduzir scripts/classes de bloqueio de scroll da producao.
- Funcional: nao copiar fetches/modais/autenticacao da producao para a V3 local.
