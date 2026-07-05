# Checklist Bloco 29.3 - restore e sanitizacao com Docker local

## Preliminar

- [x] `git status --short` conferido.
- [x] `git remote -v` vazio.
- [x] Inventario inicial criado fora do repositorio.
- [x] Scanner oficial de arquivos proibidos sem bloqueios.
- [x] Sem commit.
- [x] Sem push.

## Docker local

- [x] Docker Desktop localizado e iniciado localmente.
- [x] Docker daemon local OK.
- [x] Diagnostico `docker ps -a`, `docker network ls`, `docker volume ls` e `docker image ls postgres` executado antes de criar recursos do Tops V3.
- [x] Recursos de TopsWI/terceiros detectados apenas em modo leitura e preservados.
- [x] Nenhum `docker prune`.
- [x] Nenhum `docker compose down`.
- [x] Nenhum container/volume/network de terceiro parado ou removido.

## PostgreSQL 17

- [x] `docker pull postgres:17` executado.
- [x] Imagem `postgres:17` inspecionada localmente.
- [x] `pg_restore -l` aprovado com cliente PostgreSQL 17.
- [x] `docker run` operacional com `--pull=never`.
- [x] Containers temporarios de diagnostico nomeados com prefixo `topsv3-bloco29`.

## Recursos exclusivos Tops V3

- [x] Network `topsv3-bloco29-net`.
- [x] Volume `topsv3-bloco29-pgdata-bruto`.
- [x] Volume `topsv3-bloco29-pgdata-sanitizado`.
- [x] Container `topsv3-bloco29-pg17-bruto`.
- [x] Container `topsv3-bloco29-pg17-sanitizado`.
- [x] Backup montado somente leitura quando necessario.
- [x] Nenhuma porta publicada nos containers de restore/sanitizacao.

## Restore e sanitizacao

- [x] SHA-256 do backup autorizado conferido.
- [ ] Restore bruto concluido.
- [ ] Restore no container sanitizado concluido.
- [ ] Sanitizacao real executada somente no banco/container sanitizado.
- [ ] Dados sanitizados validados.
- [ ] SEO com dados sanitizados validado.

## Status final

```text
BLOQUEADO_FALHA_PG_RESTORE_RAW
```

O bloco nao foi aprovado porque `pg_restore` falhou no container bruto. Nenhuma limpeza destrutiva foi executada automaticamente.
