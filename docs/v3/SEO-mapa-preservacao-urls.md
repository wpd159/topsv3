# Mapa de preservacao de URLs

## Rotas publicas prioritarias

| Funcao | URL V3 | Status local |
| --- | --- | --- |
| Home | `/` | preservada |
| Pagina de anuncio | `/anuncios/[slug]` | contrato absoluto |
| Listagem por cidade | `/acompanhantes/[uf]/[cidade]` | preservada |
| Listagem por bairro | `/acompanhantes/[uf]/[cidade]/[bairro]` | preservada |
| Captacao de anunciante | `/anunciar` | wizard publico local |
| Sitemap | `/sitemap.xml` | local seguro |
| Robots | `/robots.txt` | local bloqueado |

## Rotas proibidas

Nao criar rotas alternativas publicas para anuncio:

- `/anuncio/[id]`;
- `/anuncio/[slug]`;
- `/perfil/[slug]`;
- `/acompanhante/[slug]`;
- `/ads/[slug]`.

## Regras de preservacao

- Se a producao atual ja usa uma rota correta, a V3 deve preservar o comportamento.
- Redirect so deve existir quando houver mudanca inevitavel e documentada.
- Canonical deve apontar para a URL publica final apenas em ambiente aprovado.
- Ambiente local deve permanecer em `noindex`.
- Sitemap nao pode conter endpoint de API.
- Sitemap nao pode conter admin.
- Sitemap nao pode conter URL de producao enquanto `APP_ENV=local`.

## Pendencias futuras

- Mapa final de redirects depende de staging/homologacao.
- Sitemap final deve ser gerado a partir de dados aprovados, sem URL fraca.
- JSON-LD final depende de conteudo e midia publica aprovados.
- Search Console deve ser usado para validar quedas, ganhos e consultas locais apos cutover.

## Bloco 27

O sitemap local controlado passa a preservar exemplos seguros de:

- `/`;
- `/acompanhantes/go/goiania`;
- `/acompanhantes/go/goiania/setor-bueno`;
- `/anuncios/anuncio-exemplo`;
- `/anunciar`.

Essas URLs sao apenas referencias locais de estrutura. O sitemap final de producao dependera de inventario real aprovado e Search Console completo.
