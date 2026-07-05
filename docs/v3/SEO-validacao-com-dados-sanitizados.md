# SEO - validacao com dados sanitizados

## Objetivo

Validar impacto SEO usando dados proximos da realidade, mas sem versionar slugs reais, nomes, telefones, e-mails, documentos ou midia.

## Saidas esperadas quando restore/sanitizacao estiver OK

- quantas URLs de anuncio seriam preservadas;
- quantas ficariam `noindex`/removidas;
- quantas exigem decisao;
- quantas cidades tem conteudo suficiente;
- quantos bairros tem conteudo suficiente;
- quantas paginas seriam fracas/vazias;
- classificacao das URLs desconhecidas do Bloco 28.

## Placeholders obrigatorios em relatorios

- `/anuncios/[slug-sanitizado-1]`;
- `/acompanhantes/[uf]/[cidade]`;
- `/acompanhantes/[uf]/[cidade]/[bairro]`.

## Estado Bloco 29

Validacao SEO com dados sanitizados ficou pendente por ausencia de restore local isolado e sanitizacao executada.

As 45 URLs desconhecidas do Bloco 28 seguem como pendencia de classificacao de cutover.

## Estado Bloco 29.1

O script de SEO com dados sanitizados foi preparado para gerar apenas agregados, sem lista bruta de slugs reais. Nesta execucao, o resultado continua pendente porque a validacao de dados sanitizados depende de restore/sanitizacao concluida.

## Estado Bloco 29.2

A validacao SEO com dados sanitizados continua pendente porque o cliente PostgreSQL 17.x nao foi disponibilizado localmente. Nenhum canonical, sitemap ou robots de producao foi alterado.

## Estado Bloco 29.3

SEO com dados sanitizados permanece pendente. Embora Docker e `postgres:17` estejam OK, o restore bruto falhou antes de criar base sanitizada valida.

Nao houve alteracao de canonical, sitemap, robots de producao, lista bruta de slugs, midia real ou URL real versionada.

## Estado Bloco 29.4

SEO com dados sanitizados segue pendente. A reexecucao protegida do restore falhou antes de gerar banco sanitizado valido.

Nenhum canonical, sitemap, robots de producao, lista bruta de slugs, midia real, URL real ou log bruto foi versionado.

## Estado Bloco 29.5

A quarentena sanitizada pode gerar apenas agregados SEO: URLs preservaveis estimadas, noindex/removidas estimadas, cidades/bairros com conteudo suficiente e pendencias das 45 URLs desconhecidas. Nenhum canonical, sitemap ou robots de producao deve ser alterado.

Resultado: SEO agregado com quarentena sanitizada gerou 520 URLs de anuncio preservaveis estimadas, 3 URLs noindex/removidas estimadas e manteve 45 URLs desconhecidas pendentes.

## Estado Bloco 30

O Bloco 30 encerra a frente de dados reais como gate adiado: a quarentena sanitizada nao sera perseguida agora e nao e staging final. Ela permanece apenas como evidencia agregada auxiliar de SEO, sem POST_DATA e sem validacao transacional final.

O desenvolvimento local da V3 segue com base sintetica versionavel, sem restore novo, sem sanitizacao nova, sem Docker e sem producao. Validacoes publicas, SEO local, sitemap, robots e canonical devem usar fixture sintetica ate que a Opcao A seja cumprida em pre-staging/cutover.

Opcoes consolidadas:

- Opcao A: obrigatoria antes de homologacao/cutover real, com novo backup consistente ou correcao da origem.
- Opcao B: limitada a insumo auxiliar agregado, nunca base final.
- Opcao C: bloqueada ate revisao Pro/humana em bloco futuro.
