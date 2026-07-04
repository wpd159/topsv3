# Checklist Bloco 27.1 - Correcao visual SEO publico

## Preliminar

- [x] `git status --short` conferido antes das alteracoes.
- [x] `git remote -v` vazio.
- [x] `git log --oneline -3` conferido.
- [x] Checkpoint anterior `634330c` confirmado.
- [x] Alteracoes do Bloco 27 preservadas em stage.
- [x] Inventario inicial do bloco criado fora do repositorio.
- [x] Inventario inicial em UTF-8 com BOM.

## Diagnostico

- [x] Prints do Bloco 27 analisados.
- [x] Mini-coluna identificada nos prints antigos.
- [x] H1 verticalizado identificado nos prints antigos.
- [x] Wizard `/anunciar` estreito identificado nos prints antigos.
- [x] Falha do gate anterior documentada.
- [x] Causa raiz documentada em `docs/v3/186-bloco-27-1-correcao-visual-seo.md`.

## Layout

- [x] `html/body/main` com largura segura.
- [x] `.shell` e `.public-shell` com largura renderizavel.
- [x] H1 sem `overflow-wrap:anywhere`.
- [x] Breadcrumbs sem quebra letra por letra.
- [x] Botoes sem quebra letra por letra.
- [x] Wizard com largura minima legivel.
- [x] Sem scroll lock.
- [x] Sem `document.body.style.overflow`.
- [x] Sem `100vw`.
- [x] Sem `fixed`, `absolute` ou `sticky` em UI publica do app.

## Breadcrumbs

- [x] Cidade sem link para `/acompanhantes`.
- [x] Cidade sem link para `/acompanhantes/[uf]`.
- [x] Bairro sem link para `/acompanhantes`.
- [x] Bairro sem link para `/acompanhantes/[uf]`.
- [x] Bairro linka a cidade existente.
- [x] Anuncio nao linka raiz inexistente.
- [x] Links publicos apontam apenas para rotas existentes e preservadas.

## Textos publicos

- [x] Removido `SEO por cidade e bairro`.
- [x] Removido aria-label `Texto SEO local`.
- [x] Removidos textos publicos com skeleton/V3/dados reais nas paginas institucionais ajustadas.
- [x] Home usa `Acompanhantes por cidade e bairro`.
- [x] Sem linguagem tecnica principal nas paginas de cidade, bairro, anuncio e `/anunciar`.

## Gate renderizado

- [x] `scripts/local/validar-layout-publico-renderizado.ps1` criado.
- [x] Gate mede desktop `1280x900`.
- [x] Gate mede mobile `390x844`.
- [x] Gate valida shell.
- [x] Gate valida H1.
- [x] Gate valida breadcrumbs.
- [x] Gate valida wizard.
- [x] Gate valida ausencia de scroll horizontal.
- [x] Gate valida ausencia de link inexistente.
- [x] Gate gera prints quando solicitado.
- [x] Gate gera relatorio renderizado.

## Evidencias

- [x] `docs/v3/evidencias/bloco-27-1/home-linkagem-interna.png`.
- [x] `docs/v3/evidencias/bloco-27-1/cidade-seo.png`.
- [x] `docs/v3/evidencias/bloco-27-1/bairro-seo.png`.
- [x] `docs/v3/evidencias/bloco-27-1/anuncio-seo.png`.
- [x] `docs/v3/evidencias/bloco-27-1/anunciar-link-seo.png`.
- [x] `docs/v3/evidencias/bloco-27-1/home-linkagem-interna-mobile.png`.
- [x] `docs/v3/evidencias/bloco-27-1/cidade-seo-mobile.png`.
- [x] `docs/v3/evidencias/bloco-27-1/bairro-seo-mobile.png`.
- [x] `docs/v3/evidencias/bloco-27-1/anuncio-seo-mobile.png`.
- [x] `docs/v3/evidencias/bloco-27-1/anunciar-link-seo-mobile.png`.
- [x] `relatorio-prints.md`.
- [x] `relatorio-layout-renderizado.md`.

## Validacoes

- [x] diagnostico de toolchain.
- [x] build local.
- [x] E2E local descartavel.
- [x] API publica local via smoke HTTP do E2E descartavel.
- [x] persistencia JPA estatica.
- [x] UI mobile estatica.
- [x] SEO publico local.
- [x] rotas publicas SEO local.
- [x] layout publico renderizado.
- [x] codificacao.
- [x] arquivos proibidos.
- [x] segredos.
- [x] migrations SQL estaticas.
- [x] fonte de importacao local.
- [x] backend compile/test via build local.
- [x] frontend lint/build via build local.
- [x] `git diff --check`.
- [x] `git diff --cached --check`.
- [x] `git status --short`.
- [x] `git remote -v`.

## Proibicoes

- [x] Sem producao alterada.
- [x] Sem banco de producao.
- [x] Sem dado real.
- [x] Sem midia real.
- [x] Sem migration.
- [x] Sem SQL.
- [x] Sem upload.
- [x] Sem e-mail real.
- [x] Sem WhatsApp real.
- [x] Sem pagamento.
- [x] Sem credito.
- [x] Sem Pix/Efi.
- [x] Sem checkout.
- [x] Sem webhook.
- [x] Sem importador real.
- [x] Sem API externa.
- [x] Sem remote.
- [x] Sem push.
- [x] Sem commit.
- [x] Sem fase posterior iniciada.
