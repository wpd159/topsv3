# Checklist Bloco 24 - pagamentos read-only

## Escopo

- [x] Projeto permaneceu local.
- [x] Nenhuma consulta SSH foi necessaria.
- [x] Nenhuma migration foi criada.
- [x] SQL de schema nao foi alterado.
- [x] Nenhum dado real, dump real ou arquivo real foi usado.
- [x] Remote permaneceu vazio.
- [x] Push e commit nao foram executados.

## Correcoes preliminares

- [x] Empacotador corrigido para nao registrar validacoes obrigatorias como `nao informado` quando elas existem.
- [x] `Saldo calculado` passou a ser calculado pelos movimentos do ledger.
- [x] Scan amplo de consistencia documentado como local/sintetico.
- [x] IDs internos em DTOs admin documentados como pendencia Pro antes de homologacao/producao.

## Backend

- [x] Endpoints admin de pagamentos criados somente por `GET`.
- [x] RBAC conservador aplicado: ADMIN + FINANCEIRO_LER.
- [x] DTOs sanitizados criados.
- [x] Services de consulta/consistencia read-only criados.
- [x] Repositories usam leitura do schema existente.
- [x] Sem `@Modifying`, controller de escrita, save operacional ou acao financeira real.
- [x] OpenAPI atualizado.

## Dados sinteticos

- [x] Pagamento Efi sintetico aprovado com credito.
- [x] Pagamento aprovado sem credito.
- [x] Credito sem pagamento coberto por alerta.
- [x] Pagamento sem provedor suficiente usando `DESCONHECIDO`.
- [x] Pagamento Mercado Pago legado com evidencia explicita.
- [x] Pagamento com status inconsistente.
- [x] Webhook duplicado por hash sanitizado.

## Frontend

- [x] `/admin/financeiro` deixou de ser placeholder.
- [x] Painel read-only exibe pagamentos, detalhe sintetico e consistencia.
- [x] Sem botao de cobrar, pagar, Pix, webhook, conciliar, creditar, estornar ou ajustar.
- [x] Sem redesign.
- [x] Print desktop gerado em `docs/v3/evidencias/bloco-24/desktop-admin-pagamentos.png`.
- [x] Print mobile gerado em `docs/v3/evidencias/bloco-24/mobile-admin-pagamentos.png`.

## Validacoes esperadas

- [x] Backend compile/test.
- [x] Frontend lint/build.
- [x] E2E local descartavel.
- [x] Smoke HTTP com pagamentos.
- [x] Scanners de seguranca.
- [x] Validacao mobile estatica quando aplicavel.
- [x] Git diff checks.
- [x] ZIP final gerado na Area de Trabalho.

## Bloqueios mantidos

- [x] Sem producao.
- [x] Sem VPS.
- [x] Sem banco de producao.
- [x] Sem Efi real.
- [x] Sem Mercado Pago real.
- [x] Sem API externa.
- [x] Sem OpenAI.
- [x] Sem importador real.
- [x] Sem remote.
- [x] Sem push.
- [x] Sem commit.
