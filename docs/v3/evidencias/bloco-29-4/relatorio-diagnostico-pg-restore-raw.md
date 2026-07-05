# Relatorio - diagnostico sanitizado pg_restore

- Bloco: 29.4
- Status: FALHA_PG_RESTORE_RAW
- Etapa operacional: RAW
- Fase da falha: POST_DATA
- Tipo provavel: CONSTRAINT/FK
- Falha parece causada por volume parcial anterior: False
- Seguro tentar novamente com volume limpo: False
- Necessario ajuste de flags: False
- Necessario parar e pedir decisao humana: True
- Log bruto externo nao versionado: sim
- Log bruto versionado: nao
- Tabela/constraint/chave/slug/dado pessoal/payload exibido: nao

## Redacoes aplicadas
- Objetos: [objeto-redigido].
- Constraints: [constraint-redigida].
- Schemas: [schema-redigido].
- Chaves Key (...)=(...): redigidas integralmente.
- Caminhos, tokens, URLs, e-mails, telefones, IPs, buckets e storage keys: redigidos.

## Conclusao operacional

- A falha ocorreu novamente apos limpeza/recriacao dos recursos proprios e uso de `--single-transaction`.
- Nao aplicar flags adicionais por suposicao.
- Proxima acao exige revisao humana/Pro do diagnostico bruto externo, sem versionar log bruto ou dados reais.
