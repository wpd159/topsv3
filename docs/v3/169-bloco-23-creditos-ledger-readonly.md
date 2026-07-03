# Bloco 23 - creditos e ledger read-only local

## Objetivo

Implementar consulta local de creditos, ledger e consistencia de saldo usando somente o schema existente, dados sinteticos e endpoints administrativos `GET`.

Este bloco nao cria compra, checkout, pagamento real, credito real, ajuste real, estorno real, conciliacao real, Pix/Efi funcional, worker, scheduler, migration, SQL de schema, seed real, producao, VPS, API externa, remote, push ou commit apos o checkpoint autorizado.

## Checkpoint preliminar

Antes das alteracoes do Bloco 23, o checkpoint local do Bloco 22 foi criado:

- commit: `de71025`;
- mensagem: `feat: consolida base v3 local ate bloco 22`;
- remote: vazio;
- push: nao executado.

O inventario inicial usado para o pacote final foi gerado fora do repositorio em:

`C:\Users\WpD\AppData\Local\Temp\topsv3-inventario-inicial-2026-07-02-214253-964\INVENTARIO-INICIAL.csv`

## Backend

Endpoints criados:

- `GET /api/admin/creditos/usuarios/{id}/saldo`;
- `GET /api/admin/creditos/usuarios/{id}/movimentos`;
- `GET /api/admin/creditos/consistencia`;
- `GET /api/admin/creditos/inconsistencias`.

Todos sao `ADMIN` + `FINANCEIRO_LER`, com DTO sanitizado e somente leitura.

Services locais:

- `CreditoSaldoConsultaService`;
- `CreditoLedgerConsultaService`;
- `CreditoConsistenciaService`;
- `CreditoSanitizer`.

Repositorios adicionados ou estendidos apenas com metodos derivados Spring Data/JPA. Nao ha query nativa complexa, `@Modifying`, `save`, controller de escrita ou acao financeira.

## DTOs sanitizados

Os retornos administrativos nao expoem:

- txid;
- identificador de provedor;
- payload Pix/Efi;
- copia e cola;
- QR Code;
- valor pago;
- chave operacional bruta;
- CPF/documento;
- e-mail/telefone/WhatsApp real;
- storage key, bucket, hash ou URL privada.

Quando existe chave operacional no ledger, o DTO retorna apenas `chaveOperacionalPresente`.

## Consistencia local

Codigos documentados:

- `CREDITO_OK`;
- `SALDO_INCONSISTENTE`;
- `MOVIMENTO_SEM_ORIGEM`;
- `IDEMPOTENCY_KEY_DUPLICADA`;
- `CREDITO_SEM_PAGAMENTO`;
- `PAGAMENTO_APROVADO_SEM_CREDITO`;
- `REGRA_AJUSTE_CREDITO_PENDENTE`;
- `QUANTIDADE_INVALIDA`;
- `SALDO_NEGATIVO`;
- `PAGAMENTO_NAO_CONFIRMADO`.

Os dados sinteticos cobrem saldo divergente, credito sem pagamento, pagamento aprovado sem credito, ajuste pendente de regra Pro e pagamento ainda nao confirmado.

## Frontend/admin

`/admin/creditos` deixou de ser placeholder e passou a mostrar:

- saldo projetado;
- saldo calculado por movimentos;
- total de entradas/saidas;
- ledger sanitizado;
- inconsistencias locais;
- aviso de bloqueio de compra, ajuste, estorno, conciliacao, Pix/Efi, worker e credito real.

Nao ha botao de compra, ajuste, estorno, conciliacao, pagamento, Pix, upload ou acao real.

## Evidencias visuais

Como houve alteracao no painel admin, prints locais foram gerados com backend/frontend locais, PostgreSQL descartavel e dados sinteticos:

- `docs/v3/evidencias/bloco-23/desktop-admin-creditos.png`;
- `docs/v3/evidencias/bloco-23/mobile-admin-creditos.png`.

A validacao mobile da captura confirmou `scrollWidth = clientWidth`, `bodyOverflow` vazio e nenhum elemento `position: fixed` no DOM renderizado.

## Consulta a producao

Nao houve consulta SSH. Documentos locais, migrations e codigo do workspace foram suficientes.

## Fora do escopo

Continuam fora deste bloco:

- credito real;
- compra/checkout;
- pagamento real;
- Pix/Efi real;
- webhook real;
- conciliacao real;
- ajuste/estorno real;
- worker/scheduler;
- migration;
- SQL de schema;
- importador real;
- producao/VPS/banco de producao;
- API externa;
- remote/push.

## Riscos residuais

- Regras finais de conciliacao pagamento-creditos dependem de revisao Pro.
- Ajuste administrativo de creditos permanece pendencia Pro antes de homologacao/producao.
- Integracao Pix/Efi real e idempotencia de escrita continuam fora do escopo.
- Auditoria financeira detalhada para acao real ainda nao foi iniciada.

## Correcao posterior no Bloco 24

O Bloco 24 removeu a ambiguidade do campo exibido como `Saldo calculado`: o backend passou a dobrar os movimentos do ledger em ordem cronologica e comparar o resultado com o ultimo movimento e com `saldo_credito_usuario.saldo_atual`.

A tela pode manter o rotulo `Saldo calculado`, porque o valor agora e calculado de fato pelos movimentos locais. A divergencia sintetica do Bloco 23 continua proposital e deve permanecer como alerta local, sem executar ajuste financeiro.
