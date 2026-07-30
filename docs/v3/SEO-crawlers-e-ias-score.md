# SEO e acesso por crawlers - score tecnico

Data da validacao: 2026-07-29.

## Escopo

Esta avaliacao cobre a configuracao preparada para o dominio final
`https://topsdojob.com`. A pre-producao continua integralmente bloqueada por
`SEARCH_INDEXING_MODE=blocked`, meta e header `noindex, nofollow, noarchive`,
`robots.txt` com `Disallow: /` e sitemap vazio.

A liberacao final depende somente da combinacao:

```text
SEARCH_INDEXING_MODE=public
NEXT_PUBLIC_SITE_URL=https://topsdojob.com
```

O modo `public` aceita exclusivamente o dominio final. Ausencia ou valor
invalido falha fechado.

## Evidencias

- `robots.txt` simulado em modo publico: HTTP 200, sitemap final e grupos
  explicitos para Googlebot, Googlebot-Image, Googlebot-Video,
  Google-Extended, OAI-SearchBot, GPTBot, ChatGPT-User, Applebot, bingbot e
  `*`.
- Conteudo privado: os mesmos prefixos reais de admin, paineis, conta, KYC,
  documentos, previews, APIs e rotas autenticadas permanecem fora do
  rastreamento para todos os grupos.
- Sitemap simulado: 19 URLs, todas em `topsdojob.com`, sem query string,
  pre-producao ou rota privada. Localidades entram somente pelos limiares
  canonicos.
- `/acompanhantes`: HTTP 200 e o mesmo SSR seguro para todos os agentes
  testados, sem selecao ou bypass por User-Agent.
- Cobertura read-only usada na validacao: 13 estados, 24 cidades, 37 bairros e
  39 anuncios publicados elegiveis. Os valores sao lidos dos contratos
  publicos e nao ficam fixos no frontend.
- HTML inicial: title, description, H1, cobertura, orientacao de navegacao,
  seguranca, seis FAQs, links reais, canonical e JSON-LD presentes sem
  JavaScript.
- JSON-LD: `CollectionPage`, `BreadcrumbList`, `ItemList` das localidades
  visiveis e `FAQPage` igual ao conteudo renderizado. O `WebSite` permanece
  apenas global; nao existe `LocalBusiness`.
- Age gate: continua sobreposto no cliente sem remover o SSR. Nao existe
  liberacao de original restrito por User-Agent.
- `utm_source=chatgpt.com`: preservado na URL e consumido pela integracao GA4,
  sem redirect ou remocao no middleware.
- Lighthouse mobile, URL canonica local em modo publico: SEO 100, performance
  97, CLS 0, FCP 0,91 s, LCP 2,57 s e TBT 7 ms.
- Viewport de 390 px: sem overflow horizontal.
- Pre-producao real: HTTP 200, `Disallow: /`, sem sitemap anunciado e com meta
  e header `noindex, nofollow, noarchive`.

## Pontuacao

| Categoria | Peso | Nota | Evidencia principal |
|---|---:|---:|---|
| Rastreamento e indexacao | 2,0 | 2,0 | Separacao fail-closed por ambiente, crawlers publicos permitidos e rotas privadas bloqueadas. |
| Qualidade on-page | 2,0 | 2,0 | Title, description, H1, canonical, OG e Twitter alinhados no dominio final. |
| Conteudo util e citavel | 2,0 | 2,0 | Cobertura real, orientacao, seguranca e seis FAQs factuais no SSR. |
| Dados estruturados | 1,0 | 1,0 | Grafo valido e coerente com o conteudo visivel, sem tipos indevidos ou duplicados. |
| Arquitetura local e links | 1,0 | 1,0 | Links HTML reais e limiares unicos para pagina nacional, paginas locais e sitemap. |
| Performance e mobile | 1,0 | 0,9 | Lighthouse 97 e CLS 0; desconto conservador pelo LCP de 2,57 s. |
| Seguranca e conteudo adulto | 1,0 | 1,0 | Rating adulto unico, age gate sem cloaking e originais, KYC e documentos privados preservados. |
| **Total** | **10,0** | **9,9** | **Aprovado acima do minimo de 9,0.** |

## Referencias de crawler

- Google: `https://developers.google.com/crawling/docs/crawlers-fetchers/google-common-crawlers`
- OpenAI: `https://developers.openai.com/api/docs/bots`
- Apple: `https://support.apple.com/en-us/119829`
- Bing: `https://www.bing.com/webmasters/help/how-to-create-a-robots-txt-file-cb7c31ec`

## Gate de corte

Antes de ativar o dominio final, confirmar novamente robots, sitemap,
canonical, headers, Search Console e ausencia de bloqueio no CDN/WAF. A
pre-producao nao deve ser liberada nem enviada a mecanismos de busca.
