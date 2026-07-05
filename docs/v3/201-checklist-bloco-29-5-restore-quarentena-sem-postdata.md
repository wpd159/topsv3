# Checklist - Bloco 29.5

## Seguranca Inicial

- [x] Inventario inicial salvo fora do repositorio.
- [x] `git remote -v` vazio.
- [x] Recursos `cripto*`/TopsWI apenas observados e preservados.
- [x] Scanners de arquivos proibidos e secrets sem bloqueios.
- [x] Nenhum dump, backup, SQL bruto, midia real, documento real, `.env`, certificado, chave, token ou banco local dentro do repositorio.

## Docker Quarentena

- [x] Inventario Docker pre-quarentena gerado.
- [x] Somente container de quarentena `topsv3-bloco29-pg17-quarentena` removido/recriado, se necessario.
- [x] Somente volume de quarentena `topsv3-bloco29-pgdata-quarentena` removido/recriado, se necessario.
- [x] Network propria `topsv3-bloco29-net` criada ou reaproveitada.
- [x] Nenhum `docker prune`, `docker compose down`, `volume prune` ou `network prune`.
- [x] Nenhum recurso fora do prefixo `topsv3-bloco29` alterado.

## Restore

- [x] SHA-256 do backup conferido.
- [x] `pg_restore -l` passou com `postgres:17`.
- [x] Restore usou `--section=pre-data`.
- [x] Restore usou `--section=data`.
- [x] Restore usou `--single-transaction`.
- [x] Restore nao restaurou `POST_DATA`.
- [x] Relatorio declara que o banco de quarentena nao e staging final.
- [x] Staging final nao foi aprovado.

## Sanitizacao e Validacao

- [x] Sanitizacao executada imediatamente na quarentena.
- [x] Validacao de dados sanitizados passou sem valores reais.
- [x] Validacao posterior registrou `total_sensivel=0`.
- [x] Erros agregados de sanitizacao por coluna ficaram registrados como alerta residual para revisao Pro/humana.
- [x] Diagnostico agregado de FK/orfandade gerado sem nomes sensiveis, IDs, slugs ou chaves reais.
- [x] SEO agregado gerado sem lista bruta de URLs/slugs.
- [x] Dossie A/B/C gerado sem escolher automaticamente o caminho.
- [x] Decisao Pro/humana permanece pendente.

## Fechamento

- [x] Build/validacoes locais executados.
- [x] Scanners de seguranca executados antes e depois do staging.
- [x] `git diff --check` e `git diff --cached --check` OK.
- [x] Arquivos staged.
- [x] ZIP de revisao gerado na Area de Trabalho.
- [x] Sem commit, push, remote, producao, VPS, Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real ou API externa real.
