# SEO - mapa de preservacao de URLs V3

## Regra central

A V3 deve preservar URLs publicas corretas ja existentes e so redirecionar quando houver mudanca inevitavel, aprovada e testada.

O mapa versionado e sanitizado. A lista bruta completa de anuncios reais fica fora do repositorio.

## Tabela modelo

| URL atual | Tipo | URL V3 correspondente | Acao | Canonical esperado | Sitemap esperado | Risco | Prioridade | Depende de dado real | Exige 301 | Observacao |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `/` | home | `/` | manter | propria URL | sim | medio | alta | nao | nao | preservar marca e home publica |
| `/acompanhantes/[uf]/[cidade]` | cidade | `/acompanhantes/[uf]/[cidade]` | manter | propria URL | sim, se util | alto | alta | sim | nao | depende de conteudo real aprovado |
| `/acompanhantes/[uf]/[cidade]/[bairro]` | bairro | `/acompanhantes/[uf]/[cidade]/[bairro]` | manter ou noindex | propria URL | somente se util | alto | media | sim | nao | nao indexar bairro vazio/fraco |
| `/anuncios/[slug]` | anuncio | `/anuncios/[slug]` | manter | propria URL | sim, se publicado/indexavel | alto | alta | sim | nao | slug real nao deve ser listado no repo |
| `/anuncios/[slug-removido]` | anuncio | pendente | noindex/remover/410 ou 301 contextual | nao aplicar canonical ativo | nao | alto | alta | sim | talvez | depende de status do anuncio |
| `/admin` | proibido | nenhum | noindex/bloquear | nenhum | nao | critico | alta | nao | nao | nunca indexar |
| `/api` | proibido | nenhum | noindex/bloquear | nenhum | nao | critico | alta | nao | nao | nunca indexar |
| `/anuncio/[id]` | rota antiga/proibida | `/anuncios/[slug]`, se houver equivalencia segura | pendente | URL final validada | nao | alto | alta | sim | talvez | nao criar rota paralela |
| `/perfil/[slug]` | rota antiga/proibida | pendente | remover ou 301 seguro | URL final validada | nao | alto | media | sim | talvez | so redirecionar com equivalencia |
| `/acompanhante/[slug]` | rota antiga/proibida | pendente | remover ou 301 seguro | URL final validada | nao | alto | media | sim | talvez | rota paralela proibida |
| `/ads/[slug]` | rota antiga/proibida | pendente | remover ou 301 seguro | URL final validada | nao | alto | media | sim | talvez | rota paralela proibida |

## Regras

- Manter URL quando a rota atual ja e correta e compativel com a V3.
- Usar 301 apenas quando existe equivalencia publica segura.
- Nao redirecionar anuncio removido para pagina generica sem criterio.
- Nao inserir admin, API, paginas vazias, skeleton ou paginas fracas no sitemap.
- Local e staging devem permanecer bloqueados ate gate de cutover.
- Canonical de producao deve ser `https://topsdojob.com` somente em ambiente aprovado.

## Pendencias

- Exportacao completa de Search Console.
- Mapa completo de URLs reais com status de anuncio, sem versionar lista bruta.
- Decisao por URL de anuncio removido, pausado, bloqueado ou nao indexavel.
- Teste de redirects antes de qualquer deploy.
