# Relatorio - Componentes fonte do clone

## Fonte

Clone local somente leitura:

- `C:\clone\topsdojob-frontend`

## Estado Git do clone

O clone ja estava sujo antes deste bloco:

- `M docs/deploy.md`;
- `M src/components/anuncios/editar/anuncio-edit-media.tsx`;
- `M src/features/anuncio-wizard/components/wizard-step-fotos.tsx`.

Esse estado foi apenas registrado. Nenhuma escrita foi feita no clone.

## Componentes auditados

- `src/components/anuncios/anuncio-card.tsx`: card com midia no topo, border radius, localizacao, titulo, idade, visualizacoes, preco e CTAs.
- `src/components/anuncios/anuncios-grid.tsx`: grid responsivo com 1/2/3/4 colunas e densidade de listagem.
- `src/app/(public-routes)/anuncios/[slug]/anuncio-detalhes.tsx`: composicao de detalhe com galeria, sidebar e conteudo.
- `src/app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx`: galeria principal, miniaturas e placeholder sem foto.
- `src/app/(public-routes)/anuncios/[slug]/componentes/main-content.tsx`: blocos de descricao, servicos e localizacao.
- `src/app/(public-routes)/anuncios/[slug]/componentes/sidebar.tsx`: resumo lateral com categoria, localizacao, preco e CTAs.
- rotas cidade/bairro em `src/app/(public-routes)/acompanhantes/[estado]/[cidade]`.

## Itens nao copiados

- Fetches da producao.
- Auth/modais da producao.
- Lightbox/portal e comportamentos de galeria.
- WhatsApp real.
- Upload, stores, KYC, pagamento e Premium real.
- Scroll lock e qualquer uso de `document.body.style.overflow`.
- URL real, bucket, storage key, provider, hash ou midia real.
