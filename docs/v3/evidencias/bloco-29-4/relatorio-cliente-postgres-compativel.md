# Relatorio - cliente PostgreSQL compativel

- Bloco: 29.4
- Resultado: OK_CLIENTE_POSTGRES_COMPATIVEL
- Detalhe: Cliente local aceitou pg_restore -l do backup autorizado sem imprimir conteudo de tabela no relatorio.
- Cliente minimo esperado: PostgreSQL 17.x
- Cliente selecionado: postgres:17
- Versao selecionada: pg_restore (PostgreSQL) 17.10 (Debian 17.10-1.pgdg13+1)
- Docker pull automatico executado pelo script: nao
- Docker run com pull bloqueado: sim
- Instalacao local executada: nao
- VPS/producao acessada: nao
- Conteudo do backup impresso: nao

## Imagens locais observadas
- postgres:16
- postgres:17

## Clientes testados
- postgres:17 -> pg_restore (PostgreSQL) 17.10 (Debian 17.10-1.pgdg13+1)
