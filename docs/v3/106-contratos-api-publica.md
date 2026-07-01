# Contratos da API publica de leitura

## Rotas backend

### `GET /api/public/anuncios/{slug}`

Retorna `AnuncioDetalhePublicoDto`.

Regras:

- `slug` deve seguir padrao seguro de slug;
- retorna 404 quando nao existir anuncio publico;
- nao retorna documento privado, hash, storage key, pagamento, credito ou auditoria;
- nao retorna WhatsApp neste bloco.

### `POST /api/public/anuncios/{slug}/visualizacao`

Registra visualizacao publica local.

Regras:

- aceita somente anuncio publico `LIVRE`;
- retorna 404 para anuncio inexistente ou nao publicavel;
- armazena hashes tecnicos, nunca IP/User-Agent/referer brutos;
- retorna `PENDENTE_CONFIRMACAO_IDADE_STORIES`.

### `POST /api/public/anuncios/{slug}/clique-whatsapp`

Registra clique WhatsApp publico local.

Regras:

- aceita somente anuncio publico `LIVRE`;
- retorna `whatsappUrl` apenas quando politica backend permitir;
- nao retorna `whatsapp_normalizado` bruto;
- nao cria limite diario comercial;
- nao expoe documento, storage, pagamento, saldo ou auditoria.

### `GET /api/public/acompanhantes/{uf}/{cidade}`

Retorna `ListaAnunciosPublicaDto`.

Parametros:

- `uf`: duas letras;
- `cidade`: slug;
- `pagina`: inteiro baseado em zero, default `0`;
- `tamanho`: entre 1 e 50, default `20`.

### `GET /api/public/acompanhantes/{uf}/{cidade}/{bairro}`

Retorna `ListaAnunciosPublicaDto` filtrado por bairro.

Parametros:

- `uf`: duas letras;
- `cidade`: slug;
- `bairro`: slug;
- `pagina`: inteiro baseado em zero, default `0`;
- `tamanho`: entre 1 e 50, default `20`.

### `GET /api/public/seo/rota`

Retorna `SeoRotaPublicaDto`.

Query:

- `caminho=/anuncios/slug-local`;
- `caminho=/acompanhantes/sp/sao-paulo`;
- `caminho=/acompanhantes/sp/sao-paulo/bairro-local`;
- `caminho=/sitemap.xml`;
- `caminho=/robots.txt`.

Rotas alternativas proibidas retornam 400:

- `/perfil/*`;
- `/ads/*`;
- `/anuncio/*`;
- `/acompanhante/*`.

## DTOs

### `AnuncioDetalhePublicoDto`

Campos:

- `slug`;
- `titulo`;
- `descricao`;
- `preco`;
- `localizacao`;
- `midias`;
- `destaque`;
- `topo`;
- `midiaExtra`;
- `story`;
- `beneficiosPublicos`;
- `contatoPublico`;
- `pendenciaContatoPublico`;
- `publicadoEm`;
- `seo`.

`contatoPublico` permanece `null` no endpoint de detalhe. WhatsApp publico so pode retornar por `POST /api/public/anuncios/{slug}/clique-whatsapp`.

`pendenciaContatoPublico` usa `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO`.

### `AnuncioCardPublicoDto`

Versao resumida para listagens. Nao contem contato publico.

### `MidiaPublicaDto`

Campos:

- `tipo`;
- `finalidade`;
- `ordem`;
- `urlPublica`;
- `largura`;
- `altura`;
- `mimeType`.

Nao contem `bucket`, `chaveObjeto`, `sha256`, `etag`, `storageProvider` ou `nomeOriginal`.

`urlPublica` permanece nulo ate definicao futura de geracao CDN/publica segura.

Pendencia:

```text
PENDENTE_URL_PUBLICA_MIDIA_CDN
```

### `LocalizacaoPublicaDto`

Campos:

- `uf`;
- `cidade`;
- `cidadeSlug`;
- `bairro`;
- `bairroSlug`;
- `enderecoResumido`.

Nao expoe latitude/longitude neste bloco.

### `SeoRotaPublicaDto`

Campos:

- `title`;
- `description`;
- `canonicalPath`;
- `robots`;
- `tipoRota`;
- `indexavelFuturo`.

`canonicalPath` e caminho relativo. Nao contem dominio de producao.

### `PaginacaoPublicaDto`

Campos:

- `pagina`;
- `tamanho`;
- `totalItens`;
- `totalPaginas`.

## OpenAPI

O contrato local foi atualizado em:

```text
contracts/openapi/topsdojob-v3-local.yaml
```

## Politicas preservadas

- health/readiness/liveness continuam preservados;
- erro transversal continua padronizado;
- request id continua preservado;
- frontend publico nao foi alterado visualmente;
- API publica nao sugere rota SEO alternativa;
- admin permanece skeleton local, sem funcionalidade real.

## Complemento Bloco 6

A paginacao das listagens publicas foi corrigida para aplicar filtro `PUBLICADO`/`APROVADO`/`removido_em null` antes da pagina final de anuncios publicos.

O frontend publico passa a consumir este contrato por cliente local tipado, com fallback seguro e sem expor WhatsApp, storage key, hash, bucket ou imagem real.

## Complemento Bloco 8

Foram adicionados os contratos de metrica publica, politica de WhatsApp backend e stories pendentes de confirmacao de idade.

A classificacao publica e binaria: `LIVRE`/`BLOQUEADO`.
