# Bloco 59 - Transplante visual cards, grids e detalhe

## Objetivo

Executar a segunda fase do transplante visual da producao para a V3, com foco em cards, grids, placeholders de midia e detalhe publico do anuncio.

## Checkpoint de entrada

- Checkpoint do Bloco 58: `46a1ff7`.
- Mensagem: `style: inicia transplante visual producao ate bloco 58`.
- `git remote -v` em `C:\topsv3`: vazio.
- Nenhum push foi executado.

## Fonte visual auditada

Fonte local somente leitura:

- `C:\clone\topsdojob-frontend`

Componentes auditados:

- `src/components/anuncios/anuncio-card.tsx`;
- `src/components/anuncios/anuncios-grid.tsx`;
- `src/app/(public-routes)/anuncios/[slug]/anuncio-detalhes.tsx`;
- `src/app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx`;
- `src/app/(public-routes)/anuncios/[slug]/componentes/main-content.tsx`;
- `src/app/(public-routes)/anuncios/[slug]/componentes/sidebar.tsx`;
- `src/app/(public-routes)/acompanhantes/[estado]/[cidade]/page.tsx`;
- `src/app/(public-routes)/acompanhantes/[estado]/[cidade]/[bairro]/page.tsx`.

## Adaptacao executada

- Placeholder publico de midia ganhou estrutura visual mais proxima de galeria, sem URL real.
- Cards/listagens ganharam densidade, borda, sombra leve, placeholder mais alto e CTA alinhado ao visual da producao.
- Detalhe do anuncio passou a ter galeria visual, miniaturas informativas, resumo lateral, preco/localizacao/beneficios e bloco de descricao.
- CTA do detalhe permanece informativo, sem abrir WhatsApp real e sem criar acao nova.
- Mobile permanece em fluxo normal, sem scroll horizontal planejado e sem elemento flutuante.

## Limites preservados

- Nenhum fetch da producao foi copiado.
- Nenhuma regra de negocio foi alterada.
- Nenhum backend, banco, migration, auth/RBAC, restore, VPS, Pix/Efi, pagamento, upload real, storage/CDN real ou API externa foi usado.
- Nenhum scroll lock, `document.body.style.overflow`, botao flutuante, barra fixa, carrossel automatico ou animacao chamativa foi criado.

## Status

O Bloco 59 melhora a paridade visual de cards, grids e detalhe publico, mas nao encerra `BLOQUEADO_PARIDADE_VISUAL_PRODUCAO`. Ainda ficam pendentes wizard `/anunciar`, footer, admin quando aplicavel, comparacao final mobile/desktop e revisao humana/Pro.
