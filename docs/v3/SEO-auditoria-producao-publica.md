# SEO - auditoria publica de producao

## Escopo

Auditoria realizada no Bloco 27, somente leitura, para preservar o comportamento publico atual antes de ajustar as paginas locais da V3.

Nao houve login, formulario, SQL, banco, dump, midia real, imagem real, segredo, `.env`, certificado, deploy, restart, `git pull`, migration ou alteracao de arquivo.

## Comandos somente leitura

```text
ssh topsdojob "curl -sI https://topsdojob.com/robots.txt | head -40"
ssh topsdojob "curl -sI https://topsdojob.com/sitemap.xml | head -40"
ssh topsdojob "curl -sL --max-time 15 https://topsdojob.com/robots.txt | sed -n '1,120p'"
ssh topsdojob "curl -sL --max-time 15 https://topsdojob.com/sitemap.xml | grep -Eo 'https://topsdojob.com/acompanhantes/[^<]+' | head -40"
ssh topsdojob "curl -sL --max-time 15 https://topsdojob.com/sitemap.xml | grep -Eo 'https://topsdojob.com/anuncios/[^<]+' | head -40"
ssh topsdojob "curl -sL --max-time 15 URL | grep -Eio '<title...>|canonical|description|robots|h1' | head -40"
```

## Robots e sitemap

- `https://topsdojob.com/robots.txt`: HTTP 200.
- `https://topsdojob.com/sitemap.xml`: HTTP 200.
- Robots atual permite indexacao geral.
- Robots bloqueia parametros de filtro, ordenacao, busca e UTM.
- Sitemap canonico atual: `https://topsdojob.com/sitemap.xml`.

## URLs observadas

Cidades:

- `https://topsdojob.com/acompanhantes/go/goiania`
- `https://topsdojob.com/acompanhantes/sp/sao-paulo`
- `https://topsdojob.com/acompanhantes/pr/curitiba`

Bairros:

- `https://topsdojob.com/acompanhantes/go/goiania/jardim-america`
- `https://topsdojob.com/acompanhantes/sp/sao-paulo/bela-vista`
- `https://topsdojob.com/acompanhantes/ce/fortaleza/aldeota`

Anuncios:

- `https://topsdojob.com/anuncios/monique-77-acompanhante`
- `https://topsdojob.com/anuncios/ravena-do-boquete-molhado`
- `https://topsdojob.com/anuncios/acompanhante-em-londrina`

## Metadados observados

Padrao de cidade:

- title: `Acompanhantes em [Cidade], [UF] | Tops do Job`
- H1: `Acompanhantes em [Cidade]`
- description: texto curto com acompanhantes na cidade, fotos nos perfis, contato direto e navegacao por bairro.
- robots: `index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1`
- canonical: URL publica da propria cidade.

Padrao de bairro:

- title: `Acompanhantes em [Bairro], [Cidade] | Tops do Job`
- H1: `Acompanhantes em [Bairro], [Cidade]`
- description: texto curto com acompanhantes no bairro/cidade, fotos nos perfis, contato direto e navegacao por bairro.
- robots: `index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1`
- canonical: URL publica do proprio bairro.

Padrao de anuncio:

- title: nome publico do anuncio + localidade + `| Tops do Job`.
- H1: nome publico do anuncio.
- description: baseada na descricao publica do perfil.
- robots: `index, follow, max-video-preview:-1, max-image-preview:large, max-snippet:-1`
- canonical: URL publica do proprio anuncio.

## Padroes preservaveis

- Manter a intencao principal "Acompanhantes em [Cidade]".
- Manter cidade e bairro como rotas canonicas.
- Preservar `/anuncios/[slug]`.
- Preservar canonical proprio por pagina.
- Preservar sitemap com home, categorias, cidades, bairros e anuncios.
- Preservar bloqueio de parametros fracos no robots futuro.

## Lacunas para V3 local

- Paginas locais ainda estavam com textos tecnicos de fase anterior.
- H1/description locais nao estavam alinhados ao padrao de producao.
- Breadcrumbs locais ainda precisavam ser materializados.
- Linkagem cidade -> bairro -> anuncio -> cidade/bairro precisava ficar explicita.
- Ambiente local deve continuar noindex, sem cutover.

## Riscos

- Remover noindex antes do cutover pode expor ambiente local ou staging.
- Criar paginas vazias em massa pode gerar conteudo fraco.
- Copiar texto de anuncio real para desenvolvimento violaria a regra de nao usar dado real.
- Alterar rotas preservadas pode perder trafego ja existente.

## Confirmacao

Nada foi alterado em producao. A consulta foi estritamente publica/somente leitura.
