# Checklist Bloco 29.1 - restore, sanitizacao e validacao

## Preliminar

- [x] `git status --short` conferido.
- [x] `git remote -v` vazio.
- [x] Arquivos staged do Bloco 29 preservados.
- [x] Inventario inicial da execucao criado fora do repositorio.
- [x] Nenhum commit executado nesta etapa.

## Backup

- [x] Backup autorizado permanece fora de `C:\topsv3`.
- [x] SHA-256 esperado conferido.
- [x] SHA-256 encontrado bate com o esperado.
- [x] Conteudo do backup nao foi impresso.
- [x] Dump nao foi convertido para SQL bruto.

## Cliente PostgreSQL

- [x] Imagens Docker PostgreSQL locais verificadas.
- [x] `postgres:16` identificado como insuficiente para custom format 1.16.
- [ ] Cliente/imagem PostgreSQL 17.x compativel encontrado localmente.
- [x] Nenhum `docker pull` executado.
- [x] Nenhuma instalacao de software executada.
- [x] Comando manual sugerido documentado sem executar.

## Restore e sanitizacao

- [ ] Restore local isolado executado.
- [ ] Banco bruto local criado.
- [ ] Banco sanitizado local criado.
- [ ] Sanitizacao real executada.
- [ ] Ausencia de dados sensiveis validada no banco sanitizado.
- [ ] SEO validado com dados sanitizados.

## Proibicoes confirmadas

- [x] Sem producao alterada.
- [x] Sem VPS/producao acessada.
- [x] Sem SQL em producao.
- [x] Sem banco de producao como bancada.
- [x] Sem dump novo de producao.
- [x] Sem backup/dump/SQL bruto no Git ou ZIP.
- [x] Sem midia/documento real copiado.
- [x] Sem slug real bruto versionado.
- [x] Sem Pix/Efi, pagamento, upload, e-mail real ou WhatsApp real.
- [x] Sem remote, push ou commit nao autorizado.

## Status final

```text
BLOQUEADO_PENDENTE_CLIENTE_POSTGRES_COMPATIVEL
```

O Bloco 29.1 nao pode ser aprovado como restore/sanitizacao concluido ate existir cliente PostgreSQL compativel local autorizado.
