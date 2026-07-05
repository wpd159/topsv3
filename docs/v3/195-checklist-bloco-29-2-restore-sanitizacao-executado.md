# Checklist Bloco 29.2 - restore e sanitizacao com PostgreSQL 17

## Preliminar

- [x] `git status --short` conferido.
- [x] `git remote -v` vazio.
- [x] Inventario inicial criado fora do repositorio.
- [x] Varredura inicial nao encontrou backup/dump/env/cert/banco local dentro de `C:\topsv3`.
- [x] Sem commit.
- [x] Sem push.

## Hardening

- [x] `docker run` do diagnostico usa `--pull=never`.
- [x] `docker run` do restore usa `--pull=never`.
- [x] Container PostgreSQL local isolado usa `docker run --pull=never`.
- [x] Scripts apontam por padrao para evidencias do Bloco 29.2.

## PostgreSQL 17

- [x] Comando autorizado `docker pull postgres:17` executado.
- [ ] Imagem `postgres:17` disponibilizada localmente.
- [x] Falha registrada por Docker daemon indisponivel.
- [x] Nenhum outro download tentado.

## Restore e sanitizacao

- [ ] `pg_restore -l` com cliente 17.x aprovado.
- [ ] Restore local isolado executado.
- [ ] Banco bruto local criado.
- [ ] Banco sanitizado local criado.
- [ ] Sanitizacao real executada.
- [ ] Dados sanitizados validados.
- [ ] SEO com dados sanitizados validado.

## Validacoes locais sem Docker

- [x] Toolchain local diagnosticada.
- [x] Build backend/frontend local validado.
- [x] Persistencia JPA estatica validada.
- [x] UI mobile estatica validada.
- [x] SEO publico, rotas publicas e mapa de preservacao SEO validados.
- [x] Layout publico renderizado validado.
- [x] Scanners de seguranca executados.
- [x] `git diff --check` e `git diff --cached --check` executados.

## Gates pendentes por bloqueio operacional

- [ ] E2E local descartavel.
- [ ] Smoke HTTP completo da API local.
- [ ] Diagnostico `pg_restore -l` com imagem PostgreSQL 17.
- [ ] Restore local isolado.
- [ ] Sanitizacao real.
- [ ] Validacao de dados sanitizados.
- [ ] Validacao SEO com dados sanitizados.

## Proibicoes confirmadas

- [x] Sem producao alterada.
- [x] Sem VPS/producao acessada.
- [x] Sem SQL em producao.
- [x] Sem banco de producao como bancada.
- [x] Sem dump novo.
- [x] Sem SQL bruto.
- [x] Sem backup/dump no Git ou ZIP.
- [x] Sem midia/documento real copiado.
- [x] Sem slug real bruto versionado.
- [x] Sem Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real ou API externa real.
- [x] Sem remote, push ou commit nao autorizado.

## Status final

```text
BLOQUEADO_PENDENTE_CLIENTE_POSTGRES_COMPATIVEL
```
