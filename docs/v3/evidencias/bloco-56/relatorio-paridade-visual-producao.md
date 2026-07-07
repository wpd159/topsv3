# Relatorio - paridade visual producao x V3 local

## Status

`BLOQUEADO_PARIDADE_VISUAL_PRODUCAO`

O Bloco 56 tecnico foi parado em ponto seguro. A auditoria visual demonstrou que a V3 local ainda nao tem paridade visual suficiente com a producao publica atual. Nenhuma correcao visual ampla foi executada neste complemento.

## Consulta a producao

- Necessidade: confirmar visual publico atual porque a revisao apontou que a V3 local ainda parece skeleton tecnico.
- Modo: GET/navegacao publica em navegador, sem login e sem formulario.
- Rotas: `/`, `/acompanhantes/go/goiania`, `/acompanhantes/go/goiania/setor-bueno`, `/anuncios/anuncio-exemplo`, `/anunciar`, `/admin`.
- Evidencia obtida: prints redigidos/pixelados e metricas de layout.
- Producao alterada: nao.
- Dados reais versionados: nao; prints brutos ficaram temporariamente fora do repositorio, foram pixelados e removidos.

## Prints

Os prints de producao foram redigidos/pixelados para preservar somente layout, cores e espacamento:

- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/desktop-home.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/mobile-home.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/desktop-cidade-goiania.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/mobile-cidade-goiania.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/desktop-bairro-setor-bueno.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/mobile-bairro-setor-bueno.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/desktop-anuncio-sintetico.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/mobile-anuncio-sintetico.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/desktop-anunciar.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/mobile-anunciar.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/desktop-admin.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/producao-redacted/mobile-admin.png`

Prints V3 local:

- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/desktop-home.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/mobile-home.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/desktop-cidade-goiania.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/mobile-cidade-goiania.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/desktop-bairro-setor-bueno.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/mobile-bairro-setor-bueno.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/desktop-anuncio-sintetico.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/mobile-anuncio-sintetico.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/desktop-anunciar.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/mobile-anunciar.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/desktop-admin.png`
- `docs/v3/evidencias/bloco-56/prints/paridade-visual/v3-local/mobile-admin.png`

## Resultado resumido

- Producao usa Poppins; V3 local usa Arial/Helvetica.
- Producao tem header publico real; home local nao tem header equivalente.
- Producao usa fundo branco; V3 local usa cinza claro dominante.
- Producao tem imagens/midias e listagens densas; V3 local quase nao tem midia/cards.
- Producao tem muitos CTAs e elementos de navegacao; V3 local tem poucos links e shells mais documentais.
- `/anunciar` em producao redireciona para `/?next=/anunciar` antes de aceitar fluxo; V3 local abre wizard diretamente.
- `/admin` em producao nao foi acessado com login e redireciona para fluxo publico/gate; V3 local exibe shell admin sintetico.

## Decisao

Homologacao/cutover seguem reprovados ate bloco visual dedicado corrigir paridade publica e passar por revisao humana/Pro.
