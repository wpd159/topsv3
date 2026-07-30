# SEO - canonical, sitemap, robots e crawlers no cutover

## Fonte unica por ambiente

A decisao de indexacao usa somente `SEARCH_INDEXING_MODE`:

- `blocked`: ambiente integralmente bloqueado;
- `public`: rastreamento publico habilitado.

O dominio canonico continua vindo de `NEXT_PUBLIC_SITE_URL`. A aplicacao aceita
`SEARCH_INDEXING_MODE=public` apenas quando a origem for exatamente
`https://topsdojob.com`. Valor ausente resulta em `blocked`; valor desconhecido
ou combinacao invalida interrompe o build ou startup.

Nao criar flags adicionais para sitemap, Googlebot, OAI-SearchBot ou GPTBot.

## Pre-producao e HML

`v3.esle.cloud`, HML, pre-producao e ambiente local permanecem:

- `SEARCH_INDEXING_MODE=blocked`;
- `X-Robots-Tag: noindex, nofollow, noarchive`;
- meta robots bloqueado pela aplicacao;
- `User-agent: *` e `Disallow: /`;
- sitemap vazio na aplicacao;
- bloqueio adicional no Nginx.

O Nginx e o workflow continuam sendo barreiras independentes. Nenhum deploy
desta preparacao remove o bloqueio externo.

## Producao final

Somente no cutover aprovado:

```text
SEARCH_INDEXING_MODE=public
NEXT_PUBLIC_SITE_URL=https://topsdojob.com
```

Essa combinacao:

- permite Googlebot e OAI-SearchBot nas rotas publicas;
- bloqueia GPTBot por padrao;
- mantem admin, paineis, conta, KYC, documentos, previews, APIs e rotas
  autenticadas fora do rastreamento;
- preserva `noindex` das paginas de baixa qualidade;
- publica sitemap apenas no dominio final.

## Robots e verificacao de bots

O `robots.txt` de producao separa:

- `Googlebot`: permitido nas rotas publicas;
- `OAI-SearchBot`: permitido nas rotas publicas;
- `GPTBot`: `Disallow: /`;
- crawlers comuns: permitidos apenas nas mesmas rotas publicas.

A aplicacao nao entrega conteudo diferente por User-Agent. O age gate, o HTML
SSR, as derivacoes borradas e os contratos anonimos sao os mesmos para crawler
e visitante.

Firewall, CDN e rate limit nao devem confiar somente no texto do User-Agent.
Para Google, usar DNS reverso seguido de DNS direto ou as listas oficiais:

`https://developers.google.com/search/docs/crawling-indexing/verifying-googlebot`

Para OpenAI, consumir as faixas oficiais publicadas, sem copiar IPs para o
codigo da aplicacao:

`https://openai.com/searchbot.json`

`https://openai.com/gptbot.json`

## Sitemap

Entram somente:

- home e indices publicos;
- Blog e FAQs publicados;
- paginas institucionais indexaveis;
- estados com ao menos uma cidade indexavel;
- cidades e bairros que atendem aos limiares canonicos;
- anuncios publicados, aprovados e aprovados pela politica
  `shouldIndexAnuncio`.

Nao entram:

- pre-producao;
- API, admin, conta, paineis ou checkout;
- KYC, documentos, previews ou URLs assinadas;
- buscas, filtros, UTM e parametros de ordenacao;
- paginas locais abaixo do limiar;
- anuncios pendentes, pausados, bloqueados, removidos ou reprovados;
- URLs 404, redirect ou `noindex`.

## Conteudo adulto e SSR

Home, catalogo, paginas locais e detalhes de anuncio declaram uma unica tag
`rating=adult`. Blog, FAQ e paginas institucionais nao herdam essa tag.

O age gate nao remove o conteudo textual SSR. Midia `RESTRITA_18` anonima usa
somente derivacao publica borrada; o original, KYC e documentos privados nao
entram no HTML ou payload anonimo. Nao existe bypass por crawler.

## llms.txt

Esta preparacao nao cria `llms.txt`. Robots, sitemap, canonical, HTML SSR e
dados estruturados continuam sendo as fontes publicas verificaveis.

## Gate e rollback

Antes do cutover:

1. validar a simulacao `public` em build isolado;
2. testar Googlebot e OAI-SearchBot por mecanismo verificado;
3. confirmar GPTBot bloqueado;
4. comparar sitemap com paginas `index`;
5. validar View Source com JavaScript desativado;
6. confirmar ausencia de dados privados;
7. manter `v3.esle.cloud` bloqueado.

Se canonical, sitemap, robots ou headers estiverem incorretos, restaurar
`SEARCH_INDEXING_MODE=blocked`, retirar URLs incorretas do sitemap e somente
retomar o cutover depois de nova validacao.
