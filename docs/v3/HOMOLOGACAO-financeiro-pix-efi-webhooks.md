# HOMOLOGACAO - Financeiro, Pix/Efi e webhooks

## Principio

Nenhum Pix real, pagamento real, credito real por pagamento ou webhook real existe neste bloco. Este documento define contrato futuro de homologacao.

## Variaveis obrigatorias sem valores reais

| Variavel | Escopo | Observacao |
| --- | --- | --- |
| `EFI_ENV` | homologacao | Ambiente do provedor, sem valor real neste documento. |
| `EFI_CLIENT_ID` | secret externo | Fora do Git. |
| `EFI_CLIENT_SECRET` | secret externo | Fora do Git. |
| `EFI_CERTIFICATE_PATH` | secret externo | Certificado fora do Git. |
| `EFI_WEBHOOK_SECRET` | secret externo | Assinatura/verificacao fora do Git. |
| `EFI_PIX_KEY` | secret externo | Chave de homologacao fora do Git. |
| `PAYMENT_WEBHOOK_BASE_URL` | ambiente | Dominio de homologacao aprovado. |

## Certificados e segredos

- Certificados ficam fora do repositorio.
- Nenhum certificado entra em ZIP de revisao.
- Logs nao podem imprimir caminho sensivel completo, payload sensivel ou credencial.
- Rotacao de credenciais deve ser documentada antes de producao.

## Webhook em homologacao

- Deve usar endpoint de homologacao.
- Deve validar assinatura/origem.
- Deve ser idempotente.
- Deve registrar request-id/correlation-id.
- Deve rejeitar replay quando aplicavel.
- Deve gerar auditoria sanitizada.
- Deve ter fila/reprocessamento ou decisao formal documentada.

## Idempotencia e conciliacao

- Cada evento financeiro deve ter chave de idempotencia.
- Evento duplicado nao pode duplicar credito.
- Conciliacao deve comparar pagamento, ledger e credito.
- Divergencia bloqueia cutover financeiro.
- Estorno/cancelamento exige contrato proprio antes de producao.

## Creditos e ledger

- Creditos continuam inteiros.
- Dinheiro usa tipo numerico/BigDecimal.
- Ledger deve ser append-only ou possuir auditoria equivalente.
- Ajuste manual exige motivo, permissao e auditoria.

## Rollback financeiro

- Rollback de app nao pode apagar ledger.
- Reversao financeira exige evento compensatorio auditado.
- Falha de conciliacao deve bloquear Go.
- Webhook real nao pode ser habilitado sem criterio de rollback.

## Logs

- Sem payload sensivel em log versionado.
- Sem documento, e-mail real, WhatsApp real, certificado, credencial ou chave.
- Relatorios devem usar agregados, status e ids tecnicos sanitizados.
