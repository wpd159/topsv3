# Relatorio - riscos residuais de dados

## Riscos residuais

- Restore local isolado pendente por falta de cliente PostgreSQL compativel com custom format 1.16.
- Sanitizacao real pendente.
- Validacao de contagens reais sanitizadas pendente.
- Validacao SEO com dados sanitizados pendente.
- Classificacao das 45 URLs desconhecidas do Bloco 28 pendente de cutover.

## Controles aplicados

- Backup bruto copiado apenas para pasta externa.
- Backup bruto nao versionado.
- Backup bruto nao incluido no ZIP.
- Conteudo do backup nao foi impresso.
- Producao nao foi alterada.
- Banco de producao nao foi usado como teste.
- Nenhum SQL foi executado em banco de producao.
- Nenhuma midia real foi baixada.
