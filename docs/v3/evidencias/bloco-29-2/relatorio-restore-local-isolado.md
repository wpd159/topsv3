# Relatorio - restore local isolado

- Bloco: 29.2
- Resultado: PENDENTE_CLIENTE_POSTGRES_COMPATIVEL
- Detalhe: restore nao executado porque `docker pull postgres:17` falhou por Docker daemon indisponivel.
- Restore externo planejado: `C:\topsv3-auditoria-local\restore\bloco-29-2`
- Container planejado: `topsv3-bloco292-postgres`
- Banco bruto planejado: `topsv3_prod_raw_bloco292`
- Banco sanitizado planejado: `topsv3_prod_sanitizado_bloco292`
- Producao alterada: nao
- Banco de producao acessado/testado: nao
- Conteudo do backup impresso: nao
- Dump/SQL bruto gerado: nao
- Backup versionado: nao

## Passos executados

- SHA-256 do backup autorizado conferido.
- Scripts endurecidos para `docker run --pull=never`.
- Restore interrompido antes de `pg_restore -l` porque nao ha cliente 17.x local disponivel.
