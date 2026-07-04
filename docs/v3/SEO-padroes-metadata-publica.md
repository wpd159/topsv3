# SEO - padroes de metadata publica

## Objetivo

Padronizar title, description, H1, canonical e robots das paginas publicas da V3 para preparar ranqueamento local sem executar cutover.

## Regras permanentes

- Local continua `noindex, nofollow`.
- Canonical local usa `localUrl`.
- Nenhuma pagina local deve hardcodar `topsdojob.com`.
- Producao futura so pode usar `index, follow` depois de gate Pro.
- Frontend nao decide classificacao, contato publico ou liberacao de midia.
- Texto publico deve evitar termos tecnicos como skeleton, API local, previa local, dados sinteticos e rota preservada.
- Textos publicos nao devem usar "SEO local", "Texto SEO local" ou "SEO por cidade e bairro".
- H1, breadcrumbs, botoes e wizard nao podem depender de `overflow-wrap:anywhere`.
- Mudanca visual publica de SEO deve passar por `scripts/local/validar-layout-publico-renderizado.ps1`.

## Cidade

Padrao:

- title: `Acompanhantes em [Cidade] - [UF] | Tops do Job`
- H1: `Acompanhantes em [Cidade] - [UF]`
- description: curta, humana e focada em busca local.
- canonical: `/acompanhantes/[uf]/[cidade]`
- breadcrumbs: `Inicio > Acompanhantes > [UF] > [Cidade]`
- apenas `Inicio` e link; `Acompanhantes` e UF sao texto enquanto `/acompanhantes` e `/acompanhantes/[uf]` nao existirem.

Intencoes cobertas:

- `acompanhante em [cidade]`
- `acompanhantes em [cidade]`
- `acompanhantes em [cidade] - [UF]`

## Bairro

Padrao:

- title: `Acompanhantes em [Bairro], [Cidade] - [UF] | Tops do Job`
- H1: `Acompanhantes em [Bairro], [Cidade] - [UF]`
- description: curta, com bairro, cidade e UF.
- canonical: `/acompanhantes/[uf]/[cidade]/[bairro]`
- breadcrumbs: `Inicio > Acompanhantes > [UF] > [Cidade] > [Bairro]`
- apenas `Inicio` e cidade linkam; `Acompanhantes`, UF e bairro atual sao texto quando nao houver rota existente.

Intencoes cobertas:

- `acompanhante em [bairro]`
- `acompanhantes em [bairro]`
- `acompanhante em [bairro], [cidade]`
- `acompanhantes em [bairro], [cidade] - [UF]`

## Anuncio

Padrao:

- title: `[Titulo seguro] em [Bairro], [Cidade], [UF] | Tops do Job`, quando houver localidade.
- H1: titulo publico seguro do anuncio.
- description: descricao publica sanitizada ou fallback seguro.
- canonical: `/anuncios/[slug]`
- breadcrumbs: `Inicio > Cidade/Bairro > Anuncio`
- anuncio nao deve linkar para `/acompanhantes` ou `/acompanhantes/[uf]`.

O anuncio deve reforcar a ligacao com cidade e bairro, mas nao pode expor contato bruto, midia bloqueada ou conteudo bloqueado.

## Home

Padrao:

- title: `Tops do Job | Acompanhantes por cidade e bairro`
- description: navegar por acompanhantes em cidades, bairros e anuncios.
- canonical: `/`
- linkagem para cidades, bairros, anuncios e `/anunciar`.

## Cutover futuro

Antes de remover noindex:

- validar sitemap real;
- revisar Search Console completo;
- confirmar inventario de URLs reais;
- revisar paginas vazias;
- confirmar canonical de producao;
- aprovar robots de producao;
- validar que admin/API continuam fora do sitemap.

## Bloco 28

Metadata de producao observada deve ser preservada por intencao:

- cidade: `Acompanhantes em [Cidade], [UF]`;
- bairro: `Acompanhantes em [Bairro], [Cidade]`;
- anuncio: title/H1 publicos preservados apenas por padrao sanitizado nos docs;
- canonical de producao usa `https://topsdojob.com` somente em ambiente aprovado;
- local permanece `noindex` e sem canonical de producao.
