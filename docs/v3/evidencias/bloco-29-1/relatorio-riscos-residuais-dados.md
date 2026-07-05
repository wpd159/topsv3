# Relatorio - riscos residuais de dados

- Bloco: 29.1
- Resultado: BLOQUEADO_PENDENTE_CLIENTE_POSTGRES_COMPATIVEL
- Producao alterada: nao
- Banco de producao usado como bancada: nao
- Backup/dump versionado: nao
- Conteudo sensivel versionado: nao

## Riscos residuais

- Restore local isolado ainda nao foi concluido por ausencia de cliente PostgreSQL 17.x compativel local.
- Sanitizacao real ainda nao foi executada porque depende do restore.
- Validacao de ausencia de CPF/e-mail/telefone/IP/storage/Pix/Efi ainda nao pode aprovar sem banco sanitizado.
- SEO com dados sanitizados ainda nao gerou agregados reais.
- As 45 URLs desconhecidas do Bloco 28 continuam pendentes se exigirem lista bruta para classificacao.

## Mitigacao

- Manter backup bruto fora do repositorio e fora do ZIP.
- Executar `docker pull postgres:17` somente com autorizacao consciente do usuario.
- Reexecutar os scripts do Bloco 29.1 depois que o cliente compativel existir localmente.
- Nao conectar a V3 ao banco bruto.
- Versionar apenas contagens, agregados e placeholders.
