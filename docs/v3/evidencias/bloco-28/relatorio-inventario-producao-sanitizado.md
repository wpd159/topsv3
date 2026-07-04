# Relatorio de inventario SEO de producao sanitizado

- Bloco: 28
- Data local: 2026-07-03 23:30:43 -03:00
- Base publica consultada: `https://topsdojob.com`
- Escopo: robots.txt, sitemap.xml, sitemaps filhos quando publicos e metadados publicos limitados.
- Politica: somente leitura, sem login, sem banco, sem SQL, sem dump, sem midia, sem formulario e sem alteracao de producao.
- Saida bruta externa: `C:\topsv3-auditoria-local\seo\bloco-28`
- Lista bruta completa de anuncios reais versionada: nao.

## Status HTTP publico

| Recurso | Status | Observacao |
| --- | ---: | --- |
| robots.txt | 200 | lido |
| sitemap.xml | 200 | lido |

## Contagem por tipo

| Tipo | Total |
| --- | ---: |
| home | 1 |
| cidade | 29 |
| bairro | 21 |
| anuncio | 62 |
| institucional | 1 |
| outros | 0 |
| proibido/admin/api | 0 |
| desconhecido | 45 |

## Padroes de URL observados

| Tipo | Padrao preservavel | Regra V3 |
| --- | --- | --- |
| home | `/` | manter quando aprovado para producao |
| cidade | `/acompanhantes/[uf]/[cidade]` | preservar e validar conteudo util |
| bairro | `/acompanhantes/[uf]/[cidade]/[bairro]` | preservar quando houver conteudo suficiente |
| anuncio | `/anuncios/[slug]` | preservar slug publico quando anuncio for indexavel |
| institucional | caminhos publicos sem login | manter ou redirecionar conforme mapa |
| proibido/admin/api | `/admin` e `/api` | nao indexar, nao colocar no sitemap |

## Exemplos sanitizados - home
- `/`

## Exemplos sanitizados - cidade
- `/acompanhantes/go/aparecida-de-goiania`
- `/acompanhantes/se/aracaju`
- `/acompanhantes/mg/belo-horizonte`
- `/acompanhantes/pa/belem`
- `/acompanhantes/df/brasilia`

## Exemplos sanitizados - bairro
- `/acompanhantes/go/goiania/[bairro-publico-amostra-1]`
- `/acompanhantes/go/goiania/[bairro-publico-amostra-2]`
- `/acompanhantes/go/goiania/[bairro-publico-amostra-3]`
- `/acompanhantes/go/goiania/[bairro-publico-amostra-4]`
- `/acompanhantes/go/goiania/[bairro-publico-amostra-5]`

## Exemplos sanitizados - anuncio
- `/anuncios/[slug-publico-amostra-1]`
- `/anuncios/[slug-publico-amostra-2]`
- `/anuncios/[slug-publico-amostra-3]`
- `/anuncios/[slug-publico-amostra-4]`
- `/anuncios/[slug-publico-amostra-5]`

## Exemplos sanitizados - institucional
- `/sobre`

## Exemplos sanitizados - outros

- Nenhum item observado.

## Exemplos sanitizados - proibido/admin/api

- Nenhum item observado.

## Exemplos sanitizados - desconhecido
- `/anuncios`
- `/acompanhantes`
- `/blog`
- `/creditos`
- `/faq`

## Metadados publicos limitados

