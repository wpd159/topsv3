# SEO e URLs

## Objetivo

Preservar autoridade, indexação, tráfego orgânico e links externos durante a construção e virada da V3.

SEO não é etapa final. A V3 deve nascer com registro de URLs, canonical, redirects, sitemap e robots como fontes de verdade testáveis.

## Domínio canônico

O domínio canônico e:

```text
https://topsdojob.com
```

Regras:

- Sem `www`.
- Todo acesso a `https://www.topsdojob.com` deve retornar 301 para a URL equivalente sem `www`.
- Canonical, sitemap, robots, JSON-LD, Open Graph, IndexNow e links internos devem usar a mesma fonte de domínio.
- Ambientes de staging, preview e homologação não podem emitir canonical de produção em página indexável.

## URLs públicas preservadas

As rotas abaixo são contratos públicos:

```text
/anuncios/[slug]
/acompanhantes/[uf]/[cidade]
/acompanhantes/[uf]/[cidade]/[bairro]
/sitemap.xml
/robots.txt
```

Qualquer mudança interna de tecnologia deve preservar essas rotas.

Na Fase 1C.4, essas rotas passam a existir no frontend apenas como skeleton local seguro. Elas usam placeholders neutros, `noindex`, canonical local via `NEXT_PUBLIC_CANONICAL_DOMAIN` e não carregam anúncios reais, cidades reais como dados, fotos reais, busca real, JSON-LD final, backend de domínio ou API externa.

Na Fase 1C.8, a preservação dessas rotas passa a ter validação local estática por `scripts/local/validar-rotas-publicas-seo-local.ps1`. Essa validação protege `/anuncios/[slug]` como contrato público absoluto e falha se surgirem rotas alternativas de anúncio como `/anuncio/[id]`, `/perfil/[slug]`, `/acompanhante/[slug]` ou `/ads/[slug]`.

SEO público real continua reservado para a Fase 4, e a consolidação de SEO programático, sitemap definitivo, robots de produção e dados estruturados finais continua reservada para a Fase 6.

## Registro de URL

A V3 deve manter uma entidade `seo_url` para controlar:

- caminho público;
- canonical completo;
- entidade relacionada;
- status esperado;
- indexabilidade;
- entrada no sitemap;
- última validação;
- motivo de noindex, quando aplicável.

Essa tabela ou visão operacional deve ser a fonte para sitemap, auditoria SEO e comparação com o legado.

## Canonical

Regras por tipo:

- Anúncio: `https://topsdojob.com/anuncios/[slug]`
- Cidade: `https://topsdojob.com/acompanhantes/[uf]/[cidade]`
- Bairro: `https://topsdojob.com/acompanhantes/[uf]/[cidade]/[bairro]`
- Sitemap: `https://topsdojob.com/sitemap.xml`
- Robots: `https://topsdojob.com/robots.txt`

Proibicoes:

- Canonical com `www`.
- Canonical apontando para URL noindex.
- Canonical para URL que retorna 404, 5xx ou redirect.
- Canonical diferente do destino final após redirects.

## Sitemap

O sitemap deve:

- conter apenas URLs canônicas sem `www`;
- conter apenas páginas 200 e indexáveis;
- excluir admin, login, checkout, APIs, staging, previews e páginas sem inventário/conteúdo suficiente;
- ser particionado se o volume exigir;
- ter geração atômica;
- registrar data de geração;
- ser validado por crawler antes do cutover.

Fontes de sitemap:

- anúncios aprovados e publicáveis;
- cidades com anúncios publicáveis e conteúdo suficiente;
- bairros com anúncios publicáveis e conteúdo suficiente;
- conteúdo/blog publicado e aprovado;
- páginas institucionais publicáveis.

## Robots.txt

`/robots.txt` deve ter uma única fonte por ambiente.

Produção:

- permitir páginas públicas indexáveis;
- bloquear rotas administrativas, APIs internas e áreas privadas;
- apontar para `https://topsdojob.com/sitemap.xml`.

Staging/homologação:

- bloquear indexação integralmente;
- não enviar IndexNow;
- não aparecer em sitemap de produção.

Local:

- bloquear indexação integralmente;
- apontar sitemap apenas para URLs locais de skeleton;
- não emitir canonical de produção;
- não usar domínio de produção salvo em documentação;
- não enviar IndexNow.

## Páginas indexáveis

Podem ser indexáveis quando passarem nas regras de qualidade:

- página de anúncio aprovado, ativo e com mídia real válida;
- página de cidade com anúncios publicáveis e conteúdo local suficiente;
- página de bairro com anúncios publicáveis e conteúdo local suficiente;
- post de blog publicado e aprovado;
- página institucional revisada.

## Páginas com noindex

Devem ser `noindex`:

