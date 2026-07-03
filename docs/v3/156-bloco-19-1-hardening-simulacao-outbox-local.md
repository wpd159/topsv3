# Bloco 19.1 - hardening da simulacao local de outbox

## Objetivo

Endurecer o endpoint `POST /api/admin/outbox/{id}/simular-processamento-local` para uso exclusivamente local.

## Decisao

O backend bloqueia a simulacao quando `app.env` nao for exatamente `local`.

Erro escolhido fora de local:

- HTTP `403 Forbidden`
- mensagem tecnica: `simulacao de outbox disponivel somente em APP_ENV=local`

Essa escolha preserva a natureza admin do endpoint e deixa claro que a acao existe, mas e proibida fora do ambiente local.

## Regra fail-closed

A simulacao falha quando `app.env` estiver:

- ausente;
- vazio;
- `nao_configurado`;
- `staging`;
- qualquer valor diferente de `local`.

Fora de local, o service retorna erro antes de carregar, salvar ou auditar o outbox.

## Garantias

- Fora de `APP_ENV=local`, status nao e alterado.
- Fora de `APP_ENV=local`, outbox nao e marcado como `PROCESSADO`.
- Fora de `APP_ENV=local`, auditoria de simulacao nao e gravada.
- Em `APP_ENV=local`, `ADMIN` continua podendo simular outbox `PENDENTE`.
- `MODERADOR` continua recebendo `403` mesmo em local.
- Sem sessao continua recebendo `401` via camada de seguranca.

## Proibicoes preservadas

Nao foi criado:

- envio real;
- reenvio real;
- worker;
- scheduler;
- retry real;
- API externa;
- SMTP externo;
- WhatsApp real;
- Pix/Efi;
- pagamento ou credito;
- importador real;
- migration;
- SQL de schema.

## Riscos residuais

`PROCESSADO` por simulacao local continua sendo marcador tecnico de teste local. Antes de qualquer envio real, ainda sao obrigatorias fase futura, revisao Pro, idempotencia real, provider definido, retry seguro, templates, observabilidade e politica de auditoria revisada.

## Relacao com Bloco 20

O preview sanitizado do Bloco 20 e independente da simulacao. Ele usa `GET /api/admin/outbox/{id}/preview`, retorna `somentePreview=true`, nao depende de `APP_ENV=local` para executar envio porque nao ha envio, e nao altera status do outbox.
