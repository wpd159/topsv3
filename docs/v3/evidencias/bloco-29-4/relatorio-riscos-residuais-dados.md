# Relatorio - riscos residuais de dados

- Bloco: 29.4
- Resultado: BLOQUEADO_FALHA_PG_RESTORE_RAW_CONSTRAINT_FK
- Producao alterada: nao
- Banco de producao usado como bancada: nao
- Backup/dump versionado: nao
- Conteudo sensivel versionado: nao
- Log bruto versionado: nao
- Recursos TopsWI/terceiros alterados: nao

## Riscos residuais

- O restore bruto falhou novamente, agora com volume limpo e `--single-transaction`.
- O diagnostico sanitizado classifica a falha como `CONSTRAINT/FK` em fase `POST_DATA`.
- O raw log existe somente fora do repositorio em area de auditoria local nao versionada.
- Bancos bruto e sanitizado permaneceram com 0 tabelas apos a falha, indicando que `--single-transaction` evitou restauracao parcial.
- A sanitizacao real nao foi executada.
- A validacao de ausencia de CPF/e-mail/telefone/IP/storage/Pix/Efi ainda nao pode aprovar sem banco sanitizado.
- SEO com dados sanitizados ainda nao gerou agregados reais.

## Mitigacao

- Nao conectar a aplicacao V3 ao container bruto.
- Nao executar flags adicionais por suposicao.
- Manter backup bruto e raw log fora do repositorio e fora do ZIP.
- Preservar containers, volumes, networks e compose de TopsWI/terceiros.
- Proxima acao exige revisao humana/Pro do raw log externo e decisao explicita.
