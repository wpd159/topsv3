# Relatorio - restore local isolado

- Bloco: 29.1
- Resultado: PENDENTE_CLIENTE_POSTGRES_COMPATIVEL
- Detalhe: Somente postgres:16 foi encontrado localmente; ele nao le o dump custom format 1.16. Nao foi feito docker pull.
- Backup externo: `C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump`
- Restore externo: `C:\topsv3-auditoria-local\restore\bloco-29-1`
- Container: `topsv3-bloco291-postgres`
- Imagem PostgreSQL: `nenhuma`
- Banco bruto local: `topsv3_prod_raw_bloco291`
- Banco sanitizado local: `topsv3_prod_sanitizado_bloco291`
- Producao alterada: nao
- Banco de producao acessado/testado: nao
- Conteudo do backup impresso: nao
- Dump/SQL bruto gerado: nao
- Backup versionado: nao

## Passos
- SHA-256 do backup autorizado conferido antes do restore.
