# Relatorio - riscos residuais de dados

- Bloco: 29.3
- Resultado: BLOQUEADO_FALHA_PG_RESTORE_RAW
- Producao alterada: nao
- Banco de producao usado como bancada: nao
- Backup/dump versionado: nao
- Conteudo sensivel versionado: nao
- Recursos TopsWI/terceiros alterados: nao

## Riscos residuais

- O restore bruto falhou durante `pg_restore` no container `topsv3-bloco29-pg17-bruto`.
- O container bruto ficou com restauracao parcial detectada por contagem estrutural agregada de 77 tabelas.
- O container sanitizado foi criado, mas permaneceu sem tabelas restauradas.
- A sanitizacao real nao foi executada.
- A validacao de ausencia de CPF/e-mail/telefone/IP/storage/Pix/Efi ainda nao pode aprovar sem banco sanitizado.
- SEO com dados sanitizados ainda nao gerou agregados reais.

## Mitigacao

- Manter backup bruto fora do repositorio e fora do ZIP.
- Nao conectar a aplicacao V3 ao container bruto.
- Nao executar limpeza destrutiva automatica sem decisao explicita.
- Manter recursos Docker do Tops V3 isolados com prefixo `topsv3-bloco29`.
- Preservar containers, volumes, networks e compose de TopsWI/terceiros.
