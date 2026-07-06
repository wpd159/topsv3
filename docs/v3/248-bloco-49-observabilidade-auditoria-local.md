# Bloco 49 - Observabilidade e auditoria local

## Objetivo

Fazer checkpoint local do Bloco 48 e auditar logs, request-id, erros, auditoria JSON e observabilidade local com dados sinteticos.

## Checkpoint consolidado

- Checkpoint local do Bloco 48: `9bd38f3`.
- Mensagem do commit: `test: valida auth rbac csrf local ate bloco 48`.
- Remote: vazio.
- Push: nao executado.

## Escopo validado

- `X-Request-Id` propagado em respostas 200, 400, 401, 403 e 404.
- Request-id invalido substituido por identificador seguro.
- Logs locais de request contem request-id, metodo, status e duracao.
- Logs locais filtrados por request-id nao expoem cabecalho bruto, cookie, credencial, documento, contato, IP bruto, user-agent bruto ou stack trace.
- Erros 400 e 404 possuem corpo padronizado com requestId.
- Erros 401 e 403 possuem header request-id e writer de seguranca padronizado.
- Erro 500 foi validado por codigo para resposta generica e log sem stack trace publico.
- Auditoria local usa requestId, sanitizers e JSON sem dados reais.
- Eventos de moderacao continuam rastreaveis por acao, recurso e requestId.

## Correcoes pequenas feitas

- `RequestIdFilter` recebeu ordem de precedencia maxima para rodar antes da cadeia de seguranca e cobrir 401/403.
- `AdminSecurityErrorWriter` passou a definir UTF-8 explicitamente, escrever o JSON pelo writer e fazer flush do buffer.

## Resultado

- Validador criado: `scripts/local/validar-observabilidade-auditoria-local.ps1`.
- Resultado: `OK_OBSERVABILIDADE_AUDITORIA_LOCAL`.
- Ambiente: PostgreSQL descartavel com migrations V001 a V017 e fixture sintetica.
- Recursos descartaveis removidos ao final.

## Pendencias de producao

- Definir formato final de logs estruturados JSON.
- Definir hashing real de IP e user-agent, com politica de retencao.
- Validar pipeline centralizado de logs, alertas, mascaramento e acesso operacional.
- Revisar auditoria JSON com Pro antes de homologacao/producao.

## Limites preservados

- Sem producao, VPS, restore, staging ou dados reais.
- Sem Pix/Efi real, webhook, pagamento real ou API externa.
- Sem nova arquitetura de observabilidade.
- Sem migration nova.
- Sem push.
- Sem fase posterior iniciada.
