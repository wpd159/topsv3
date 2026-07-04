# SEO - canonical, sitemap e robots para cutover

## Ambiente local

- Local sempre seguro e bloqueado para indexacao.
- Nao remover `noindex` local.
- Nao emitir canonical de producao em local.
- Nao usar `https://topsdojob.com` como canonical local.
- Sitemap local pode conter exemplos seguros, mas nao representa sitemap final.

## Producao futura

Em producao aprovada:

- canonical deve usar `https://topsdojob.com`;
- nao usar `www` se a decisao do projeto e dominio sem `www`;
- cidade/bairro/anuncio devem apontar canonical para a propria URL final;
- anuncios removidos, bloqueados ou nao indexaveis nao devem permanecer no sitemap;
- paginas vazias/fracas nao devem entrar no sitemap;
- admin e API nunca entram no sitemap.

## Robots

Antes do cutover:

- staging/homologacao devem continuar bloqueados enquanto necessario;
- local permanece bloqueado;
- parametros fracos, busca interna, filtros e UTM devem continuar protegidos.

Depois do cutover aprovado:

- liberar apenas o dominio final correto;
- manter admin/API bloqueados;
- validar que `robots.txt` aponta para sitemap final correto.

## Sitemap

Entram no sitemap final somente:

- home aprovada;
- paginas institucionais aprovadas;
- cidades com conteudo util;
- bairros com conteudo suficiente;
- anuncios publicados, LIVRE/indexaveis e aprovados;
- `/anunciar`, se fizer parte da estrategia publica.

Nao entram:

- API;
- admin;
- preview;
- skeleton;
- pagina vazia;
- anuncio bloqueado/removido;
- rota alternativa proibida.

## Rollback

Se canonical, sitemap ou robots forem publicados incorretamente:

- pausar cutover;
- restaurar regra anterior;
- remover URLs incorretas do sitemap;
- revalidar headers e HTML publico;
- registrar decisao no SDD;
- monitorar Search Console por cobertura, 404 e queda de consultas.
