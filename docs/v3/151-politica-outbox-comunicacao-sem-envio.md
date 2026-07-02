# Politica de outbox sem envio externo

## Regra principal

`outbox_evento` e apenas registro local de intencao futura de comunicacao.

Enquanto nao houver fase especifica aprovada, outbox pendente nao significa comunicacao enviada.

## Proibido nesta fase

- Enviar e-mail real.
- Enviar WhatsApp real.
- Enviar notificacao externa.
- Integrar SMTP externo.
- Integrar provedor de comunicacao.
- Criar worker.
- Criar scheduler.
- Marcar evento como enviado.
- Reenviar evento.
- Expor destinatario real.
- Expor payload bruto.

## Consulta administrativa

A consulta admin read-only pode exibir:

- estado do evento;
- tipo de evento;
- entidade tecnica associada;
- data de criacao;
- tentativas registradas;
- previa logica sanitizada;
- dados allowlist sanitizados.

A consulta deve sempre indicar `envioExternoExecutado=false`.

## Sanitizacao obrigatoria

A API deve mascarar ou bloquear:

- e-mail;
- telefone;
- WhatsApp;
- CPF/documento;
- token;
- senha;
- cookie;
- storage key;
- bucket;
- hash;
- payload financeiro;
- Pix;
- certificado.

## Fase futura

Qualquer envio real exige fase separada com:

- revisao Pro;
- politica de idempotencia real;
- template aprovado;
- opt-in/consentimento quando aplicavel;
- canal local de teste, como Mailpit, antes de provedor real;
- auditoria de envio;
- tratamento de erro e retry;
- segregacao entre homologacao e producao.

## Estado atual

O Bloco 18 criou somente leitura local. Nao ha entrega real, nao ha processamento de fila e nao ha alteracao de status do outbox.
