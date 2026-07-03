# Politica de templates de comunicacao de moderacao

## Regra principal

Template local de outbox e apenas preview administrativo sanitizado.

Preview nao e envio, nao e reenvio, nao e confirmacao de entrega e nao autoriza provedor externo.

## Catalogo permitido

O catalogo local pode conter somente comunicacoes de moderacao:

- solicitacao de ajuste;
- reprovacao de revisao;
- anuncio remetido para revisao;
- midia rejeitada quando houver evento de outbox aplicavel;
- comunicacao pendente generica.

## Sanitizacao

Antes da renderizacao, o backend deve:

- mascarar e-mail;
- mascarar telefone e WhatsApp;
- mascarar CPF e documento;
- bloquear storage key, bucket, hash e etag;
- bloquear segredo;
- bloquear Pix, pagamento, valor e credito;
- remover HTML perigoso;
- remover URL real;
- limitar tamanho de assunto e corpo;
- usar placeholders neutros quando dado essencial nao existir.

O renderer nao deve confiar no payload do outbox. Apenas campos allowlist e previamente sanitizados podem influenciar a previa.

## Campos bloqueados

O preview nao pode expor:

- e-mail real;
- telefone real;
- WhatsApp real;
- CPF;
- documento privado;
- storage provider;
- bucket;
- chave de objeto;
- hash ou etag;
- segredo;
- payload financeiro;
- Pix copia e cola;
- QR Code Pix;
- JSON bruto integral.

## Proibicoes

Continua proibido:

- enviar e-mail real;
- enviar WhatsApp real;
- enviar SMS real;
- chamar webhook real;
- integrar SMTP externo;
- integrar Mailgun, SendGrid, SES, Zenvia, Twilio, Meta ou WhatsApp API;
- criar worker real;
- criar scheduler real;
- criar retry real;
- criar fila externa;
- marcar comunicacao como enviada por provedor real;
- usar dados reais;
- acessar producao, VPS ou banco de producao.

## Fase futura

Qualquer envio real exige fase futura com revisao Pro, provider definido, ambiente de homologacao isolado, auditoria de entrega, retry idempotente, politica de consentimento/opt-out quando aplicavel e validacao contra duplicidade.
