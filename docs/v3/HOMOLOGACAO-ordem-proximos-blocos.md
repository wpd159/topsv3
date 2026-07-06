# HOMOLOGACAO - Ordem recomendada dos proximos blocos

## Principio

Nao avancar para homologacao, cutover ou producao sem fechar os gates bloqueantes. O MVP local sintetico e base de continuidade, nao substitui validacao com ambiente controlado e dados autorizados.

## Ordem segura recomendada

1. Fechar Bloco 29 com decisao segura de restore completo: novo backup consistente ou correcao da origem/backup, sempre fora do repositorio.
2. Executar restore completo local isolado e sanitizacao aprovada, sem promover quarentena sem `POST_DATA` a staging final.
3. Repetir Flyway real e gitleaks real no ambiente controlado de homologacao, mesmo que ambos ja estejam OK localmente.
4. Definir staging/homologacao com segredos fora do Git, rede controlada, profiles nao-locais e rollback.
5. Fechar hardening de auth/RBAC/CSRF/admin para ambiente nao local.
6. Definir CDN/storage e upload real com separacao de midia publica, privada e documentos.
7. Executar importador real apenas com fonte autorizada, dry-run, relatorios agregados e rollback.
8. Validar financeiro, Premium, creditos, Pix/Efi e webhooks em homologacao, sem payload sensivel em logs.
9. Validar SEO real: mapa completo, 301, canonical, sitemap, robots, Search Console e pagina vazia.
10. Fechar auditoria JSON, monitoramento, backup, rollback e runbook operacional.
11. Fazer revisao Pro/humana final antes de qualquer cutover.

## Bloqueios

- Nao usar producao como bancada de teste.
- Nao usar banco de quarentena como staging final.
- Nao versionar dump, backup, SQL bruto, log bruto, midia real, documento real, segredo ou dado sensivel.
- Nao executar Pix/Efi real, pagamento real, webhook real, upload real ou API externa sem fase propria e autorizacao expressa.
