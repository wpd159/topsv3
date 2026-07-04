# Bloco 27 - SEO publico de cidade, bairro e anuncio

## Objetivo

Preparar as paginas publicas locais da V3 para SEO de cidade, bairro e anuncio, com foco em `acompanhante em [cidade]`, preservando ambiente local seguro e sem cutover.

## Checkpoint

Antes das alteracoes, foi criado commit local de checkpoint:

```text
634330c feat: consolida wizard seo premium local ate bloco 26.2
```

Nao houve remote configurado e nao houve push.

## Auditoria de producao

A producao foi consultada somente leitura para observar robots, sitemap e metadados publicos.

Resumo observado:

- cidades usam title `Acompanhantes em [Cidade], [UF] | Tops do Job`;
- bairros usam title `Acompanhantes em [Bairro], [Cidade] | Tops do Job`;
- anuncios usam title com titulo publico e localidade;
- robots de producao atual e `index, follow`;
- canonical aponta para a URL publica da propria pagina;
- sitemap preserva estados, cidades, bairros e anuncios.

Detalhes estao em `docs/v3/SEO-auditoria-producao-publica.md`.

## Implementacao local

Criado helper:

- `frontend/src/lib/seo/publicSeo.ts`

O helper centraliza:

- exibicao de cidade, bairro e UF;
- title, description e H1 de cidade;
- title, description e H1 de bairro;
- title e description segura de anuncio;
- canonical via `localUrl`;
- robots local `noindex, nofollow`;
- marcador de robots futuro de producao, sem ativar cutover.

## Pagina de cidade

Atualizada:

- `frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx`

Padrao:

- title: `Acompanhantes em [Cidade] - [UF] | Tops do Job`
- H1: `Acompanhantes em [Cidade] - [UF]`
- breadcrumbs: `Inicio > Acompanhantes > [UF] > [Cidade]`
- link para bairros quando houver itens;
- link para anuncios retornados;
- link para `/anunciar`;
- estado vazio sem texto tecnico.

## Pagina de bairro

Atualizada:

- `frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx`

Padrao:

- title: `Acompanhantes em [Bairro], [Cidade] - [UF] | Tops do Job`
- H1: `Acompanhantes em [Bairro], [Cidade] - [UF]`
- breadcrumbs: `Inicio > Acompanhantes > [UF] > [Cidade] > [Bairro]`
- link de volta para cidade;
- link para anuncios;
- link para `/anunciar`;
- estado vazio sem texto tecnico.

## Pagina de anuncio

Atualizada:

- `frontend/src/app/anuncios/[slug]/page.tsx`

Padrao:

- title seguro baseado no anuncio quando houver API local;
- H1 seguro;
- description segura;
- canonical proprio;
- breadcrumbs com cidade/bairro quando houver;
- link para cidade;
- link para bairro quando houver;
- link para `/anunciar`.

Conteudo bloqueado, stories, midia e WhatsApp continuam mediados pelos fluxos existentes.

## Componentes criados

- `PublicBreadcrumbs`
- `PublicSeoIntro`
- `PublicInternalLinks`
- `PublicLocalitySeoHeader`

Todos sao simples, sem animacao, sem `fixed`, sem `absolute`, sem `sticky`, sem `100vw`, sem scroll lock e sem `document.body.style.overflow`.

## Home, sitemap e robots

Home:

- reforca links para cidade, bairro, anuncio e `/anunciar`;
- remove texto tecnico principal.

Sitemap local:

- usa `localUrl`;
- inclui home, cidade, bairro, anuncio e `/anunciar`;
- nao inclui API;
- nao inclui admin;
- nao inclui rota fraca/skeleton;
- nao usa dominio de producao.

Robots local:

- continua bloqueado com `Disallow: /`;
- sitemap local via `localUrl`.

## Proibicoes preservadas

Nao houve:

- alteracao em producao;
- banco de producao;
- dado real;
- midia real;
- migration;
- SQL;
- upload;
- email real;
- WhatsApp real;
- pagamento;
- credito;
- Pix/Efi;
- checkout;
- webhook;
- importador real;
- API externa;
- remote;
- push;
- commit apos as alteracoes do Bloco 27.

## Riscos residuais

- Search Console completo ainda pendente.
- Inventario completo de URLs reais ainda pendente.
- Cutover de indexacao depende de revisao Pro.
- Bairros vazios nao devem ser indexados em producao futura.
- Paridade fina de textos com producao deve ser revisada antes de homologacao.

## Correcao posterior - Bloco 27.1

A auditoria visual reprovou os prints iniciais do Bloco 27 por mini-coluna, H1 verticalizado, breadcrumbs com links para rotas inexistentes e linguagem publica tecnica.

O Bloco 27.1 corrige esses pontos e cria o gate renderizado `scripts/local/validar-layout-publico-renderizado.ps1`. A aprovacao visual do Bloco 27 depende das evidencias em `docs/v3/evidencias/bloco-27-1/` e da validacao renderizada OK.
