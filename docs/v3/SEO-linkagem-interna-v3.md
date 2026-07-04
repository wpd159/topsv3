# SEO - linkagem interna V3

## Objetivo

Organizar a navegacao publica para preservar trafego atual e preparar crescimento em buscas como `acompanhante em [cidade]`.

## Grafo principal

```text
home
  -> cidade
  -> bairro
  -> anuncio
  -> /anunciar

cidade
  -> bairros com perfis suficientes
  -> anuncios retornados pela API local
  -> /anunciar

bairro
  -> cidade
  -> anuncios do bairro
  -> /anunciar

anuncio
  -> cidade
  -> bairro, quando houver
  -> /anunciar
```

## Rotas preservadas

- `/acompanhantes/[uf]/[cidade]`
- `/acompanhantes/[uf]/[cidade]/[bairro]`
- `/anuncios/[slug]`
- `/anunciar`

Nao criar rotas alternativas como `/anuncio`, `/perfil`, `/ads` ou `/acompanhante`.

## Breadcrumbs

Breadcrumbs so podem linkar rotas existentes.

- Cidade: `Inicio` linka `/`; `Acompanhantes`, UF e cidade atual ficam como texto.
- Bairro: `Inicio` linka `/`; cidade linka `/acompanhantes/[uf]/[cidade]`; `Acompanhantes`, UF e bairro atual ficam como texto.
- Anuncio: `Inicio` linka `/`; cidade e bairro linkam apenas quando a localidade existir; anuncio atual fica como texto.

Enquanto `/acompanhantes` e `/acompanhantes/[uf]` nao existirem, esses itens nunca devem ser links.

## Cidade para bairro

A pagina de cidade pode exibir bairros quando a lista local tiver perfis suficientes para justificar a navegacao. Bairro vazio nao deve ser indexado em producao futura sem gate.

## Bairro para cidade

A pagina de bairro sempre deve oferecer retorno para a cidade, evitando pagina isolada e reforcando a hierarquia local.

## Anuncio para localidade

A pagina de anuncio deve apontar para cidade e bairro quando a API local trouxer localidade publica. O contato continua mediado e a midia continua controlada pela politica publica.

## Home

A home deve apontar para exemplos controlados de cidade, bairro, anuncio e `/anunciar`, sem depender de dados reais e sem exibir texto tecnico.

## Riscos antes do cutover

- linkar bairro vazio em massa;
- criar paginas sem perfis suficientes;
- remover noindex antes de revisar Search Console;
- publicar canonical de producao em ambiente local;
- deixar admin, API ou rotas fracas no sitemap.
- aprovar mudanca visual publica sem `scripts/local/validar-layout-publico-renderizado.ps1`.
