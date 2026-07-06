# HOMOLOGACAO - Monitoramento operacional

## Principio

Monitoramento de homologacao/cutover deve provar saude tecnica, rastreabilidade e ausencia de dado sensivel em logs. Este documento e contrato; nao configura servico externo.

## Health

- Endpoint de health vivo.
- Readiness separado quando houver dependencia de banco/storage.
- Liveness sem expor detalhe sensivel.
- Falha de dependencia deve ser visivel como status operacional, nao stack trace publico.

## Logs estruturados

- Request-id em todas as respostas e logs.
- Correlation-id quando houver fluxo multi-servico.
- Metodo, rota normalizada, status e duracao.
- Sem cookie, credencial, documento, WhatsApp, e-mail real, IP bruto ou user-agent bruto.

## Erros 4xx/5xx

- 4xx com mensagem amigavel e request-id.
- 5xx sem stack trace publico.
- Alerta minimo para 5xx acima do limite.
- Erro de seguranca 401/403 sanitizado.

## Auditoria admin

- Evento administrativo com ator, papel, acao, alvo tecnico e request-id.
- Motivo obrigatorio quando aplicavel.
- Dados sensiveis mascarados.
- Auditoria JSON final exige revisao Pro.

## Metricas de produto

- Anuncios publicados/pendentes/rejeitados.
- Cliques WhatsApp agregados.
- Visualizacoes agregadas.
- Funil `/anunciar`.
- Premium/beneficios ativos/expirados.
- Sem IP bruto, user-agent bruto ou contato real em relatorio versionado.

## Alertas minimos

- API indisponivel.
- Banco indisponivel.
- Falha de login elevada.
- Falha CSRF.
- 5xx elevado.
- Falha de webhook futuro.
- Falha de importacao/dry-run futuro.
- Queda de paginas publicas criticas.

## Retencao

- Politica de retencao de logs definida antes de dados reais.
- Expurgo ou anonimização de logs conforme decisao juridica.
- Relatorios versionados apenas com agregados e status.

## Bloqueio

Sem monitoramento, alertas minimos e auditoria sanitizada, o ambiente nao pode virar producao.
