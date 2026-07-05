# Relatorio - restore local isolado

- Bloco: 29
- Resultado: PENDENTE_CLIENTE_POSTGRES_COMPATIVEL
- Detalhe: Dump em formato PostgreSQL custom mais novo que o pg_restore disponivel na imagem postgres:16. Cliente PostgreSQL compativel deve ser disponibilizado localmente sem baixar ferramenta automaticamente.
- Backup externo: `C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump`
- Restore externo: `C:\topsv3-auditoria-local\restore\bloco-29`
- Container planejado: `topsv3-bloco29-postgres`
- Imagem PostgreSQL planejada: `postgres:16`
- Banco bruto planejado: `topsv3_prod_raw_bloco29`
- Banco sanitizado planejado: `topsv3_prod_sanitizado_bloco29`
- Producao alterada: nao
- Banco de producao acessado: nao
- Conteudo do backup impresso: nao
- Backup versionado: nao

## Passos
- Imagem postgres:16 encontrada localmente.
