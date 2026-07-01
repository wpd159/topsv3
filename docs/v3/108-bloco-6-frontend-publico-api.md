# Bloco 6 - Frontend publico integrado a API local

## Objetivo

Integrar o frontend publico skeleton aos contratos locais de leitura criados no Bloco 5, preservando o visual atual, sem redesign, sem dados reais, sem imagens reais e sem qualquer acesso externo de negocio.

## Escopo entregue

- cliente local de API publica em `frontend/src/lib/api/publicApi.ts`;
- tipos TypeScript compativeis com o OpenAPI local em `frontend/src/lib/api/publicTypes.ts`;
- integracao das rotas publicas skeleton:
  - `/anuncios/[slug]`;
  - `/acompanhantes/[uf]/[cidade]`;
  - `/acompanhantes/[uf]/[cidade]/[bairro]`;
- fallback seguro quando o backend local estiver indisponivel;
- correcao do backend para aceitar `/sitemap.xml` e `/robots.txt` no endpoint SEO de rota;
- correcao da paginação publica para filtrar anuncios publicados/aprovados antes da pagina final;
- documentacao da pendencia segura de URL publica de midia/CDN.

## Backend corrigido

`RotaPublicaGuard.caminhoPublico` aceita agora:

- `/sitemap.xml`;
- `/robots.txt`;
- `/`;
- `/anuncios/*`;
- `/acompanhantes/*`.

Continuam proibidas:

- `/perfil/*`;
- `/ads/*`;
- `/anuncio/*`;
- `/acompanhante/*`;
- URL absoluta como `https://topsdojob.com/anuncios/x`.

## Paginacao publica

Status: corrigida neste bloco.

A listagem publica deixou de paginar localizacoes antes de filtrar anuncios. O fluxo atual e:

1. localizar cidade ou bairro por parametros seguros;
2. coletar IDs de anuncios vinculados a localizacao;
3. aplicar filtro de anuncio `PUBLICADO`, moderacao `APROVADO` e `removido_em null`;
4. paginar o resultado final de anuncios publicos;
5. montar DTO publico sem expor entidade JPA.

Nao foi criada query nativa, SQL, migration, banco runtime ou dependencia de Flyway.

## Midia publica

`MidiaPublicaDto.urlPublica` permanece nulo por seguranca enquanto a politica de geracao publica de URL/CDN nao for definida.

Pendencia registrada:

```text
PENDENTE_URL_PUBLICA_MIDIA_CDN
```

Storage provider, bucket, chave de objeto, hash, etag e URL privada continuam fora dos DTOs publicos.

## Frontend integrado

O cliente `publicApi.ts`:

- usa `NEXT_PUBLIC_API_BASE_URL` quando configurado;
- usa `http://localhost:8080` apenas como fallback de ambiente local;
- nao usa token, secret, cookie de sessao, localStorage ou sessionStorage;
- nao chama producao por padrao;
- converte falhas de backend em estado seguro;
- consome somente endpoints `GET` publicos.

As paginas continuam skeleton local. Elas mostram apenas estado estrutural da resposta da API local, como status, contagem de itens, total local, canonical relativo e robots. Elas nao renderizam imagem real, telefone, WhatsApp publico, contato direto ou dado sensivel.

## Fallback seguro

Quando o backend local nao responde, as paginas renderizam:

```text
conteudo indisponivel localmente
```

O fallback nao tenta buscar outra origem, nao chama API externa e nao usa producao.

## Preservacao visual

Nao houve redesign. Foram reaproveitados:

- `PublicRouteShell`;
- `SeoPlaceholder`;
- classes globais existentes como `panel`, `muted` e `health-grid`.

Nao houve nova paleta, nova tipografia, nova identidade visual, imagem real ou componente final de layout publico.

## WhatsApp

WhatsApp publico permanece nao exposto.

Pendencia preservada:

```text
PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO
```

## Fora de escopo preservado

Nao houve:

- admin funcional;
- autenticacao real;
- sessao/token real;
- acao critica;
- financeiro funcional;
- Pix/Efi funcional;
- moderacao real;
- aprovacao/reprovacao;
- importador real;
- ETL;
- dump;
- dado real;
- banco de producao;
- migration;
- SQL;
- Flyway;
- Docker;
- API externa;
- producao;
- VPS;
- remote;
- push;
- commit.

## Validacoes

Foram executadas validacoes locais de backend, frontend, scanners, rotas publicas, migrations estaticas e fonte de importacao local durante o fechamento do bloco.

## Complemento Bloco 7

O Bloco 7 validou a integracao frontend/API em ambiente local completo.

O frontend continuou sem redesign e `npm run lint`/`npm run build` passaram. A API publica consumida pelas paginas skeleton respondeu no e2e local com dados sinteticos minimos e fallback seguro preservado.

## Complemento Bloco 8

A rota `/anuncios/[slug]` passa a usar componente client-side isolado para registrar visualizacao local e solicitar WhatsApp pelo endpoint autorizado.

O componente nao usa localStorage, sessionStorage, token, cookie de sessao ou tracking externo. Ele nao decide classificacao e apenas reflete a resposta backend.
