# Bloco 20 - templates e preview sanitizado de outbox

## Objetivo

Criar camada local de templates de comunicacao de moderacao para preview administrativo sanitizado.

Endpoint criado:

- `GET /api/admin/outbox/{id}/preview`

O endpoint e somente leitura, nao altera status do outbox, nao registra envio, nao marca `PROCESSADO`, nao cria auditoria de envio e nao chama API externa.

## Templates locais

Templates versionados em Java:

- `MODERACAO_SOLICITAR_AJUSTE`
- `MODERACAO_REPROVADA`
- `ANUNCIO_REMETIDO_REVISAO`
- `MODERACAO_MIDIA_REPROVADA`
- `GENERICO_OUTBOX_MODERACAO`

Todos usam placeholders neutros:

- `[anunciante]`
- `[anuncio]`
- `[motivo]`
- `[acao_necessaria]`
- `[suporte]`
- `[link_painel_futuro]`

Nenhum template contem dado real, URL real de producao, link real de login, link real de pagamento, telefone real, WhatsApp real, CPF, documento ou conteudo adulto real.

## Permissoes

- `ADMIN`: acessa preview de qualquer outbox.
- `MODERADOR`: acessa preview de outbox de moderacao.
- `COMERCIAL`: recebe `403`.
- `USUARIO`: recebe `403`.
- Sem sessao: `401`.
- Outbox inexistente: `404`.

O backend continua sendo a fonte de permissao.

## DTO

Resposta renderizada:

- `id`
- `tipoEvento`
- `status`
- `assuntoSanitizado`
- `corpoSanitizado`
- `canalPrevisto`
- `envioExternoExecutado=false`
- `somentePreview=true`
- `camposMascarados`
- `pendencias`

O DTO nao retorna payload bruto integral nem destino real.

## Garantias

- Sem envio real.
- Sem reenvio.
- Sem worker.
- Sem scheduler.
- Sem retry real.
- Sem SMTP.
- Sem WhatsApp API.
- Sem provedor externo.
- Sem fila externa.
- Sem alteracao de status.
- Sem `PROCESSADO`.
- Sem migration ou SQL de schema.

## E2E local

O smoke HTTP local valida:

- login ADMIN;
- geracao de outbox por moderacao local;
- consulta do outbox;
- consulta do preview;
- `envioExternoExecutado=false`;
- `somentePreview=true`;
- status preservado antes da simulacao;
- `403` para COMERCIAL;
- `403` para USUARIO;
- `401` sem sessao.

## Riscos residuais

Envio real continua pendente de fase futura e revisao Pro. Antes de homologacao/producao ainda serao necessarios provider aprovado, templates juridicamente revisados, idempotencia real, retry seguro, observabilidade, politica de opt-out quando aplicavel e auditoria final revisada.
