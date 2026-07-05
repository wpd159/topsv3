# Relatorio - diagnostico agregado FK quarentena

- Bloco: 29.5
- Resultado: OK_DIAGNOSTICO_FKS_QUARENTENA
- Banco de quarentena sanitizada: `topsv3_quarentena`
- POST_DATA restaurado: nao
- Banco aprovado para staging final: nao
- FKs previstas no TOC do dump: 76
- Falhas POST_DATA / FK classificadas no diagnostico bruto sanitizado: 1
- Nomes de tabela/constraint/chave/slug/ID real listados: nao

## FKs previstas por dominio inferido do TOC
- anuncio: 31
- midia: 1
- cidade_bairro: 2
- premium: 0
- pagamentos: 4
- auditoria: 3
- moderacao: 0
- usuario_admin: 10
- desconhecido: 25

## Heuristica de orfandade na quarentena sanitizada
- Formato: dominio=relacoes_avaliadas:registros_orfaos_agregados
- anuncio=38:321604
- auditoria=2:0
- cidade_bairro=8:0
- desconhecido=4:0
- pagamentos=3:20
- premium=1:0
- usuario_admin=13:0

## Limites
- Como POST_DATA nao foi restaurado, as FKs finais nao existem no banco de quarentena.
- A heuristica nao substitui validacao final com restore completo consistente.
- Qualquer correcao local de orfaos exige novo bloco e decisao humana/Pro.
