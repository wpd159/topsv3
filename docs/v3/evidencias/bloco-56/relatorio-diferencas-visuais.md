# Relatorio - diferencas visuais

## Status

`BLOQUEADO_PARIDADE_VISUAL_PRODUCAO`

## Diferencas por rota

### Home `/`

- Producao: header real com altura aproximada de 129px desktop e 89px mobile, tipografia Poppins, fundo branco, cerca de 40 links, 10 botoes e 8 midias/imagens na primeira pagina capturada.
- V3 local: sem header equivalente na home, tipografia Arial/Helvetica, fundo cinza claro, 4 links, nenhum botao e nenhuma midia.
- Impacto: a primeira dobra nao comunica o site real.

### Cidade `/acompanhantes/go/goiania`

- Producao: listagem extensa, 103 links, cerca de 93 botoes desktop/95 mobile e 22 imagens.
- V3 local: shell sintetico com 3 links, nenhum botao e nenhuma imagem.
- Impacto: densidade, cards, midia e navegacao de listagem nao estao em paridade.

### Bairro `/acompanhantes/go/goiania/setor-bueno`

- Producao: 45 links, 16 botoes e 5 imagens.
- V3 local: 5 links, nenhum botao e nenhuma imagem.
- Impacto: listagem de bairro ainda parece placeholder documental.

### Detalhe `/anuncios/anuncio-exemplo`

- Producao: header publico, midia, CTAs e estrutura de detalhe real observavel.
- V3 local: shell sintetico, zero imagem, 2 links, 3 botoes e campos do age gate/local.
- Impacto: detalhe de anuncio nao preserva hierarquia visual/midia/contato do site atual.

### Wizard `/anunciar`

- Producao: rota redireciona para `/?next=/anunciar` antes de qualquer aceitacao; a auditoria nao clicou nem aceitou age gate.
- V3 local: wizard abre diretamente com form progressivo.
- Impacto: entrada publica do funil nao esta visualmente alinhada ao comportamento inicial observado em producao.

### Admin `/admin`

- Producao: sem login; redireciona para `/?next=/admin`, com gate/experiencia publica.
- V3 local: shell admin sintetico com login/cards.
- Impacto: nao ha referencia admin publica suficiente para paridade; manter como gate de produto/seguranca separado.

## Diferencas transversais

- Header: ausente/incompativel na V3 local.
- Container: producao usa faixas e secoes extensas; V3 local usa shell central/documental.
- Cores: producao predominantemente branca; V3 local cinza claro.
- Tipografia: producao Poppins; V3 local Arial/Helvetica.
- Cards: producao densa e midiatica; V3 local quase sem cards publicos.
- Botoes/CTAs: producao tem muitos pontos de acao; V3 local reduzido.
- Midia: producao mostra imagens/midias publicas; V3 local usa placeholders/ausencia.
- Mobile: producao preserva header compacto e listagem longa; V3 local vira paineis de conteudo sintetico.

## Plano cirurgico

- Primeiro bloco visual: header, fundo, tipografia e container publico.
- Segundo bloco visual: cards/listagens cidade/bairro com densidade e CTAs equivalentes.
- Terceiro bloco visual: detalhe do anuncio com area de midia e contato/idade em hierarquia proxima da producao.
- Quarto bloco visual: `/anunciar` alinhado ao comportamento inicial observavel, sem executar acao real.
- Quinto bloco visual: validacao humana/Pro com prints lado a lado.
