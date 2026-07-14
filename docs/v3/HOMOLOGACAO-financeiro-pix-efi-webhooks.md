# HOMOLOGACAO - Financeiro, Pix/Efi e webhooks

## Estado

A arquitetura Efi foi reimplementada na V3 sem reutilizar o saldo direto da producao e sem manter Mercado Pago ativo em paralelo. A integracao estrutural e os testes locais estao aprovados, mas local, teste e HML permanecem fail-closed com `EFI_ENABLED=false`.

Por decisao do usuario, credenciais, certificado, chave Pix, registro de webhook e homologacao real so serao instalados no ambiente correto durante a fase de importacao/preparacao do cutover. Pix permanece indisponivel no HML; nenhum pagamento, webhook, chamada externa ou credito por pagamento deve ocorrer nesta fase.

## Arquitetura auditada na producao

- OAuth2 `client_credentials` por mTLS com certificado PKCS12.
- Homologacao em `https://pix-h.api.efipay.com.br` e producao em `https://pix.api.efipay.com.br`.
- Cobranca imediata por `PUT /v2/cob/{txid}`.
- QR Code por `GET /v2/loc/{locId}/qrcode`.
- Consulta e conciliacao por `GET /v2/cob/{txid}`.
- Webhook Efi associado a txid, com repeticao possivel e necessidade de idempotencia.
- Estruturas `PagamentoMP`/`pagamentos_mp` ainda participam do fluxo Efi por legado nominal. A V3 nao copia o saldo mutavel, payload bruto ou logs sensiveis encontrados nesse fluxo.

## Matriz de legado nominal

| Nome legado | Arquivo ou tabela | Funcao atual auditada | Provedor efetivo | Dados existentes | Decisao |
| --- | --- | --- | --- | --- | --- |
| `PagamentoMP` | entidade da producao | Registro de pagamentos e cobrancas | Efi quando `provider=EFI`; MP apenas historico | Sim | Migrar comportamento para `pagamento` V3; preservar historico |
| `pagamentos_mp` | tabela/migrations historicas | Guarda ids antigos e campos Efi adicionados depois | Misto historico/Efi | Sim | Nao remover nem renomear migration historica |
| `PagamentoMPRepository` | repository da producao | Consulta pagamentos usados pelo checkout Efi | Efi no fluxo vigente | Sim | Nao copiar; usar `PagamentoRepository` V3 |
| `mp_payment_id`/`mpPaymentId` | coluna/campo historico | Identificador de operacoes antigas MP | Mercado Pago historico | Sim | Permanecer como historico; nao reutilizar para Efi |
| `provider_payment_id`, `txid`, `pix_*` | colunas na tabela nominal MP | Identificacao e apresentacao da cobranca Pix | Efi | Sim | Comportamento consolidado nos campos Efi de `pagamento` V3 |
| `CreditoUsuario`/`HistoricoCredito` | saldo/historico da producao | Credito direto apos pagamento | Independente do provedor | Sim | Nao copiar como fluxo ativo; V3 usa somente ledger V024 |
| `EfiPixService` | service da producao | OAuth mTLS, cobranca, QR e consulta | Efi | Nao se aplica | Reimplementado de forma sanitizada na V3 |
| `EfiWebhookController` | controller da producao | Recebe notificacoes Pix | Efi | Nao se aplica | Reimplementado com HMAC, filtro de origem no Nginx e conciliacao idempotente |

Nenhuma remocao ou renomeacao de tabela/migration historica foi feita.

## Variaveis do gate futuro

| Variavel | Origem | Regra |
| --- | --- | --- |
| `EFI_ENABLED` | ambiente | Fixo em `false` no HML durante o freeze |
| `EFI_ENVIRONMENT` | ambiente | Configurar somente no ambiente autorizado durante o gate |
| `EFI_BASE_URL` | ambiente | Endpoint estrito do ambiente autorizado, sem mistura com producao |
| `EFI_CLIENT_ID` | secret externo | Instalar somente durante o gate, fora do Git |
| `EFI_CLIENT_SECRET` | secret externo | Instalar somente durante o gate, fora do Git |
| `EFI_CERTIFICATE_PATH` | arquivo externo | Definir somente apos instalar o certificado no ambiente correto |
| `EFI_CERTIFICATE_PROTECTION_VALUE` | secret externo | Nunca versionar ou registrar em log |
| `EFI_PIX_KEY` | secret externo | Instalar somente durante o gate financeiro |
| `EFI_WEBHOOK_BASE_URL` | ambiente | Registrar somente durante o gate financeiro |
| `EFI_WEBHOOK_VERIFIER_VALUE` | secret externo | HMAC fora de logs, instalado somente durante o gate |
| `EFI_CHARGE_EXPIRATION_SECONDS` | ambiente | Entre 60 e 86400 segundos quando a integracao for autorizada |

Escopos minimos esperados na aplicacao Efi: `cob.write`, `cob.read`, `payloadlocation.read`, `webhook.write` e `webhook.read`.

## Contratos V3

- `POST /api/public/minha-conta/pagamentos/pix`: cria cobranca autenticada com `Idempotency-Key`.
- `GET /api/public/minha-conta/pagamentos/{pagamentoId}`: consulta somente pagamento do usuario da sessao.
- `POST /api/public/minha-conta/pagamentos/{pagamentoId}/conciliar`: consulta Efi e reconcilia o pagamento.
- `POST /api/public/webhooks/efi` e `/api/public/webhooks/efi/pix`: recebem notificacao Efi sem sessao, com HMAC obrigatorio.

O webhook permanece fora do CSRF apenas nesses dois caminhos. O Nginx do HML limita a origem Efi, desativa access log nessa location e o backend valida HMAC em tempo constante. O registro operacional deve usar a URL com HMAC e a configuracao Efi de webhook de homologacao; o segredo nao pode aparecer em evidencias.

## Ledger, idempotencia e conciliacao

- `movimento_credito` da V024 continua a unica fonte do saldo.
- Pagamento confirmado gera exatamente um movimento `PAGAMENTO` com chave `efi-pagamento:{pagamentoId}`.
- `pagamento.idempotency_key`, `pagamento.txid`, evento do provedor e webhook possuem unicidade.
- Webhook repetido e conciliacao repetida nao geram novo credito.
- O backend consulta a cobranca na Efi antes de creditar e compara txid e valor recebido.
- Divergencia, falha, cobranca ativa ou expiracao nao geram credito.
- Payload financeiro bruto, CPF, nome do pagador, token OAuth e binarios nao sao persistidos nem registrados em log.

## Gate obrigatorio antes do cutover

1. Na fase de importacao/preparacao do cutover, criar ou confirmar aplicacao e chave Pix do ambiente correto com os escopos minimos.
2. Instalar certificado e secrets diretamente no ambiente autorizado, sempre fora do Git.
3. Registrar o webhook exclusivo com HMAC e validar a origem conforme a documentacao Efi.
4. Executar OAuth, cobranca controlada, consulta, webhook repetido e conciliacao sem dado real quando aplicavel ao ambiente.
5. Confirmar um unico lancamento no ledger e ausencia de secrets/payloads em logs.
6. Registrar revisao humana/Pro e Go/No-Go financeiro antes de remover o freeze.

## Pendencias de cutover

- Credenciais, certificado, chave Pix e webhook de producao continuam proibidos nesta fase.
- Definir rotacao/revogacao, alertas, reconciliacao agendada, tratamento operacional de estorno/devolucao e runbook de indisponibilidade.
- Executar revisao humana/Pro e Go/No-Go financeiro antes de qualquer ativacao comercial.
