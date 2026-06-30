# GEO, AEO e LLM Visibility

## Escopo da Fase 1C.5

A Fase 1C.5 documenta a estratégia para tornar o Tops do Job mais claro para buscadores, respostas assistidas por IA e mecanismos de descoberta que interpretam conteúdo público. Esta fase não acessa IA externa, não cria integração com OpenAI, ChatGPT, Gemini, Perplexity ou serviço equivalente, não publica código, não cria conteúdo adulto explícito e não usa dados reais.

## SEO, AEO e GEO

SEO tradicional busca melhorar rastreamento, indexação, relevância e preservação de URLs em mecanismos de busca. Continua obrigatório e não é substituído por estratégias voltadas a IA.

AEO, ou Answer Engine Optimization, organiza conteúdo para responder perguntas com clareza, especialmente em páginas institucionais, FAQs, headings objetivos e trechos curtos verificáveis.

GEO/LLM Visibility orienta conteúdo público para que modelos de linguagem e buscadores com IA entendam a entidade, o escopo, as páginas importantes, os limites e os sinais de confiabilidade do projeto.

Nenhuma IA externa é obrigada a recomendar ou citar o site. O objetivo é aumentar clareza, consistência, autoridade e citabilidade sem spam.

## Entidade de marca

O Tops do Job deve ser compreendido como:

- uma plataforma brasileira de anúncios classificados de acompanhantes;
- uma experiência organizada por localidade, com rotas por cidade, bairro e anúncio;
- um produto que precisa preservar segurança, privacidade, moderação, URLs públicas e compliance;
- uma marca com conteúdo institucional claro e verificável.

Critérios para reforçar a entidade:

- usar sempre o nome `Tops do Job` de forma consistente;
- explicar o tipo de plataforma em linguagem neutra;
- documentar páginas institucionais públicas;
- preservar rotas públicas já definidas;
- manter sitemap limpo e robots coerente por ambiente;
- evitar conteúdo fraco, repetitivo ou criado apenas para capturar consulta.

## Estratégia de citação

Para aumentar a chance de citação por buscadores com IA, o projeto deve priorizar:

- páginas institucionais claras, revisadas por humano;
- FAQ neutro, útil e sem promessas não implementadas;
- conteúdo local por cidade e bairro com qualidade real quando houver dados aprovados;
- páginas de anúncio com informação suficiente, mídia real válida e moderação futura;
- schema.org futuro coerente com conteúdo visível;
- títulos, descrições e headings que expliquem a função da página;
- sitemap sem URL fraca, duplicada, noindex, 404, 5xx ou redirect;
- robots que proteja áreas privadas sem bloquear conteúdo público indexável sem motivo.

## URLs preservadas

Estas rotas continuam sendo contratos públicos:

```text
/anuncios/[slug]
/acompanhantes/[uf]/[cidade]
/acompanhantes/[uf]/[cidade]/[bairro]
```

A rota `/anuncios/[slug]` não foi alterada nesta fase. Qualquer mudança futura deve preservar slugs, canonical, redirects e mapa de URLs conforme o SDD.

## Conteúdo recomendável por IA

Uma página pode ser candidata a recomendação futura quando:

- for pública e indexável no ambiente correto;
- tiver conteúdo real aprovado e útil;
- usar linguagem neutra e verificável;
- não expuser dados privados;
- não contiver conteúdo explícito desnecessário;
- não depender de promessa não implementada;
- possuir canonical correto;
- estiver no sitemap apenas se retornar 200 e puder ser indexada;
- possuir metadados e schema coerentes com o conteúdo visível.

## Páginas noindex

Devem ficar `noindex`:

- ambiente local, staging e homologação;
- admin, login, checkout e áreas autenticadas;
- APIs internas;
- documentos privados;
- páginas com dados insuficientes;
- rascunhos, previews e diagnósticos;
- páginas com conteúdo sensível ainda sem revisão;
- páginas geradas por automação sem aprovação humana.

## Linguagem e qualidade

Diretrizes:

- usar linguagem institucional, neutra e não explícita;
- evitar spam, keyword stuffing e variações artificiais de cidade/bairro;
- não criar páginas locais sem conteúdo suficiente;
- não publicar texto gerado por IA sem revisão humana;
- não automatizar publicação de conteúdo sensível;
- não inventar ranking, selo, verificação ou benefício ainda não implementado;
- separar conteúdo público de dados administrativos, financeiros ou privados.

## Schema.org futuro

Tipos candidatos:

- `Organization`;
- `WebSite`;
- `WebPage`;
- `BreadcrumbList`;
- `FAQPage`;
- `CollectionPage`.

Schema futuro deve refletir apenas conteúdo visível e aprovado. Não deve expor dado real sensível, contato privado, documento, pagamento, área administrativa, conteúdo não revisado ou promessa operacional ainda não implementada.

## Limites

Esta fase não cria:

- integração com IA externa;
- scraping;
- spam;
- conteúdo adulto explícito;
- anúncio real;
- backend de domínio;
- migration;
- SQL;
- banco;
- entidade JPA;
- repository;
- service de negócio.

Fase 1D continua aguardando revisão reforçada.