- admin e dashboard;
- login, cadastro, recuperação e áreas autenticadas;
- checkout e pagamentos;
- busca interna com filtros combinatorios fracos;
- páginas locais sem inventário suficiente;
- anúncio rascunho, pendente, rejeitado, pausado ou removido;
- previews;
- staging e homologação;
- páginas com mídia quebrada quando isso comprometer qualidade mínima;
- URLs temporárias de importação ou diagnóstico.

## Redirects 301

Regras:

- `www` para sem `www`.
- Slug antigo para slug novo, quando inevitável.
- URL legada equivalente para URL V3.
- Apenas um salto para o destino final.
- Sem loops.
- Sem redirect para página noindex.
- Redirects devem ser testados antes da virada.

Quando uma URL não puder ser preservada, a decisão deve ser documentada com motivo e alternativa. Remoção definitiva deve ser exceção.

## Slugs

Regras:

- Preservar slug do anúncio sempre que possível.
- Slug é contrato público, não detalhe cosmético.
- Geração de slug novo deve ser determinística.
- Duplicidade deve gerar pendência `SLUG_DUPLICADO`.
- Slug alterado exige `seo_redirect`.
- Slug de cidade/bairro deve ser estável e derivado de cadastro saneado.

## Cidade e bairro

Páginas locais dependem de:

- UF válida;
- cidade válida;
- bairro válido quando presente;
- slug canônico;
- anúncios publicáveis;
- metadados únicos;
- regra de indexação aprovada.

Página local sem inventário suficiente deve existir apenas se houver decisão editorial/SEO clara; caso contrário, `noindex` ou não inclusão no sitemap.

## Anúncios

Página de anúncio indexável exige:

- status aprovado/publicável;
- slug único;
- mídia real válida;
- cidade válida;
- canonical sem `www`;
- metadados gerados por fonte única;
- dados estruturados consistentes;
- telefone/WhatsApp tratado sem exposição indevida.

## Mapa URL atual x URL V3

Antes da implementação, deve existir um mapa com:

- URL atual;
- status atual;
- canonical atual;
- tipo de página;
- entidade legada;
- URL V3 esperada;
- ação: manter, redirecionar, noindex, remover justificado;
- status esperado V3;
- prioridade SEO;
- validado em;
- observação.

Exemplo de colunas:

```csv
url_atual,tipo,entidade_legado,url_v3,acao,status_esperado,prioridade,observacao
/anuncios/exemplo,anuncio,123,/anuncios/exemplo,MANTER,200,ALTA,slug preservado
/anuncios/slug-antigo,anuncio,456,/anuncios/slug-novo,REDIRECT_301,301,ALTA,slug duplicado saneado
```

## Search Console

Validações obrigatórias:

- propriedade do domínio sem `www`;
- sitemap enviado após cutover;
- amostragem de URLs prioritárias com inspeção manual;
- monitoramento de cobertura;
- monitoramento de 404;
- monitoramento de canonical escolhido pelo Google;
- comparação de páginas indexadas antes/depois;
- acompanhamento de CTR, impressões e posição por pelo menos 30 dias.

## IndexNow

IndexNow deve:

- usar domínio canônico sem `www`;
- receber eventos de publicação, alteração relevante e remoção;
- ser idempotente;
- operar por fila;
- não rodar em staging;
- ter logs de sucesso/falha sem dados sensíveis.

## GEO, AEO e LLM Visibility

GEO/AEO/LLM Visibility é atividade transversal para tornar o projeto mais compreensível por buscadores e mecanismos de resposta com IA. Essa estratégia não substitui SEO tradicional, sitemap limpo, robots seguro, canonical correto, redirects e conteúdo público revisado.

Diretrizes:

- reforçar a entidade `Tops do Job` com páginas institucionais claras;
- preservar `/anuncios/[slug]`, `/acompanhantes/[uf]/[cidade]` e `/acompanhantes/[uf]/[cidade]/[bairro]`;
- usar FAQ neutro e revisado;
- preparar schema.org futuro coerente com conteúdo visível;
- manter `llms.txt` como guia institucional público, sem substituir sitemap ou robots;
- evitar spam, keyword stuffing e conteúdo fraco;
- manter aprovação humana para conteúdo sensível ou público final;
- não permitir que IA externa ou automação publique conteúdo sozinha.

IA externa não pode ser controlada e não é obrigada a recomendar o site. O objetivo é aumentar clareza, autoridade e citabilidade sem comprometer segurança, privacidade ou compliance.

## Gate SEO antes da virada

A V3 não pode virar produção se:

- houver canonical com `www`;
- sitemap contiver URL 404, 5xx, redirect ou noindex;
- rotas prioritárias não responderem;
- redirects tiverem cadeia ou loop;
- staging estiver indexável;
- anúncios ativos perderem slug sem redirect;
- páginas locais prioritárias divergirem do baseline sem aprovação.
