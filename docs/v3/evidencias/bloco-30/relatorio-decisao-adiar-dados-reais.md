# Relatorio - decisao de adiar dados reais

## Decisao registrada

O Bloco 29 permanece materialmente aberto e adiado para pre-staging/cutover. O desenvolvimento atual da V3 nao depende de teste com dados reais.

## Motivos

- O restore completo do Bloco 29 falhou em `POST_DATA / CONSTRAINT-FK`.
- A quarentena sanitizada do Bloco 29.5 foi util apenas para diagnostico agregado.
- Quarentena sem `POST_DATA` nao valida integridade transacional final.
- Dados reais/sanitizados nao sao necessarios para o proximo ciclo local de desenvolvimento.

## Regras mantidas

- Opcao A: obrigatoria antes de homologacao/cutover real.
- Opcao B: limitada a insumo auxiliar agregado.
- Opcao C: bloqueada ate revisao Pro/humana.
- Producao continua proibida como bancada de teste.
- Banco de producao nao pode ser usado.

## Confirmacoes

- Restore novo executado: nao.
- POST_DATA restaurado: nao.
- Sanitizacao nova executada: nao.
- Correcao de orfaos executada: nao.
- Docker acessado: nao no Bloco 30.
- Producao/VPS acessados: nao.
- Dump, backup, SQL bruto ou log bruto versionados: nao.
