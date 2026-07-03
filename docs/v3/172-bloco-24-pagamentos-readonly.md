# Bloco 24 - pagamentos read-only local

## Objetivo

Adicionar leitura administrativa local de pagamentos, evidencias sanitizadas, conciliacao e inconsistencias financeiras, sem criar cobranca real, Pix/Efi real, webhook real, worker, scheduler ou mutation financeira.

Este bloco usa somente schema existente, dados sinteticos locais e endpoints `GET`.

## Endpoints

- `GET /api/admin/pagamentos`;
- `GET /api/admin/pagamentos/{id}`;
- `GET /api/admin/pagamentos/consistencia`;
- `GET /api/admin/pagamentos/inconsistencias`.

Todos exigem `ADMIN` com `FINANCEIRO_LER`.

`MODERADOR`, `COMERCIAL`, `USUARIO` e requisicao sem sessao nao acessam pagamentos.

## Backend

Foram criados services locais read-only:

- `PagamentoConsultaService`;
- `PagamentoConsistenciaService`;
- `PagamentoEvidenciaProvedorService`;
- `PagamentoSanitizer`.

DTOs administrativos nao expoem valor monetario, payload bruto, evidencia bruta de transacao, identificador bruto de provedor, chave operacional bruta, QR Code, copia e cola, documento, contato real ou storage.

`PagamentoWebhookEntity` e repositorios de leitura foram adicionados apenas para mapear a tabela existente e ler evidencias sanitizadas. Nenhuma migration ou alteracao de SQL de schema foi criada.

## Frontend

`/admin/financeiro` deixou de ser placeholder e passou a exibir painel read-only de pagamentos locais:

- total de pagamentos;
- total de inconsistencias;
- eventos/webhooks sanitizados;
- provedor classificado;
- lista de registros locais;
- alertas de consistencia.

Nao ha botao de cobrar, pagar, gerar Pix, processar webhook, conciliar, creditar, estornar, ajustar ou executar acao real.

Prints locais com dados sinteticos:

- `docs/v3/evidencias/bloco-24/desktop-admin-pagamentos.png`;
- `docs/v3/evidencias/bloco-24/mobile-admin-pagamentos.png`.

## Dados sinteticos

Foram adicionados cenarios locais:

- pagamento Efi sintetico aprovado com credito conciliado;
- pagamento aprovado sem credito;
- credito sem pagamento ja existente do Bloco 23 reaproveitado como alerta;
- pagamento sem provedor suficiente, usando `DESCONHECIDO` porque o schema exige provedor;
- pagamento Mercado Pago legado com evidencia explicita;
- pagamento Efi pendente;
- pagamento com status interno/provedor inconsistente;
- webhooks sanitizados duplicados por hash de payload.

Os dados ficam em `scripts/local/dados-sinteticos/`, nunca em migration.

## Consulta a producao

Nao houve consulta SSH. O comportamento necessario foi confirmado por documentos locais, migrations e codigo do workspace.

## Correcoes preliminares

- `RESUMO-ENTREGA.md` do pacote passou a preencher validacoes obrigatorias do empacotador quando nao houver JSON de metadados.
- `Saldo calculado` do Bloco 23 passou a ser calculado pelo fold real dos movimentos locais.
- O scan amplo de consistencia foi documentado como local/sintetico, com paginacao/escopo pendente para fase Pro.
- IDs internos em DTOs administrativos de creditos foram documentados como aceitaveis localmente e pendentes de decisao Pro antes de homologacao/producao.

## Fora do escopo

Continuam proibidos:

- cobranca real;
- checkout;
- QR Code ou copia e cola Pix;
- Pix/Efi real;
- Mercado Pago real;
- webhook real;
- conciliacao real;
- credito real;
- estorno;
- worker/scheduler financeiro;
- mutation financeira;
- migration ou SQL de schema;
- dados reais, dump real ou arquivo real;
- producao, VPS ou banco de producao;
- API externa;
- remote, push ou commit.

## Riscos residuais

- Regras finais de conciliacao financeira dependem de revisao Pro.
- Retencao e auditoria de eventos financeiros reais ainda exigem decisao antes de homologacao/producao.
- Paginacao/escopo dos scans amplos precisa ser definido antes de volume real.
- Exposicao de IDs internos em admin local exige decisao Pro antes de homologacao/producao.
