# Relatorio - Inventario visual producao

## Stack

- Next.js 15.5.7.
- React 19.1.2.
- Tailwind CSS 4.
- Poppins.
- Radix UI, Heroicons, lucide-react, framer-motion e componentes locais.

## Rotas publicas relevantes

- `/`
- `/acompanhantes`
- `/acompanhantes/[estado]`
- `/acompanhantes/[estado]/[cidade]`
- `/acompanhantes/[estado]/[cidade]/[bairro]`
- `/anuncios`
- `/anuncios/[slug]`
- `/registrar`
- paginas publicas legais/institucionais.

## Componentes publicos relevantes

- Header: `src/components/layout/header.tsx`.
- Header wrapper: `src/components/layout/header-wrapper.tsx`.
- Hero: `src/components/layout/hero.tsx`.
- Categorias: `src/components/layout/categoria-section.tsx`.
- Card de categoria: `src/components/layout/categoria-card.tsx`.
- Card de anuncio: `src/components/anuncios/anuncio-card.tsx`.
- Grid de anuncios: `src/components/anuncios/anuncios-grid.tsx`.
- Detalhe de anuncio: arquivos em `src/app/(public-routes)/anuncios/[slug]/`.
- Wizard: arquivos em `src/features/anuncio-wizard/`.

## Visual observado

- Fundo branco e area central ampla.
- Header branco com borda inferior, logo, navegacao curta e CTA rosa.
- Hero com imagem de fundo na producao, overlay escuro e CTA forte.
- Cards com midia no topo, borda clara, raio discreto, titulo, localizacao, preco e botoes.
- Listagens em grid responsivo com 1 coluna mobile e 4 colunas desktop quando ha largura.
- Categoria/home com imagens e chamadas mais comerciais.

## Assets publicos observados

- `public/logo-finallllll.webp`;
- `public/2151117281.jpg`;
- `public/bg-top.jpg`;
- `public/icone-sem-foto.png`;
- `public/cards/acompanhante-feminina.jpg`;
- `public/cards/acompanhante-masculino.jpg`;
- `public/cards/acompanhante-trans.jpg`;
- `public/cards/casual.jpg`;
- `public/cards/massagem.jpg`.

## Trechos com risco

- `html[data-scroll-locked]` e variaveis de remocao de barra de scroll em `globals.css`.
- Botao fixo de voltar ao topo no footer publico.
- Modais/auth e fetches da producao.
- Componentes com `position: absolute` e overlays que exigem revisao mobile antes de transplante.
- Dependencias visuais ligadas a upload, KYC, stores e fluxos reais.
