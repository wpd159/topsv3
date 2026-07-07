# Relatorio - Plano de transplante visual

## Arquivos fonte do clone

- `C:\clone\topsdojob-frontend\src\app\globals.css`
- `C:\clone\topsdojob-frontend\src\components\layout\header.tsx`
- `C:\clone\topsdojob-frontend\src\components\layout\hero.tsx`
- `C:\clone\topsdojob-frontend\src\components\anuncios\anuncio-card.tsx`
- `C:\clone\topsdojob-frontend\src\components\anuncios\anuncios-grid.tsx`
- `C:\clone\topsdojob-frontend\src\app\(public-routes)\acompanhantes\[estado]\[cidade]\page.tsx`
- `C:\clone\topsdojob-frontend\src\app\(public-routes)\acompanhantes\[estado]\[cidade]\[bairro]\page.tsx`
- `C:\clone\topsdojob-frontend\src\app\(public-routes)\anuncios\[slug]\anuncio-detalhes.tsx`
- `C:\clone\topsdojob-frontend\src\features\anuncio-wizard\anuncio-wizard.tsx`

## Arquivos V3 adaptados neste bloco

- `frontend/src/app/globals.css`
- `frontend/src/modules/public/components/PublicSiteHeader.tsx`

## Arquivos V3 candidatos para blocos seguintes

- `frontend/src/modules/public/components/PublicHomeHero.tsx`
- `frontend/src/modules/public/components/PublicAnuncioCard.tsx`
- `frontend/src/modules/public/components/PublicAnuncioGrid.tsx`
- `frontend/src/modules/public/components/PublicAnuncioDetalhe.tsx`
- `frontend/src/modules/public/components/PublicMidiaPlaceholder.tsx`
- `frontend/src/modules/public/components/PublicAnunciarWizard.tsx`
- `frontend/src/modules/public/components/PublicAnunciarForm.tsx`
- `frontend/src/app/page.tsx`
- `frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx`
- `frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx`
- `frontend/src/app/anuncios/[slug]/page.tsx`
- `frontend/src/app/anunciar/page.tsx`

## Copiar/adaptar

- Tokens: Poppins, `#FC1EAD`, `#e01a9a`, `#B30078`, bordas claras.
- Largura/container publico.
- Aparencia de CTA rosa.
- Densidade de cards e grid.
- Ritmo de espacamento do header e listagens.

## Reimplementar

- Hero com midia segura sem asset real ate aprovacao.
- Detalhe de anuncio com DTO V3 e regras LIVRE/BLOQUEADO.
- Wizard com arquitetura V3 e sem stores/upload/pagamento real.
- Footer sem botao fixo/flutuante enquanto o gate mobile nao aprovar.

## Nao copiar

- Fetches da producao.
- Auth/modais da producao.
- Scroll lock e `document.body.style.overflow`.
- `position: fixed` do footer/voltar ao topo.
- Dados reais, URLs reais sensiveis, storage keys, buckets e midia privada.

## Riscos e mitigacao

- SEO/rotas: manter rotas preservadas e validadores locais.
- Mobile: validar sem scroll horizontal, sem flutuante e sem salto de layout.
- Scroll lock: bloquear qualquer reintroducao por CSS ou JS.
- Funcional: separar visual de comportamento; nao transplantar regras de negocio da producao.
