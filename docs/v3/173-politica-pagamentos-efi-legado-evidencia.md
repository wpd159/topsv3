# Politica de pagamentos, Efi legado e evidencia

## Principios

Pagamentos na V3 devem ser tratados como trilha financeira auditavel e conservadora. Leitura local pode exibir estado sanitizado e inconsistencias, mas nao pode iniciar cobranca, chamar provedor, processar webhook real, alterar saldo ou executar conciliacao real.

Backend e a fonte da classificacao. Frontend apenas renderiza dados recebidos.

## Classificacao de provedor

A classificacao deve usar evidencia explicita:

- `EFI`: somente quando `pagamento.provedor = EFI` ou evento/webhook sanitizado indicar Efi;
- `MERCADO_PAGO_LEGADO`: somente quando `pagamento.provedor = MERCADO_PAGO_LEGADO` ou evento/webhook sanitizado indicar Mercado Pago legado;
- `OUTRO_LEGADO`: somente quando houver evidencia explicita;
- `DESCONHECIDO`: quando a evidencia for insuficiente.

Nao presumir Mercado Pago pelo nome de tabela legada, por origem historica ou por ausencia de informacao.

## Dados proibidos nas respostas

Respostas admin read-only de pagamento nao devem retornar:

- valor monetario;
- evidencia bruta de transacao;
- identificador bruto de provedor;
- chave operacional bruta;
- payload Pix/Efi;
- QR Code;
- copia e cola;
- link de pagamento;
- documento, contato real ou e-mail real;
- storage key, bucket ou URL privada.

Campos permitidos:

- IDs internos sinteticos/local;
- provedor declarado e provedor classificado;
- metodo;
- status interno e status operacional sanitizado;
- quantidade de creditos;
- moeda;
- flags booleanas de evidencia e credito vinculado;
- evidencia de transacao mascarada;
- contagens de eventos/webhooks sanitizados;
- codigos de consistencia.

IDs internos em DTOs admin sao aceitaveis apenas no ambiente local sintetico. Revisao Pro deve decidir minimizacao antes de homologacao/producao.

## Codigos de consistencia

Codigos documentados:

- `PAGAMENTO_OK`;
- `PAGAMENTO_SEM_PROVEDOR`;
- `PAGAMENTO_SEM_TXID`;
- `PAGAMENTO_DUPLICADO`;
- `PAGAMENTO_APROVADO_SEM_CREDITO`;
- `CREDITO_SEM_PAGAMENTO`;
- `STATUS_PAGAMENTO_INCONSISTENTE`;
- `EVENTO_WEBHOOK_DUPLICADO`;
- `PAGAMENTO_EFI_NAO_CONFIRMADO`;
- `PAGAMENTO_MERCADO_PAGO_LEGADO`;
- `PAGAMENTO_PROVEDOR_DESCONHECIDO`;
- `PAGAMENTO_PAYLOAD_SENSIVEL_OCULTO`.

O codigo `PAGAMENTO_SEM_TXID` e um codigo operacional; ele nao autoriza expor o valor bruto do campo tecnico.

## Scans locais

`/api/admin/pagamentos/consistencia` e `/api/admin/pagamentos/inconsistencias` usam scan amplo apenas em ambiente local/sintetico. Antes de volume real, a V3 deve definir paginacao, janela temporal, escopo operacional ou processamento controlado aprovado.

## Proibicoes

Antes de fase futura expressa e revisao Pro, permanece proibido:

- criar cobranca real;
- gerar QR Code ou copia e cola;
- consultar Efi real;
- consultar Mercado Pago real;
- processar webhook real;
- conciliar pagamento real;
- creditar saldo real;
- estornar;
- criar worker/scheduler financeiro;
- alterar schema financeiro;
- usar dado real ou dump real.
