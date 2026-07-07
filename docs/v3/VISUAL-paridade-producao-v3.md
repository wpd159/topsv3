# VISUAL - Paridade producao x V3

## Status

`BLOQUEADO_PARIDADE_VISUAL_PRODUCAO`

A V3 local esta funcionalmente validada em dados sinteticos para varios fluxos, mas ainda nao representa o visual publico real do Tops do Job. A homologacao/cutover nao deve ser aprovada antes de uma rodada visual dedicada.

## Achado central

A producao atual usa uma experiencia publica com header real, tipografia Poppins, fundo branco, listagens densas, imagens/midias, multiplos botoes/CTAs e estrutura extensa por cidade/bairro. A V3 local ainda usa shell sintetico, fundo cinza claro, Arial, poucos links, poucas secoes, sem midia real/sintetica equivalente e componentes com classes internas de skeleton publico.

## Plano cirurgico recomendado

1. Criar inventario visual de componentes de producao com screenshots redigidos: header, navegacao, home, card, listagem, detalhe e gate de idade.
2. Ajustar CSS publico da V3 para aproximar container, espacos, branco de fundo, escala de titulos, botoes e densidade, sem nova paleta e sem nova tipografia inventada.
3. Recriar header publico equivalente ao observavel em producao, preservando rotas V3.
4. Reestruturar cards/listagens para aproximar proporcao, quantidade de informacao, CTAs e grade responsiva.
5. Ajustar detalhe do anuncio para aproximar hierarquia visual, area de midia, contato/idade e informacoes publicas.
6. Revisar `/anunciar` em bloco proprio, porque a producao redireciona para `/?next=/anunciar` antes de aceitar age gate; a V3 local exibe wizard diretamente.
7. Tratar `/admin` como sem referencia publica suficiente; producao redireciona para fluxo publico/gate, entao admin local nao deve ser usado como criterio de paridade visual publica.
8. Validar desktop e mobile sem scroll horizontal, sem scroll lock e sem elemento solto/flutuante.

## Regras de ajuste futuro

- Somente CSS/componentes publicos quando o bloco for visual.
- Sem banco.
- Sem regra de negocio nova.
- Sem redesign novo.
- Sem nova paleta.
- Sem nova tipografia.
- Sem scroll lock.
- Sem `document.body.style.overflow`.
- Sem producao alterada.
- Sem dado real versionado.
