# Relatorio - riscos residuais de dados

- Bloco: 29.2
- Resultado: BLOQUEADO_PENDENTE_CLIENTE_POSTGRES_COMPATIVEL
- Producao alterada: nao
- Banco de producao usado como bancada: nao
- Backup/dump versionado: nao
- Conteudo sensivel versionado: nao

## Riscos residuais

- Docker daemon local indisponivel impediu baixar `postgres:17`.
- Cliente PostgreSQL 17.x ainda nao foi disponibilizado localmente.
- Restore local isolado ainda nao foi concluido.
- Sanitizacao real ainda nao foi executada.
- Validacao de ausencia de CPF/e-mail/telefone/IP/storage/Pix/Efi ainda nao pode aprovar sem banco sanitizado.
- SEO com dados sanitizados ainda nao gerou agregados reais.
- E2E local descartavel e smoke HTTP completo da API local permanecem pendentes porque dependem do ambiente Docker/backend local associado ao restore.

## Mitigacao

- Manter backup bruto fora do repositorio e fora do ZIP.
- Nao tentar outro download sem nova autorizacao.
- Reexecutar o bloco somente com Docker daemon local disponivel.
- Usar `--pull=never` em todo `docker run` posterior ao pull autorizado.
