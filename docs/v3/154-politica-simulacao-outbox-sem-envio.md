# Politica de simulacao de outbox sem envio

## Definicao

Simulacao local de outbox e uma acao administrativa controlada para validar fluxo, status e auditoria em ambiente local.

Ela nao e envio real.

A partir do Bloco 19.1, a simulacao so pode executar quando `APP_ENV=local`. Qualquer outro ambiente retorna `403` antes de alterar o outbox.

## Proibicoes

Durante a simulacao, continua proibido:

- enviar e-mail real;
- enviar WhatsApp real;
- enviar SMS;
- chamar webhook real;
- integrar SMTP externo;
- integrar API externa;
- criar worker real;
- criar scheduler real;
- criar retry real;
- usar fila externa;
- acessar producao;
- acessar banco de producao;
- usar dados reais;
- criar migration;
- alterar SQL de schema.

## Status

O Bloco 19 usa `PROCESSADO` apenas porque esse status ja existe no schema e representa processamento local concluido.

`PROCESSADO` nesta fase nao equivale a:

- e-mail enviado;
- WhatsApp enviado;
- SMS enviado;
- webhook entregue;
- contato notificado.

Fora de local, a simulacao nao pode marcar `PROCESSADO`.

## requestIdCliente

`requestIdCliente` permanece reservado.

Ele nao deduplica a simulacao, nao garante idempotencia real e nao deve ser vendido como contrato de reprocessamento seguro.

## Promocao futura

Antes de qualquer envio real, exigir:

- decisao Pro sobre provider;
- politica de retry;
- idempotencia real;
- template de comunicacao;
- mascaramento revisado;
- auditoria revisada;
- observabilidade;
- opt-out quando aplicavel;
- gate de homologacao;
- protecao contra envio duplicado.

## Preview local no Bloco 20

O preview por template local preserva esta politica: nao altera status, nao marca `PROCESSADO`, nao registra envio e nao chama provider externo.
