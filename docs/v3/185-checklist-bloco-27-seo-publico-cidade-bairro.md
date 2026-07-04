# Checklist Bloco 27 - SEO publico cidade, bairro e anuncio

## Preliminar

- [x] `git status --short` executado antes do checkpoint.
- [x] `git remote -v` vazio antes do checkpoint.
- [x] Commit local de checkpoint criado: `634330c`.
- [x] `git log --oneline -3` conferido.
- [x] Inventario inicial criado fora do repositorio.
- [x] Inventario inicial em UTF-8 com BOM.

## Producao somente leitura

- [x] `robots.txt` consultado.
- [x] `sitemap.xml` consultado.
- [x] Ate 3 URLs de cidade observadas.
- [x] Ate 3 URLs de bairro observadas.
- [x] Ate 3 URLs de anuncio observadas.
- [x] Somente metadados e estrutura basica consultados.
- [x] Nenhum HTML inteiro foi impresso.
- [x] Nenhuma imagem ou midia foi baixada.
- [x] Nada foi alterado em producao.

## SEO implementado

- [x] Helper `frontend/src/lib/seo/publicSeo.ts` criado.
- [x] Title/H1 de cidade no padrao `Acompanhantes em [Cidade] - [UF]`.
- [x] Title/H1 de bairro no padrao `Acompanhantes em [Bairro], [Cidade] - [UF]`.
- [x] Title/description segura de anuncio.
- [x] Canonical via `localUrl`.
- [x] Robots local `noindex, nofollow`.
- [x] Robots futuro de producao documentado sem ativacao.

## Paginas publicas

- [x] Cidade atualizada.
- [x] Bairro atualizado.
- [x] Anuncio atualizado.
- [x] Home com linkagem interna.
- [x] `/anunciar` com texto principal acentuado.
- [x] Estado vazio sem texto tecnico principal.
- [x] Conteudo bloqueado continua mediado.
- [x] WhatsApp continua mediado.

## Componentes

- [x] `PublicBreadcrumbs`.
- [x] `PublicSeoIntro`.
- [x] `PublicInternalLinks`.
- [x] `PublicLocalitySeoHeader`.
- [x] Sem animacao.
- [x] Sem elemento flutuante.
- [x] Sem `fixed`.
- [x] Sem `absolute`.
- [x] Sem `sticky`.
- [x] Sem `100vw`.
- [x] Sem scroll lock.
- [x] Sem `document.body.style.overflow`.

## Sitemap, robots e validadores

- [x] Sitemap local revisado.
- [x] Robots local preservado.
- [x] Validador SEO atualizado.
- [x] Admin fora do sitemap.
- [x] API fora do sitemap.
- [x] Rotas alternativas proibidas ausentes.

## Evidencias

- [x] `home-linkagem-interna.png`.
- [x] `cidade-seo.png`.
- [x] `bairro-seo.png`.
- [x] `anuncio-seo.png`.
- [x] `anunciar-link-seo.png`.
- [x] `home-linkagem-interna-mobile.png`.
- [x] `cidade-seo-mobile.png`.
- [x] `bairro-seo-mobile.png`.
- [x] `anuncio-seo-mobile.png`.
- [x] `anunciar-link-seo-mobile.png`.

## Validacoes

- [x] diagnostico de toolchain.
- [x] build local.
- [x] E2E local descartavel.
- [x] API publica local via smoke HTTP do E2E descartavel.
- [x] persistencia JPA estatica.
- [x] UI mobile estatica.
- [x] SEO publico local.
- [x] codificacao.
- [x] arquivos proibidos.
- [x] segredos.
- [x] rotas publicas SEO local.
- [x] migrations SQL estaticas.
- [x] fonte de importacao local.
- [x] backend compile/test.
- [x] frontend lint/build.
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
- [x] Sem email real.
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
- [x] Sem fase posterior iniciada.

## Auditoria posterior

- [x] Bloco 27 exigiu correcao visual obrigatoria no Bloco 27.1.
- [x] A aprovacao visual depende de `scripts/local/validar-layout-publico-renderizado.ps1`.
- [x] Evidencias finais devem ser as de `docs/v3/evidencias/bloco-27-1/`.
