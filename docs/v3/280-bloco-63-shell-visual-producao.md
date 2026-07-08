# Bloco 63 - Shell visual de producao na V3

## Status

- Status: IMPLEMENTADO_LOCALMENTE
- Estrategia: primeira fase da Opção B definida no Bloco 62
- Fonte visual: `C:\clone\topsdojob-frontend`, somente leitura
- Area alterada: `C:\topsv3\frontend` e documentacao do bloco
- Backend, banco, migrations, admin, moderacao, Premium, age gate, Pix/Efi, upload real e webhook: NAO ALTERADOS

## Objetivo executado

O frontend publico da V3 recebeu a primeira casca visual baseada na producao:

- header publico com logo, links principais, entrada, registro e CTA;
- home com hero visual de producao, imagem de fundo e busca visual segura;
- categorias em destaque com imagens do clone;
- footer publico com links e CTA;
- conexao do footer nas rotas publicas principais e no shell de paginas institucionais;
- preservacao dos adapters e contratos V3 para leitura publica.

## Fonte do clone usada

Foram usados como referencia visual:

- `src/app/layout.tsx`
- `src/app/globals.css`
- `src/app/page.tsx`
- `src/components/layout/header.tsx`
- `src/components/layout/hero.tsx`
- `src/components/layout/categoria-section.tsx`
- `src/components/layout/categoria-card.tsx`
- `src/components/layout/footer-public.tsx`
- assets publicos selecionados em `public/` e `public/cards/`

## Assets copiados para a V3

- `frontend/public/2151117281.jpg`
- `frontend/public/cards/acompanhante-feminina.jpg`
- `frontend/public/cards/acompanhante-masculino.jpg`
- `frontend/public/cards/acompanhante-trans.jpg`
- `frontend/public/cards/casual.jpg`
- `frontend/public/cards/massagem.jpg`

Nao foi copiada a imagem grande `vista-frontal-mulher-bdsm-estetica (1).jpg`. Nao foram copiados `.env`, dumps, logs, credenciais, storage keys, arquivos privados ou midia real de anuncio.

## Arquivos V3 alterados

- `frontend/src/app/globals.css`
- `frontend/src/app/page.tsx`
- `frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx`
- `frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx`
- `frontend/src/app/anuncios/[slug]/page.tsx`
- `frontend/src/modules/public/components/PublicSiteHeader.tsx`
- `frontend/src/modules/public/components/PublicHomeHero.tsx`
- `frontend/src/modules/public/components/PublicSiteFooter.tsx`
- `frontend/src/modules/public/components/PublicCategorySection.tsx`
- `frontend/src/modules/public/skeleton/PublicRouteShell.tsx`

## Adapters e stubs temporarios

Os adapters V3 em `frontend/src/lib/api/**` foram preservados. Onde o clone fazia chamada ao backend antigo, o Bloco 63 nao copiou a chamada:

- busca da home virou link seguro para `/acompanhantes/go/goiania`;
- categorias viraram cards estaticos com links internos seguros;
- login/registro do header apontam para rotas V3 existentes (`/admin` e `/anunciar`);
- footer nao abre modal, nao envia formulario e nao chama API externa.

Esses stubs visuais sao temporarios e devem ser substituidos por adapters V3 nas fases de listagens/filtros/wizard.

## O que nao foi migrado

- wizard completo;
- detalhe do anuncio completo;
- filtros avancados;
- admin;
- moderacao;
- Premium;
- age gate;
- chamadas antigas de API;
- auth antigo;
- upload real;
- pagamento/Pix/Efi;
- webhook;
- analytics/Tag Manager;
- scroll lock e botoes flutuantes do clone.

## Resultado

A V3 passa a ter shell publico mais proximo da producao, com assets reais do frontend de producao e sem trazer dependencias funcionais antigas. A paridade completa ainda depende das fases seguintes.
