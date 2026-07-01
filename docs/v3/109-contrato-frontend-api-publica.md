# Contrato frontend x API publica

## Cliente

Arquivos:

- `frontend/src/lib/api/publicApi.ts`;
- `frontend/src/lib/api/publicTypes.ts`.

O cliente publica quatro funcoes:

- `getAnuncioPublico(slug)`;
- `getListagemCidadePublica(uf, cidade)`;
- `getListagemBairroPublica(uf, cidade, bairro)`;
- `getSeoRotaPublica(caminho)`.

Todas usam `fetchLocalApi`, `Accept: application/json`, `X-Request-Id`, `cache: no-store` e fallback seguro.

## Base URL

Regra:

- `NEXT_PUBLIC_API_BASE_URL` tem prioridade quando definida;
- `http://localhost:8080` e fallback somente quando `NEXT_PUBLIC_APP_ENV` e `local`;
- sem env local, nenhum dominio de producao e usado como default.

## Tipos

Tipos TypeScript espelham o contrato OpenAPI local:

- `AnuncioDetalhePublicoDto`;
- `AnuncioCardPublicoDto`;
- `ListaAnunciosPublicaDto`;
- `LocalizacaoPublicaDto`;
- `MidiaPublicaDto`;
- `SeoRotaPublicaDto`;
- `PaginacaoPublicaDto`;
- `PublicApiResponse<T>`.

## Rotas integradas

### `/anuncios/[slug]`

Consome:

- `GET /api/public/anuncios/{slug}`;
- `GET /api/public/seo/rota` com `caminho=/anuncios/{slug}`.

Renderiza apenas estado estrutural local. `contatoPublico` permanece nulo e WhatsApp segue pendente.

### `/acompanhantes/[uf]/[cidade]`

Consome:

- `GET /api/public/acompanhantes/{uf}/{cidade}`;
- `GET /api/public/seo/rota` com `caminho=/acompanhantes/{uf}/{cidade}`.

Renderiza contagens e estado local, sem busca real e sem dado real criado no frontend.

### `/acompanhantes/[uf]/[cidade]/[bairro]`

Consome:

- `GET /api/public/acompanhantes/{uf}/{cidade}/{bairro}`;
- `GET /api/public/seo/rota` com `caminho=/acompanhantes/{uf}/{cidade}/{bairro}`.

Renderiza contagens e estado local, sem busca real e sem dado real criado no frontend.

## Fallback

Em erro de rede, backend indisponivel, parametro rejeitado ou resposta nao OK, o frontend retorna:

```text
conteudo indisponivel localmente
```

O fallback nao consulta producao, nao usa API externa e nao persiste dados no navegador.

## SEO local

As paginas preservam `skeletonMetadata`, canonical local e `noindex`. O SEO vindo da API e exibido como informacao estrutural local, sem dominio de producao hardcoded.

`/sitemap.xml` e `/robots.txt` seguem como rotas locais existentes e o backend aceita esses caminhos no endpoint SEO de rota.

## Privacidade

O frontend nao exibe:

- WhatsApp publico;
- token;
- secret;
- cookie de sessao;
- storage key;
- bucket;
- hash;
- documento privado;
- imagem real;
- dado real inserido manualmente.

## Pendencias

- `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO`;
- `PENDENTE_URL_PUBLICA_MIDIA_CDN`;
- fonte visual atual ainda depende de inventario/aprovacao futura antes de qualquer experiencia final.
