# Relatorio - restore de quarentena sem POST_DATA

- Bloco: 29.5
- Resultado: OK_RESTORE_QUARENTENA_SEM_POSTDATA
- Detalhe: Restore de quarentena sem POST_DATA concluido para diagnostico; nao aprovado para staging final.
- Banco e de quarentena: sim
- Aprovado para staging final: nao
- POST_DATA restaurado: nao
- Constraints/FKs/indexes/finalizacoes de POST_DATA podem estar ausentes: sim
- Uso permitido: diagnostico, sanitizacao imediata e SEO agregado
- Backup externo: `C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump`
- Restore externo: `C:\topsv3-auditoria-local\restore\bloco-29-5`
- Container: `topsv3-bloco29-pg17-quarentena`
- Volume: `topsv3-bloco29-pgdata-quarentena`
- Network: `topsv3-bloco29-net`
- Banco: `topsv3_quarentena`
- Imagem PostgreSQL: `postgres:17`
- Flags usadas: `--section=pre-data --section=data --no-owner --no-privileges --exit-on-error --single-transaction`
- Flags proibidas usadas: nao
- Log bruto versionado: nao
- Conteudo do backup impresso: nao
- Recursos TopsWI/terceiros alterados: nao
- Producao/VPS/banco de producao acessados: nao

## Contagens agregadas
- schemas_usuario: 1
- tabelas_usuario: 77
- tabelas_com_linhas: 55
- linhas_total_agregado: 538164

## Passos
- SHA-256 do backup autorizado conferido.
- pg_restore -l validado com postgres:17.
- Container proprio de quarentena removido: topsv3-bloco29-pg17-quarentena.
- Volume proprio de quarentena removido: topsv3-bloco29-pgdata-quarentena.
- Network propria reaproveitada: topsv3-bloco29-net.
- Volume proprio criado: topsv3-bloco29-pgdata-quarentena.
- Container de quarentena criado sem publicar portas.
- Container de quarentena respondeu pg_isready.
- Banco de quarentena respondeu SELECT 1 antes do restore.
- Restore de quarentena concluido sem POST_DATA.
- Estado operacional externo registrado fora do repositorio.
