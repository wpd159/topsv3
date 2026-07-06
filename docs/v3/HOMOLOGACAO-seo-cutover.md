# HOMOLOGACAO - SEO real e cutover

## Principio

SEO real e cutover exigem mapa final de URLs, validacao antes/depois e plano de rollback. Este documento e contrato; nao acessa producao e nao executa deploy.

## Itens obrigatorios

- Mapa final de URLs.
- Resolucao das 45 URLs desconhecidas ainda pendentes, se nao resolvidas em bloco futuro.
- Plano de 301.
- Canonical.
- Sitemap.
- Robots.
- Search Console.
- Preservacao de trafego.
- Validacao antes/depois.
- Rollback SEO.

## Mapa final de URLs

O mapa final deve classificar:

- URL preservada;
- URL redirecionada;
- URL removida;
- URL desconhecida;
- URL bloqueada por dado insuficiente;
- URL que exige decisao humana.

As 45 URLs desconhecidas permanecem pendentes enquanto nao houver resolucao documental. Elas nao podem ser ignoradas no cutover.

## 301, canonical, sitemap e robots

- 301 deve apontar para destino unico e aprovado.
- Canonical local nunca deve vazar dominio de producao por engano.
- Canonical de cutover deve ser revisado antes de deploy.
- Sitemap nao deve incluir admin, API, rotas fracas ou URLs bloqueadas.
- Robots deve bloquear ambientes locais/homologacao quando aplicavel.
- Producao so pode ser indexavel apos decisao de cutover.

## Search Console

- Exportar baseline antes do cutover.
- Registrar cobertura, paginas com trafego e consultas relevantes.
- Comparar apos cutover.
- Nao versionar export bruto se contiver dado sensivel ou operacional indevido.

## Validacao antes/depois

Antes:

- mapa de URLs aprovado;
- 301 validado;
- sitemap/robots/canonical revisados;
- paginas cidade/bairro/anuncio renderizadas;
- sem texto tecnico visivel.

Depois:

- amostragem de URLs preservadas;
- amostragem de 301;
- sitemap acessivel;
- robots correto;
- Search Console acompanhado;
- queda anormal de trafego com criterio de rollback.

## Rollback SEO

- Reverter deploy de rotas/canonical/sitemap/robots quando criterio de rollback disparar.
- Preservar mapa anterior.
- Registrar causa e acao.
- Nao improvisar redirecionamento em producao sem aprovacao humana.
