# DOSSIE - Proximos passos para homologacao

## Principio

Os proximos passos devem seguir ordem segura. Nao pular para producao, staging real, dados reais/sanitizados, Pix/Efi real, webhook real, API externa real ou importador real sem bloco proprio e autorizacao expressa.

## Ordem objetiva recomendada

1. Revisao Pro/humana do dossie final.
2. Decisao sobre Bloco 29: novo backup consistente ou correcao da origem/backup.
3. Restore completo local isolado e sanitizacao aprovada.
4. Repetir Flyway real e gitleaks real no ambiente controlado.
5. Aprovar ambiente de homologacao com secrets fora do Git.
6. Validar CORS, cookies, CSRF e RBAC nao-local.
7. Validar storage/upload/CDN real apenas com arquivos sinteticos autorizados.
8. Executar dry-run de importacao com fonte autorizada e relatorio agregado.
9. Validar SEO real: mapa final, 301, canonical, sitemap, robots e Search Console.
10. Validar financeiro/Pix/Efi/webhooks em homologacao, sem payload sensivel.
11. Testar backup/rollback de app, banco e SEO.
12. Ativar monitoramento e auditoria operacional.
13. Aplicar matriz Go/No-Go.
14. Decisao humana final antes de cutover.

## Pendencias antes de homologacao real

- Fonte real ou base sanitizada autorizada.
- Banco de homologacao isolado.
- Secrets externos.
- Dominio de homologacao.
- Storage/CDN real, se no escopo.
- Politica de logs/retencao.
- Responsaveis e janelas operacionais.

## Nao aceitavel para producao

- Dados reais no repositorio.
- Secrets versionados.
- Sem rollback testado.
- Sem monitoramento.
- Sem revisao Pro/humana.
- Sem resolucao de SEO critico.
- Sem idempotencia financeira.
- Sem decisao de retencao/privacidade quando houver documento real.
