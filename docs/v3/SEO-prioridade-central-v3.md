# SEO como prioridade central da V3

## Decisao

SEO e preservacao de URL sao prioridade de produto da V3, nao acabamento posterior.

A V3 deve proteger trafego organico atual, reduzir risco de perda de indexacao e crescer intencao local com paginas uteis para buscas como `acompanhante em [cidade]`.

## Principios

- preservar rotas publicas corretas antes de criar novas rotas;
- manter `/anuncios/[slug]` como contrato absoluto da pagina de anuncio;
- manter `/acompanhantes/[uf]/[cidade]` e `/acompanhantes/[uf]/[cidade]/[bairro]` como base de intencao local;
- tratar `/anunciar` como fluxo publico importante para captacao;
- nao criar rotas alternativas como `/perfil`, `/ads`, `/anuncio` ou `/acompanhante`;
- nao publicar paginas fracas, duplicadas ou geradas em massa sem revisao humana;
- nao emitir canonical de producao em ambiente local;
- manter admin sempre `noindex`;
- remover `noindex` publico somente em cutover aprovado;
- validar sitemap e robots antes de qualquer homologacao/cutover.

## Escopo local atual

O ambiente local continua seguro:

- robots bloqueia indexacao;
- sitemap usa `localUrl`;
- canonical local nao aponta para producao;
- paginas publicas podem consumir API local, mas a UI publica nao deve exibir texto tecnico como "skeleton" ou "API local";
- dados reais, midia real, contato real e producao continuam fora do escopo.

## Regra de conteudo

Conteudo publico final deve ser escrito para pessoas, revisado por humano e coerente com o comportamento atual do site em producao.

A V3 nao deve trocar trafego de marca por paginas genericas. O objetivo e preservar marca e aumentar intencao local qualificada.

## Gates

Antes de homologacao ou cutover:

- validar rotas publicas preservadas;
- validar ausencia de rotas alternativas;
- validar sitemap sem API e sem URL fraca;
- validar canonical;
- validar robots;
- revisar Search Console;
- mapear redirects quando houver mudanca inevitavel;
- confirmar que paginas publicas nao mostram textos internos de ambiente;
- confirmar mobile estavel.

## Bloco 27

O Bloco 27 materializa a prioridade SEO nas paginas locais de home, cidade, bairro e anuncio:

- `frontend/src/lib/seo/publicSeo.ts` centraliza metadata publica;
- cidade mira `Acompanhantes em [Cidade] - [UF]`;
- bairro mira `Acompanhantes em [Bairro], [Cidade] - [UF]`;
- anuncio preserva `/anuncios/[slug]` e linka cidade/bairro quando disponivel;
- breadcrumbs e linkagem interna sao obrigatorios;
- sitemap local fica sem API, admin, rotas fracas ou dominio de producao;
- robots local permanece bloqueado ate cutover aprovado.

## Bloco 28

O Bloco 28 transforma SEO em gate operacional de preservacao:

- inventario publico sanitizado de robots, sitemap e metadados;
- lista bruta completa de URLs reais fora do repositorio;
- mapa de preservacao versionado apenas com padroes e amostras sanitizadas;
- Search Console completo pendente de exportacao manual;
- cutover SEO bloqueado ate mapa completo aprovado;
- `scripts/local/validar-mapa-preservacao-seo-local.ps1` passa a ser gate obrigatorio.
