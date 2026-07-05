# Relatorio - inventario Docker pre-limpeza

- Bloco: 29.4
- Modo: somente leitura antes da limpeza controlada
- Recursos de terceiro alterados: nao
- docker prune executado: nao
- docker compose down executado: nao

## Containers topsv3-bloco29
- topsv3-bloco29-pg17-sanitizado | postgres:17 | Up 17 minutes
- topsv3-bloco29-pg17-bruto | postgres:17 | Up 17 minutes

## Containers terceiros/TopsWI preservados
- cripto-postgres | postgres:16 | Exited (255) 5 days ago
- cripto-redis | redis:7 | Up 24 minutes

## Networks topsv3-bloco29
- topsv3-bloco29-net | bridge | local

## Networks terceiros/TopsWI preservadas
- cripto_default | bridge | local

## Volumes topsv3-bloco29
- topsv3-bloco29-pgdata-bruto
- topsv3-bloco29-pgdata-sanitizado

## Volumes terceiros/TopsWI preservados
- cripto_postgres_dados

## Imagens postgres locais
- postgres:17
- postgres:16
