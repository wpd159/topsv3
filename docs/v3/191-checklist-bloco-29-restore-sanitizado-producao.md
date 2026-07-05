# Checklist Bloco 29 - restore sanitizado de producao

## Preliminar

- [x] `git status --short` conferido.
- [x] `git remote -v` vazio.
- [x] `git log --oneline -5` conferido.
- [x] Commit local de checkpoint do Bloco 28 criado.
- [x] Inventario inicial do Bloco 29 criado fora do repositorio.

## Backup

- [x] Pasta externa `C:\topsv3-auditoria-local\backups` usada.
- [x] Backup local externo procurado.
- [x] Consulta SSH somente leitura usada para localizar backup existente.
- [x] Nenhum dump novo de producao foi gerado.
- [x] Backup existente copiado para pasta externa autorizada.
- [x] SHA-256 do backup registrado.
- [x] Backup/dump fora do repositorio.
- [x] Backup/dump fora do ZIP.

## Restore e sanitizacao

- [x] Tentativa segura de identificar tipo do dump executada.
- [x] Bloqueio operacional documentado.
- [ ] Restore local isolado executado.
- [ ] Banco bruto local criado.
- [ ] Banco sanitizado local criado.
- [ ] Sanitizacao real executada.
- [ ] Validacao real de dados sanitizados executada.
- [ ] Validacao SEO com dados sanitizados executada.

## Motivo das pendencias

- [x] `postgres:16` local nao suporta dump custom formato 1.16.
- [x] Cliente `pg_restore` 17 local nao esta disponivel.
- [x] Nao foi feito pull/instalacao automatica.
- [x] Nao foi usado banco de producao como bancada.
- [x] Bloco 29.1 confirmou novamente SHA do backup e ausencia de cliente 17.x local.

## Documentacao

- [x] Protocolo de copia sanitizada criado.
- [x] Sanitizacao de dados documentada.
- [x] Restore local/staging documentado.
- [x] SEO com dados sanitizados documentado.
- [x] Matriz de campos criada.
- [x] Riscos de privacidade documentados.
- [x] SDD atualizado.

## Validacoes

- [x] build local agregado.
- [x] E2E local descartavel.
- [x] backend compile/test.
- [x] frontend lint/build.
- [x] mobile.
- [x] SEO.
- [x] layout renderizado.
- [x] mapa SEO.
- [x] dados sanitizados executado com `PENDENTE_RESTORE_SANITIZACAO_LOCAL`.
- [x] SEO com dados sanitizados executado com `PENDENTE_DADOS_SANITIZADOS`.
- [x] scanners.
- [x] `git diff --cached --check`.
- [x] `git status --short`.

## Proibicoes

- [x] Sem producao alterada.
- [x] Sem banco de producao usado como teste.
- [x] Sem SQL em banco de producao.
- [x] Sem dump novo de producao.
- [x] Sem backup/dump no repo.
- [x] Sem backup/dump no ZIP.
- [x] Sem dado sensivel versionado.
- [x] Sem midia real versionada.
- [x] Sem migration.
- [x] Sem SQL de schema V3.
- [x] Sem upload.
- [x] Sem e-mail real.
- [x] Sem WhatsApp real.
- [x] Sem pagamento, credito, Pix/Efi, checkout ou webhook.
- [x] Sem importador real definitivo.
- [x] Sem remote.
- [x] Sem push.
- [x] Sem commit depois das alteracoes do Bloco 29.
