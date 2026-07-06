# HOMOLOGACAO - Rollback e monitoramento

## Backup e rollback

Antes de staging real com dados autorizados:

- Definir responsavel pelo rollback.
- Definir ponto de restauracao.
- Testar restauracao em ambiente isolado.
- Validar que migrations podem ser reexecutadas de forma auditavel.
- Registrar plano de reversao de frontend/backend/configuracao.
- Proibir cutover sem janela, responsavel e criterio de retorno.

## Monitoramento

Contrato minimo para homologacao:

- Request-id em resposta e logs.
- Correlation-id propagado entre frontend/backend quando aplicavel.
- Logs sem cookie, credencial, documento, WhatsApp, e-mail real, IP bruto ou user-agent bruto.
- Auditoria admin em JSON sanitizado.
- Alertas para erro 5xx, falha de login, falha CSRF, falha de outbox e falha de job futuro.
- Retencao de logs definida antes de dados reais/sanitizados.

## Evidencias esperadas

- Relatorio de Flyway real no ambiente.
- Relatorio de gitleaks real.
- Relatorio de CORS/cookies/CSRF.
- Relatorio de backup/rollback.
- Relatorio de logs/auditoria sem dado sensivel.

## Bloqueios

- Sem backup/rollback testado, nao ha cutover.
- Sem monitoramento e auditoria revisados, nao ha producao.
- Sem Pro, nao ha uso de dados reais/sanitizados, Pix/Efi real, webhook, importador real ou financeiro real.
