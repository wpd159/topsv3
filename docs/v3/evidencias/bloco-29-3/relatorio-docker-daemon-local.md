# Relatorio - Docker daemon local e preservacao TopsWI

- Bloco: 29.3
- Docker Desktop iniciado nesta execucao: sim
- Docker daemon local: OK
- docker ps -a exit code: 0
- docker network ls exit code: 0
- docker volume ls exit code: 0
- docker image ls postgres exit code: 0
- Recurso TopsWI/terceiro alterado: nao
- Container de outro projeto parado/removido: nao
- Volume de outro projeto removido: nao
- Network de outro projeto removida: nao
- docker prune executado: nao
- docker compose down executado: nao

## Containers nao Tops V3 detectados e preservados
- cripto-postgres   postgres:16   Exited (255) 5 days ago   0.0.0.0:19002->5432/tcp
- cripto-redis      redis:7       Up 6 minutes              0.0.0.0:6379->6379/tcp, [::]:6379->6379/tcp

## Possiveis recursos TopsWI/terceiros por nome
- cripto-postgres   postgres:16   Exited (255) 5 days ago   0.0.0.0:19002->5432/tcp
- cripto-redis      redis:7       Up 6 minutes              0.0.0.0:6379->6379/tcp, [::]:6379->6379/tcp

## Networks nao Tops V3 detectadas e preservadas
- cripto_default   bridge    local

## Volumes
- Volumes totais listados: 122
- Volumes nao Tops V3 preservados: 122
- Lista completa de volumes nao foi versionada para reduzir ruido; nenhum volume foi removido.

## Imagens PostgreSQL locais
- postgres:17
- postgres:16
