# Checklist Bloco 23 - creditos e ledger read-only

## Escopo

- [x] Projeto permaneceu local.
- [x] Checkpoint local do Bloco 22 foi criado antes das novas alteracoes.
- [x] Remote permaneceu vazio.
- [x] Push nao foi executado.
- [x] Nenhuma consulta SSH foi necessaria.
- [x] Nenhuma migration foi criada.
- [x] SQL de schema nao foi alterado.
- [x] Nenhum dado real, dump real ou arquivo real foi usado.

## Backend

- [x] Endpoints admin de creditos criados somente por `GET`.
- [x] RBAC conservador aplicado: ADMIN + FINANCEIRO_LER.
- [x] DTOs sanitizados criados.
- [x] Services de consulta/read-only criados.
- [x] Repositories usam metodos derivados simples.
- [x] Sem `@Modifying`, `save`, controller de escrita ou acao financeira real.
- [x] OpenAPI atualizado.

## Dados sinteticos

- [x] Ledger sintetico local criado.
- [x] Saldo sintetico local criado.
- [x] Pagamento sintetico aprovado com credito conciliado criado.
- [x] Pagamento aprovado sem credito criado para alerta.
- [x] Movimento de credito sem pagamento criado para alerta.
- [x] Pagamento nao confirmado referenciado criado para alerta.
- [x] Ajuste sintetico marcado como pendencia Pro.

## Frontend

- [x] `/admin/creditos` deixou de ser placeholder.
- [x] Painel read-only exibe saldo, ledger e consistencia.
- [x] Sem botao de comprar, pagar, ajustar, estornar, conciliar ou Pix.
- [x] Sem redesign.
- [x] Sem scroll lock ou elemento flutuante novo.

## Validacoes esperadas

- [x] Backend compile/test.
- [x] Frontend lint/build.
- [x] E2E local descartavel.
- [x] Smoke HTTP com creditos.
- [x] Scanners de seguranca.
- [x] Validacao mobile estatica quando aplicavel.
- [x] Git diff checks.
- [x] ZIP final gerado na Area de Trabalho.

## Bloqueios mantidos

- [x] Sem producao.
- [x] Sem VPS.
- [x] Sem banco de producao.
- [x] Sem Efi real.
- [x] Sem OpenAI/API externa.
- [x] Sem importador real.
- [x] Sem remote.
- [x] Sem push.
- [x] Sem commit apos o checkpoint preliminar.

## Ajustes confirmados no Bloco 24

- [x] `Saldo calculado` passou a ser calculado pelo fold dos movimentos, nao apenas espelhado do ultimo movimento.
- [x] Scan amplo de `/consistencia` documentado como local/sintetico, com paginacao/escopo pendentes para Pro.
- [x] IDs internos em DTOs admin de creditos documentados como aceitaveis localmente e pendentes de decisao Pro antes de homologacao/producao.