| Tipo | URL sanitizada | Status | Title | Description | Canonical | Robots | H1 |
| --- | --- | ---: | --- | --- | --- | --- | --- |
| home | `/` | 200 | Acompanhantes perto de você / Tops do Job | Veja anúncios de acompanhantes na sua cidade e região, com contato direto pelo WhatsApp. | `/` | AUSENTE | Encontre acompanhantes perto de você |
| cidade | `/acompanhantes/go/aparecida-de-goiania` | 200 | Acompanhantes em Aparecida de Goiânia, GO / Tops do Job | Encontre acompanhantes em Aparecida de Goiânia com fotos nos perfis, contato direto e navegação por bairro. | `/acompanhantes/go/aparecida-de-goiania` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | Acompanhantes em Aparecida de Goiânia |
| cidade | `/acompanhantes/se/aracaju` | 200 | Acompanhantes em Aracaju, SE / Tops do Job | Encontre acompanhantes em Aracaju com fotos nos perfis, contato direto e navegação por bairro. | `/acompanhantes/se/aracaju` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | Acompanhantes em Aracaju |
| cidade | `/acompanhantes/mg/belo-horizonte` | 200 | Acompanhantes em Belo Horizonte, MG / Tops do Job | Encontre acompanhantes em Belo Horizonte com fotos nos perfis, contato direto e navegação por bairro. | `/acompanhantes/mg/belo-horizonte` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | Acompanhantes em Belo Horizonte |
| cidade | `/acompanhantes/pa/belem` | 200 | Acompanhantes em Belém, PA / Tops do Job | Encontre acompanhantes em Belém com fotos nos perfis, contato direto e navegação por bairro. | `/acompanhantes/pa/belem` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | Acompanhantes em Belém |
| cidade | `/acompanhantes/df/brasilia` | 200 | Acompanhantes em Brasília, DF / Tops do Job | Encontre acompanhantes em Brasília com fotos nos perfis, contato direto e navegação por bairro. | `/acompanhantes/df/brasilia` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | Acompanhantes em Brasília |
| bairro | `/acompanhantes/go/goiania/[bairro-publico-amostra-1]` | 200 | Acompanhantes em Jardim América, Goiânia / Tops do Job | Encontre acompanhantes em Jardim América, Goiânia com fotos nos perfis, contato direto e navegação por bairro. | `/acompanhantes/go/goiania/jardim-america` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | Acompanhantes em Jardim América, Goiânia |
| bairro | `/acompanhantes/go/goiania/[bairro-publico-amostra-2]` | 200 | Acompanhantes no Setor Aeroporto, Goiânia / Tops do Job | Encontre acompanhantes no Setor Aeroporto, Goiânia com fotos nos perfis, contato direto e navegação por bairro. | `/acompanhantes/go/goiania/setor-aeroporto` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | Acompanhantes no Setor Aeroporto, Goiânia |
| bairro | `/acompanhantes/go/goiania/[bairro-publico-amostra-3]` | 200 | Acompanhantes no Setor Bueno, Goiânia / Tops do Job | Encontre acompanhantes no Setor Bueno, Goiânia com fotos nos perfis, contato direto e navegação por bairro. | `/acompanhantes/go/goiania/setor-bueno` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | Acompanhantes no Setor Bueno, Goiânia |
| bairro | `/acompanhantes/go/goiania/[bairro-publico-amostra-4]` | 200 | Acompanhantes no Setor Central, Goiânia / Tops do Job | Encontre acompanhantes no Setor Central, Goiânia com fotos nos perfis, contato direto e navegação por bairro. | `/acompanhantes/go/goiania/setor-central` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | Acompanhantes no Setor Central, Goiânia |
| bairro | `/acompanhantes/go/goiania/[bairro-publico-amostra-5]` | 200 | Acompanhantes no Setor Norte Ferroviário, Goiânia / Tops do Job | Encontre acompanhantes no Setor Norte Ferroviário, Goiânia com fotos nos perfis, contato direto e navegação por bairro. | `/acompanhantes/go/goiania/setor-norte-ferroviario` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | Acompanhantes no Setor Norte Ferroviário, Goiânia |
| anuncio | `/anuncios/[slug-publico-amostra-1]` | 200 | [title-publico-de-anuncio-sanitizado] | [description-publica-de-anuncio-sanitizada] | `/anuncios/[slug-publico-amostra-1]` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | [h1-publico-de-anuncio-sanitizado] |
| anuncio | `/anuncios/[slug-publico-amostra-2]` | 200 | [title-publico-de-anuncio-sanitizado] | [description-publica-de-anuncio-sanitizada] | `/anuncios/[slug-publico-amostra-2]` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | [h1-publico-de-anuncio-sanitizado] |
| anuncio | `/anuncios/[slug-publico-amostra-3]` | 200 | [title-publico-de-anuncio-sanitizado] | [description-publica-de-anuncio-sanitizada] | `/anuncios/[slug-publico-amostra-3]` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | [h1-publico-de-anuncio-sanitizado] |
| anuncio | `/anuncios/[slug-publico-amostra-4]` | 200 | [title-publico-de-anuncio-sanitizado] | [description-publica-de-anuncio-sanitizada] | `/anuncios/[slug-publico-amostra-4]` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | [h1-publico-de-anuncio-sanitizado] |
| anuncio | `/anuncios/[slug-publico-amostra-5]` | 200 | [title-publico-de-anuncio-sanitizado] | [description-publica-de-anuncio-sanitizada] | `/anuncios/[slug-publico-amostra-5]` | index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1 | [h1-publico-de-anuncio-sanitizado] |
| institucional | `/sobre` | 200 | Tops do Job - Encontre as melhores acompanhantes | Encontre acompanhantes com fotos reais, anúncios verificados e contato direto em uma plataforma segura, discreta e atualizada diariamente. | `AUSENTE` | AUSENTE | Quem Somos |

## Riscos observados

- Slugs de anuncio podem conter nome publico sensivel; por isso foram sanitizados nos documentos versionados.
- O sitemap publico pode conter volume de URLs dinamicas; a lista completa fica somente fora do repositorio.
- O cutover SEO depende de mapa completo, validacao de canonical, robots, sitemap e Search Console.
- Nenhuma URL de admin/API deve entrar no sitemap futuro.

## Confirmacao

- Producao alterada: nao.
- Login realizado: nao.
- Banco/SQL/dump acessado: nao.
- Midia real baixada: nao.
- Dados privados versionados: nao.
