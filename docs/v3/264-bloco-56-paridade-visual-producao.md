# Bloco 56 - Paridade visual producao x V3 local

## Status

`BLOQUEADO_PARIDADE_VISUAL_PRODUCAO`

O hardening tecnico do Bloco 56 foi parado em ponto seguro. As alteracoes tecnicas ja validas nao foram desfeitas, mas a evolucao para homologacao/cutover permanece bloqueada porque a V3 local ainda nao possui paridade visual suficiente com a producao publica atual.

## Escopo executado

- Auditoria somente leitura de producao via navegacao GET publica.
- Captura de prints redigidos/pixelados da producao para preservar apenas layout, cor, hierarquia e espacamento.
- Captura de prints da V3 local com dados sinteticos.
- Comparacao de home, listagem por cidade, listagem por bairro, detalhe de anuncio, `/anunciar` e `/admin`.
- Registro de plano cirurgico para ajustes futuros.

## Rotas auditadas

- `/`
- `/acompanhantes/go/goiania`
- `/acompanhantes/go/goiania/setor-bueno`
- `/anuncios/anuncio-exemplo`
- `/anunciar`
- `/admin`

## Evidencias

- Producao redigida: `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/`
- V3 local: `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/`
- Metricas: `docs/v3/evidencias/bloco-56/prints/paridade-visual/metricas-paridade-visual.json`
- Relatorio: `docs/v3/evidencias/bloco-56/relatorio-paridade-visual-producao.md`
- Diferencas: `docs/v3/evidencias/bloco-56/relatorio-diferencas-visuais.md`

## Decisao

Nao aprovar homologacao, cutover, staging real, restore/importacao real ou VPS enquanto a paridade visual publica nao for tratada em bloco proprio e validada por revisao humana/Pro.
