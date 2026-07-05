# Relatorio - restore local isolado

- Bloco: 29.3
- Resultado: FALHA_PG_RESTORE_RAW
- Detalhe: pg_restore falhou no container bruto local; conteudo do erro nao foi versionado para evitar exposicao.
- Backup externo: `C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump`
- Restore externo: `C:\topsv3-auditoria-local\restore\bloco-29-3`
- Network exclusiva: `topsv3-bloco29-net`
- Container bruto: `topsv3-bloco29-pg17-bruto`
- Container sanitizado: `topsv3-bloco29-pg17-sanitizado`
- Volume bruto: `topsv3-bloco29-pgdata-bruto`
- Volume sanitizado: `topsv3-bloco29-pgdata-sanitizado`
- Imagem PostgreSQL: `postgres:17`
- Banco bruto local: `topsv3_bruto`
- Banco sanitizado local: `topsv3_sanitizado`
- Producao alterada: nao
- Banco de producao acessado/testado: nao
- Conteudo do backup impresso: nao
- Dump/SQL bruto gerado: nao
- Backup versionado: nao
- Recursos TopsWI/terceiros alterados: nao
- Docker prune executado: nao
- Docker compose down executado: nao

## Passos
- SHA-256 do backup autorizado conferido antes do restore.
- Cliente PostgreSQL compativel identificado localmente: postgres:17.
- pg_restore -l aceitou o backup sem registrar conteudo de tabela no relatorio.
- Network exclusiva criada: topsv3-bloco29-net.
- Volume exclusivo criado: topsv3-bloco29-pgdata-bruto.
- Volume exclusivo criado: topsv3-bloco29-pgdata-sanitizado.
- Container proprio criado sem porta publicada: topsv3-bloco29-pg17-bruto.
- Container proprio criado sem porta publicada: topsv3-bloco29-pg17-sanitizado.
- PostgreSQL local isolado respondeu pg_isready: topsv3-bloco29-pg17-bruto.
- PostgreSQL local isolado respondeu pg_isready: topsv3-bloco29-pg17-sanitizado.
