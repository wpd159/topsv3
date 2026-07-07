# Relatorio - Transplante cards, grids e detalhe

## Arquivos V3 alterados

- `frontend/src/app/globals.css`;
- `frontend/src/modules/public/components/PublicMidiaPlaceholder.tsx`;
- `frontend/src/modules/public/components/PublicAnuncioDetalhe.tsx`.

## Adaptacoes

- O placeholder de midia passou a representar galeria em revisao com composicao visual de miniaturas, sem carregar imagem real.
- O card publico recebeu ajustes de densidade, sombra leve, borda, placeholder mais parecido com card real e preco em destaque.
- O grid publico ficou levemente mais denso, usando largura minima menor e gap coerente com a producao.
- O detalhe do anuncio ganhou estrutura de galeria, miniaturas informativas, resumo lateral, preco, localizacao, marcadores e bloco de descricao/beneficios.
- O CTA do detalhe segue informativo e nao executa acao externa. O fluxo real de contato permanece em `PublicMetricActions`, mediado pelo backend.

## Telas impactadas

- `/acompanhantes/go/goiania`;
- `/acompanhantes/go/goiania/setor-bueno`;
- `/anuncios/demo-goiania-livre-premium`;
- `/anuncios/demo-goiania-bloqueado`.

## Pendencias visuais

- Wizard `/anunciar` ainda precisa fase propria.
- Footer publico ainda precisa avaliacao para nao trazer botao fixo/flutuante da producao.
- Admin visual segue fora deste bloco.
- Detalhe ainda depende de revisao humana/Pro para comparar com a producao real em diferentes estados de midia.
