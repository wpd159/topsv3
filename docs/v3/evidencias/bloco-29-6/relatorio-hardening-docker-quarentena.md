# Relatorio - hardening Docker quarentena

- Bloco: 29.6
- Resultado: HARDENING_DOCKER_QUARENTENA_EXECUTADO
- Novo restore executado: nao
- Nova sanitizacao executada: nao
- Diagnostico FK novo executado: nao
- SEO novo com banco executado: nao
- Recursos TopsWI/terceiros alterados: nao
- `docker prune` executado: nao
- `docker compose down` executado: nao

## Recursos autorizados

- Container autorizado: `topsv3-bloco29-pg17-quarentena`.
- Volume autorizado: `topsv3-bloco29-pgdata-quarentena`.
- Network autorizada: `topsv3-bloco29-net`.
- Banco autorizado: `topsv3_quarentena`.

## Guardas adicionadas

- `scripts/local/producao-restore-quarentena-sem-postdata.ps1`: guarda exata para container, volume, network e banco.
- `scripts/local/producao-sanitizar-db-quarentena-local.ps1`: guarda exata para container e banco.
- `scripts/local/validar-dados-quarentena-sanitizada-local.ps1`: guarda exata para container e banco.
- `scripts/local/diagnosticar-fks-quarentena-sanitizada-local.ps1`: guarda exata para container e banco.
- `scripts/local/validar-seo-quarentena-sanitizada-local.ps1`: guarda exata para container e banco.

## Falha segura esperada

Se algum nome operacional for diferente do autorizado, os scripts devem parar com `FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO`.
