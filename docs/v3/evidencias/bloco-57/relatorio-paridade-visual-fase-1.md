# Relatorio - paridade visual publica fase 1

## Referencia usada

A referencia visual usada foi a auditoria redigida do Bloco 56, com prints publicos da producao pixelados/redigidos e prints da V3 local em `docs/v3/evidencias/bloco-56/prints/paridade-visual/`.

## Diferencas tratadas nesta fase

- Home local deixou de parecer painel documental simples e ganhou header publico, hero, CTAs e caminhos publicos.
- Fundo cinza dominante foi reduzido para fundo publico branco.
- Tipografia foi aproximada da producao observada, usando `Poppins` com fallback de sistema.
- Header publico foi adicionado com marca, navegacao e CTA de anuncio gratis.
- Cards de listagem ficaram mais densos, com area de foto segura, preco, localidade, beneficios e CTA.
- Listagens de cidade e bairro passaram a ter secao de perfis com heading proprio e resumo.
- Links principais passaram a usar o slug sintetico validado `demo-goiania-livre-premium`.

## Diferencas ainda pendentes

- Detalhe de anuncio ainda precisa de uma fase visual propria de paridade fina.
- Wizard `/anunciar` ainda precisa ser comparado novamente com a producao observavel, respeitando o age gate de producao.
- Admin continua fora da paridade publica desta fase e depende de referencia humana se necessario.
- Midia real da producao nao foi reproduzida; a V3 continua usando placeholder seguro sem URL real.
- A aprovacao final de paridade visual ainda exige revisao humana/Pro.

## Status do gate

`BLOQUEADO_PARIDADE_VISUAL_PRODUCAO` permanece aberto, agora com mitigacao parcial em home, cidade e bairro.

## Prints gerados no Bloco 57

- `docs/v3/evidencias/bloco-57/prints/desktop-home.png`
- `docs/v3/evidencias/bloco-57/prints/mobile-home.png`
- `docs/v3/evidencias/bloco-57/prints/desktop-cidade-goiania.png`
- `docs/v3/evidencias/bloco-57/prints/mobile-cidade-goiania.png`
- `docs/v3/evidencias/bloco-57/prints/desktop-bairro-setor-bueno.png`
- `docs/v3/evidencias/bloco-57/prints/mobile-bairro-setor-bueno.png`

O validador tambem gerou prints auxiliares de `/anunciar`, anuncios sinteticos, sitemap e robots para proteger regressao renderizada.
