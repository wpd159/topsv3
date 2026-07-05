# Relatorio - backup autorizado

- Bloco: 29
- Politica: localizar backup existente, sem gerar dump novo e sem abrir conteudo sensivel.
- Diretorio externo consultado: `C:\topsv3-auditoria-local\backups`
- Diretorio dentro do repositorio: nao

## Resultado

- Backup autorizado localizado: sim
- Caminho externo: `C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump`
- Tamanho bytes: 29520615
- Data local do arquivo: 2026-07-03 23:57:26
- SHA-256: `ca1ab4d33e8ac9f484f9c5cf61589014189407c960d1a296cf4b984817f9cee9`
- Tipo inferido: postgres-custom-provavel
- Extensao: `.dump`
- Comprimido/pacote: False
- Conteudo impresso: nao
- Backup dentro do repositorio: nao

## Observacoes

- O arquivo bruto nao deve entrar no Git, ZIP, chat ou relatorio versionado alem deste resumo sanitizado.
- Se for pacote com midia/documentos, nao extrair midia/documentos reais.
