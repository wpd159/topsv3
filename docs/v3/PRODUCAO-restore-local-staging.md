# Producao - restore local e staging

## Local isolado

Restore deve usar PostgreSQL local isolado, preferencialmente Docker, sem volume persistente quando possivel e sem exposicao publica.

Bancos planejados:

- `topsv3_prod_raw_bloco29`;
- `topsv3_prod_sanitizado_bloco29`.

## Sequencia

1. Restaurar backup bruto no banco raw local.
2. Criar copia local sanitizada.
3. Rodar sanitizacao no banco sanitizado.
4. Nunca conectar a V3 ao banco raw.
5. Validar relatorios e compatibilidade.

## Staging futuro

Somente depois de restore/sanitizacao local OK:

- revisar schema de origem;
- mapear importacao para V3;
- validar SEO e URLs;
- revisar privacidade;
- preparar staging sem dado sensivel bruto.

## Estado Bloco 29

O backup exige cliente PostgreSQL compativel com custom format 1.16. A imagem local `postgres:16` nao suporta esse dump, e nenhum pull/instalacao foi executado automaticamente.

## Estado Bloco 29.1

O restore completo passou a exigir SHA-256 valido antes de qualquer tentativa operacional. O script seleciona imagem PostgreSQL 17.x apenas se ela ja existir localmente e executa `pg_restore -l` como gate inicial. Sem imagem/cliente compativel local, retorna `PENDENTE_CLIENTE_POSTGRES_COMPATIVEL`.

## Estado Bloco 29.2

Os `docker run` do diagnostico e restore foram protegidos com `--pull=never`. O unico pull autorizado, `docker pull postgres:17`, falhou por Docker daemon indisponivel; portanto `pg_restore -l` e restore nao foram executados.

## Estado Bloco 29.3

Docker daemon local ficou OK, `docker pull postgres:17` concluiu e `pg_restore -l` passou. O restore usa somente recursos `topsv3-bloco29-*`: network, volumes e containers bruto/sanitizado.

O restore bruto falhou em `FALHA_PG_RESTORE_RAW`. O container bruto ficou parcialmente populado por contagem estrutural agregada; nenhuma limpeza destrutiva foi executada automaticamente.

## Estado Bloco 29.4

Os recursos proprios parcialmente populados foram limpos e recriados com autorizacao limitada. O restore passou a usar `--single-transaction`.

A falha repetiu com diagnostico sanitizado `CONSTRAINT/FK` na fase `POST_DATA`. Como `--single-transaction` estava ativo, a contagem agregada apos falha ficou em 0 tabelas no bruto e 0 tabelas no sanitizado.

## Estado Bloco 29.5

O restore de quarentena sem `POST_DATA` e permitido para diagnostico controlado. Ele nao restaura FKs/constraints/indexes finais, nao aprova staging final e nao deve ser usado para testar comportamento transacional definitivo da V3.

Resultado: restore de quarentena passou, mas o staging final permanece bloqueado porque `POST_DATA` nao foi restaurado.
