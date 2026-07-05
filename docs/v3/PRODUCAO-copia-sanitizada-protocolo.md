# Producao - protocolo de copia sanitizada

## Regra central

Producao serve para observar. Copia local serve para testar. Banco de producao nunca e ambiente de teste.

## Caminhos permitidos

Backup bruto, restore e dados sanitizados devem ficar fora do repositorio:

```text
C:\topsv3-auditoria-local\backups
C:\topsv3-auditoria-local\restore\bloco-29
C:\topsv3-auditoria-local\sanitizado\bloco-29
```

## Proibicoes

- nao colocar backup/dump em `C:\topsv3`;
- nao versionar backup/dump;
- nao incluir backup/dump no ZIP;
- nao imprimir conteudo do backup;
- nao usar banco de producao como teste;
- nao executar SQL no banco de producao;
- nao gerar dump novo sem autorizacao expressa separada;
- nao baixar midia/documento real.

## Fluxo aprovado

1. Localizar backup autorizado existente.
2. Copiar backup bruto para pasta externa.
3. Conferir tamanho, data, tipo e SHA-256.
4. Restaurar em PostgreSQL local isolado.
5. Criar banco bruto local e banco sanitizado local.
6. Rodar sanitizacao apenas no banco sanitizado.
7. Gerar relatorios sanitizados.
8. Validar V3 contra dados sanitizados ou usar relatorios como origem de importacao futura.

## Estado Bloco 29

Backup encontrado, mas restore local ficou pendente por cliente PostgreSQL compativel ausente.

## Estado Bloco 29.1

O SHA-256 do backup autorizado deve ser conferido antes de qualquer restore. Na execucao do Bloco 29.1 o hash bateu, mas nao havia cliente/imagem PostgreSQL 17.x local. A continuidade exige disponibilizacao local autorizada de cliente compativel, sem usar producao como bancada.

## Estado Bloco 29.2

Foi autorizado apenas `docker pull postgres:17`, mas o Docker daemon local estava indisponivel. O restore continua bloqueado sem uso de producao, VPS ou SQL bruto. Scripts operacionais devem manter `docker run --pull=never`.

## Estado Bloco 29.3

Docker Desktop local ficou disponivel, `postgres:17` foi baixado e `pg_restore -l` aprovou o backup autorizado. Os recursos Docker foram criados exclusivamente com prefixo `topsv3-bloco29`, preservando TopsWI/terceiros.

O restore bruto falhou com `FALHA_PG_RESTORE_RAW`; portanto a copia sanitizada ainda nao existe validada. Nao houve limpeza destrutiva automatica, SQL bruto versionado, dump novo, acesso a producao ou uso de banco de producao.

## Estado Bloco 29.4

Recursos `topsv3-bloco29-*` foram limpos e recriados, sem tocar em `cripto*`/TopsWI. O restore foi reexecutado com `--single-transaction`, mas falhou novamente com diagnostico sanitizado `CONSTRAINT/FK`.

Nao ha copia sanitizada valida. O raw log ficou fora do repositorio e nao foi versionado. A continuidade exige revisao humana/Pro antes de qualquer nova flag ou tentativa.

## Estado Bloco 29.5

O protocolo passa a permitir uma copia de quarentena sem `POST_DATA`, exclusivamente para diagnostico e SEO agregado. Essa copia deve ser sanitizada imediatamente e nao pode ser tratada como staging final.

Resultado: quarentena sem `POST_DATA` criada com 77 tabelas agregadas e sanitizacao aprovada. Uso permitido continua limitado a diagnostico, SEO e agregados.

## Estado Bloco 30

O restore completo nao sera perseguido agora. O gate de copia sanitizada de producao fica adiado para pre-staging/cutover.

Enquanto isso, o desenvolvimento da V3 segue com dados sinteticos locais. A quarentena sanitizada nao pode ser promovida, nao pode validar comportamento transacional final e nao pode servir de base definitiva de importacao.
