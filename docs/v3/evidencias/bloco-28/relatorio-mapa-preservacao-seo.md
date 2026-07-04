# Relatorio - mapa de preservacao SEO

## Resultado

Mapa sanitizado criado em:

```text
docs/v3/SEO-mapa-preservacao-urls-v3.md
```

## Cobertura

- Home: manter.
- Cidade: preservar `/acompanhantes/[uf]/[cidade]`.
- Bairro: preservar `/acompanhantes/[uf]/[cidade]/[bairro]` quando houver conteudo suficiente.
- Anuncio: preservar `/anuncios/[slug]` quando publicado/indexavel.
- Admin/API: manter fora do sitemap.
- Rotas antigas/proibidas: tratar apenas por 301 seguro ou remocao, sem recriar rota paralela.

## Protecao

A lista bruta completa de anuncios reais nao foi versionada. Slugs reais de anuncio ficam apenas na saida bruta externa do inventario, fora do repositorio.

## Estado

Cutover SEO permanece bloqueado ate mapa completo aprovado com Search Console, canonical, sitemap, robots e plano de rollback.
