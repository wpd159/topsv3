# Bloco 29.5 - restore de quarentena sem POST_DATA

O Bloco 29.5 executa diagnostico controlado da falha `POST_DATA / CONSTRAINT-FK` observada no Bloco 29.4.

Este bloco nao e go-live, nao aprova staging final e nao autoriza importacao definitiva. O banco gerado e uma copia de quarentena, restaurada sem `POST_DATA`, para permitir sanitizacao imediata, diagnostico agregado e avaliacao SEO sem versionar dados reais.

## Premissas

- Backup autorizado externo: `C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump`.
- SHA-256 esperado: `ca1ab4d33e8ac9f484f9c5cf61589014189407c960d1a296cf4b984817f9cee9`.
- Imagem local: `postgres:17`.
- `pg_restore -l` deve passar antes do restore.
- `git remote -v` deve continuar vazio.
- Recursos TopsWI/terceiros devem ser apenas observados e preservados.

## Escopo Autorizado

- Recriar somente `topsv3-bloco29-pg17-quarentena`.
- Recriar somente `topsv3-bloco29-pgdata-quarentena`.
- Reaproveitar ou criar `topsv3-bloco29-net`.
- Montar o backup externo somente leitura.
- Executar `pg_restore` com `--section=pre-data --section=data --no-owner --no-privileges --exit-on-error --single-transaction`.

## Proibicoes

- Restaurar `POST_DATA` neste bloco.
- Usar `--disable-triggers`, `--clean`, `--if-exists` ou flags destrutivas por tentativa.
- Aprovar o banco de quarentena como staging final.
- Versionar log bruto, dump, backup, SQL bruto, midia, documento, slug real, token ou payload sensivel.
- Alterar producao, VPS, banco de producao, recursos TopsWI ou qualquer API externa.

## Saidas Esperadas

- Restore de quarentena sem `POST_DATA`.
- Sanitizacao imediata da quarentena.
- Validacao agregada de ausencia de dados sensiveis.
- Diagnostico agregado de FK/orfandade.
- SEO agregado com quarentena sanitizada.
- Dossie de decisao tecnica A/B/C, sem escolha automatica.

## Resultado da Execucao

- Restore de quarentena sem `POST_DATA`: OK.
- Schemas de usuario agregados: 1.
- Tabelas de usuario agregadas: 77.
- Tabelas com linhas: 55.
- Linhas totais agregadas: 538164.
- Sanitizacao imediata: OK.
- Validacao de ausencia de dados sensiveis: OK, total sensivel 0.
- FKs previstas no TOC: 76.
- Diagnostico agregado de orfandade: gerado, sem nomes reais.
- SEO agregado: gerado, com 520 URLs de anuncio preservaveis estimadas.
- Banco aprovado para staging final: nao.
