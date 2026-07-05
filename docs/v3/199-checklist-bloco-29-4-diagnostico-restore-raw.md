# Checklist Bloco 29.4 - diagnostico restore bruto

## Preliminar

- [x] `git status --short` conferido.
- [x] `git remote -v` vazio.
- [x] Inventario inicial criado fora do repositorio.
- [x] Scanner oficial de arquivos proibidos sem bloqueios.
- [x] Recursos `cripto*`/TopsWI detectados e preservados.
- [x] Sem commit.
- [x] Sem push.

## Script restore

- [x] Relatorios da fase apontam para `docs/v3/evidencias/bloco-29-4`.
- [x] Raw log de `pg_restore` fica fora do repositorio.
- [x] Diagnostico versionado e sanitizado criado.
- [x] `--single-transaction` aplicado.
- [x] `docker run --pull=never` mantido.

## Limpeza segura

- [x] Inventario pre-limpeza registrado.
- [x] Containers proprios `topsv3-bloco29-*` removidos.
- [x] Volumes proprios `topsv3-bloco29-*` removidos.
- [x] Network propria `topsv3-bloco29-net` removida/recriada.
- [x] Nenhum recurso `cripto*`/TopsWI alterado.
- [x] Nenhum `docker prune`.
- [x] Nenhum `docker compose down`.

## Restore

- [x] SHA-256 do backup autorizado conferido.
- [x] `pg_restore -l` aprovado com `postgres:17`.
- [x] Restore bruto reexecutado com `--single-transaction`.
- [ ] Restore bruto concluido.
- [ ] Restore sanitizado concluido.
- [ ] Sanitizacao real executada.
- [ ] Dados sanitizados validados.
- [ ] SEO com dados sanitizados validado.

## Resultado

```text
BLOQUEADO_FALHA_PG_RESTORE_RAW_CONSTRAINT_FK
```

O bloco nao foi aprovado porque a falha se repetiu apos limpeza/recriacao de recursos proprios e uso de `--single-transaction`.
