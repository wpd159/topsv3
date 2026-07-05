# Bloco 29.4 - diagnostico seguro do restore bruto

## Objetivo

Diagnosticar de forma segura a falha `FALHA_PG_RESTORE_RAW`, limpar/recriar somente recursos Docker proprios `topsv3-bloco29-*`, reexecutar restore com protecao contra restauracao parcial e prosseguir para sanitizacao apenas se o restore concluísse.

## Resultado desta execucao

Status:

```text
BLOQUEADO_FALHA_PG_RESTORE_RAW_CONSTRAINT_FK
```

O diagnostico confirmou:

- `git remote -v` vazio;
- Docker local disponivel;
- `postgres:17` local disponivel;
- `pg_restore -l` aprovado;
- recursos `cripto*`/TopsWI preservados;
- recursos `topsv3-bloco29-*` limpos e recriados;
- restore executado com `--single-transaction`;
- restore bruto falhou novamente;
- raw log salvo somente fora do repositorio;
- relatorio versionado contem apenas classificacao sanitizada.

## Diagnostico sanitizado

- Fase da falha: `POST_DATA`.
- Tipo provavel: `CONSTRAINT/FK`.
- Falha causada por volume parcial anterior: nao.
- Seguro tentar novamente apenas com volume limpo: nao.
- Necessario ajuste de flags por suposicao: nao.
- Necessario parar e pedir decisao humana/Pro: sim.

Nenhum nome real de tabela, constraint, chave, slug, dado pessoal, caminho sensivel, URL, token, bucket, storage key ou payload foi versionado.

## Protecao contra restauracao parcial

O restore foi reexecutado com:

```text
--no-owner
--no-privileges
--exit-on-error
--single-transaction
```

Apos a falha, as contagens estruturais agregadas indicaram:

- banco bruto `topsv3_bruto`: 0 tabelas;
- banco sanitizado `topsv3_sanitizado`: 0 tabelas.

Isso confirma que `--single-transaction` evitou nova restauracao parcial.

## Limpeza controlada

Foram removidos/recriados somente recursos proprios:

- `topsv3-bloco29-pg17-bruto`;
- `topsv3-bloco29-pg17-sanitizado`;
- `topsv3-bloco29-pgdata-bruto`;
- `topsv3-bloco29-pgdata-sanitizado`;
- `topsv3-bloco29-net`.

Nao houve `docker prune`, `docker system prune`, `docker volume prune`, `docker network prune` ou `docker compose down`.

## Gates nao executados

Como o restore falhou novamente, nao foram executados:

- sanitizacao real;
- validacao de dados sanitizados;
- validacao SEO com dados sanitizados;
- E2E/API usando dados sanitizados.

## Confirmacoes

- Producao alterada: nao.
- VPS/producao acessada: nao.
- Banco de producao usado: nao.
- SQL em producao: nao.
- Dump novo de producao: nao.
- SQL bruto gerado/versionado: nao.
- Log bruto versionado: nao.
- Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real ou API externa real: nao.
- Remote, push ou commit nao autorizado: nao.
- Fase posterior iniciada: nao.
