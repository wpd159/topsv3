# Bloco 5 - API publica minima de leitura

## Objetivo

Criar a primeira API backend publica minima de leitura da V3, alinhada as rotas publicas preservadas, sem admin funcional, sem autenticacao real, sem acao critica, sem banco persistente, sem importacao real, sem dados reais e sem alteracao de schema.

## Endpoints criados

- `GET /api/public/anuncios/{slug}`;
- `GET /api/public/acompanhantes/{uf}/{cidade}`;
- `GET /api/public/acompanhantes/{uf}/{cidade}/{bairro}`;
- `GET /api/public/seo/rota`.

Esses endpoints sao API backend. Eles nao criam rota publica alternativa no frontend. As rotas publicas do site permanecem:

- `/anuncios/[slug]`;
- `/acompanhantes/[uf]/[cidade]`;
- `/acompanhantes/[uf]/[cidade]/[bairro]`.

Rotas alternativas como `/perfil`, `/ads`, `/anuncio/[id]` e `/acompanhante/[slug]` continuam proibidas.

## Camadas criadas

Aplicacao publica:

- `backend/src/main/java/br/com/topsdojob/v3/application/publico/dto`;
- `backend/src/main/java/br/com/topsdojob/v3/application/publico/mapper`;
- `backend/src/main/java/br/com/topsdojob/v3/application/publico/service`.

Web publica:

- `backend/src/main/java/br/com/topsdojob/v3/web/publico`.

## DTOs publicos

- `AnuncioCardPublicoDto`;
- `AnuncioDetalhePublicoDto`;
- `MidiaPublicaDto`;
- `LocalizacaoPublicaDto`;
- `ListaAnunciosPublicaDto`;
- `SeoRotaPublicaDto`;
- `PaginacaoPublicaDto`.

Os DTOs nao expõem documento privado, CPF, IP, user-agent, hash, auditoria administrativa, payload financeiro, dados de pagamento, saldo de credito, e-mail privado, status interno sensivel, storage key privada ou documento privado como midia.

## Mappers

- `AnuncioPublicoMapper`;
- `MidiaPublicaMapper`;
- `SeoPublicoMapper`.

Regras de mapper:

- midia so entra no DTO quando o vinculo esta `PUBLICAVEL`;
- arquivo de midia so entra quando esta `VALIDADO`;
- apenas classificacao `LIVRE` entra; `BLOQUEADO` nao exibe midia publica nem contato publico;
- storage provider, bucket, chave de objeto, hash, etag e nome original nao sao expostos;
- WhatsApp nao e exposto neste bloco.

## Services de leitura

- `AnuncioPublicoConsultaService`;
- `ListagemPublicaConsultaService`;
- `SeoPublicoConsultaService`.

Todos os services usam `@Transactional(readOnly = true)`.

## Controllers publicos

- `AnuncioPublicoController`;
- `ListagemPublicaController`;
- `SeoPublicoController`.

Todos os controllers criados neste bloco expõem somente `GET`.

## Repositories

Repositories novos:

- `AnuncioLocalizacaoRepository`;
- `AnuncioMidiaRepository`;
- `EstadoRepository`;
- `CidadeRepository`;
- `BairroRepository`.

Repositories existentes atualizados com metodos derivados read-only:

- `AnuncioRepository`;
- `ArquivoMidiaRepository`;
- `SeoUrlRepository`.

Nao foram criados metodos `delete`, `update`, `@Modifying`, lock, query nativa, `EntityManager` ou `JdbcTemplate`.

## Tratamento de erros

`GlobalExceptionHandler` agora trata `ResponseStatusException` para preservar o padrao transversal de erro.

- anuncio inexistente retorna 404;
- parametro invalido retorna 400;
- erro de validacao nao deve virar 500;
- erro inesperado segue sem vazar detalhe interno.

## WhatsApp

WhatsApp nao foi exposto.

Marcador registrado no DTO de detalhe:

```text
PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO
```

Nao foi criado limite diario de clique, contato ou WhatsApp.
Nao foi criada regra intermediaria de classificacao, blur por categoria ou desbloqueio parcial por visitante.

## Premium

Os DTOs preparam campos publicos:

- `destaque`;
- `topo`;
- `midiaExtra`;
- `story`;
- `beneficiosPublicos`.

Neste bloco esses campos sao estruturais e conservadores. Nao foi criado paywall agressivo e a utilidade do gratuito nao foi reduzida.

## SEO

`SeoRotaPublicaDto` prepara:

- `title`;
- `description`;
- `canonicalPath`;
- `robots`;
- `tipoRota`;
- `indexavelFuturo`.

`canonicalPath` e sempre caminho relativo. O backend nao emite canonical de producao por padrao local.

## Testes

Testes unitarios/mockados criados:

- mapper nao expoe storage key, hash ou documento privado;
- mapper bloqueia midia nao aprovada;
- service retorna 404 para anuncio inexistente;
- SEO preserva canonicalPath relativo e sem dominio de producao;
- SEO rejeita rota alternativa proibida;
- controller/listagem publica nao expoe campo sensivel.

Nenhum teste usa banco, dump, arquivo real de entrada, API externa ou contexto Spring com DataSource.

## Limites preservados

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
- banco;
- Flyway;
- Docker;
- migration nova;
- SQL alterado;
- seed;
- API externa;
- producao;
- VPS;
- remote;
- push;
- commit.

## Riscos residuais

- consultas de listagem foram corrigidas no Bloco 6 para filtrar anuncios publicos antes da pagina final; otimizacao com banco real segue futura;
- nomes publicos completos de cidade/bairro dependem de dados locais futuros;
- exposicao publica de WhatsApp segue pendente por politica;
- URL publica/CDN de midia segue pendente por seguranca: `PENDENTE_URL_PUBLICA_MIDIA_CDN`;
- premium publico ainda e estrutural;
- validacao runtime com banco descartavel nao faz parte deste bloco.

## Complemento Bloco 7

O Bloco 7 validou a API publica de leitura em execucao local real contra PostgreSQL descartavel.

Foram testados:

- health/readiness;
- SEO para `/sitemap.xml`;
- SEO para `/robots.txt`;
- detalhe de anuncio sintetico;
- listagem por cidade sintetica;
- listagem por bairro sintetico;
- rejeicao de `/perfil/slug`;
- rejeicao de URL absoluta de producao.

O smoke HTTP nao encontrou exposicao de contato publico, documento privado, storage key, bucket, hash, pagamento ou auditoria administrativa.
