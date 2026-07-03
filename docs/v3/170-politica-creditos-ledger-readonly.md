# Politica creditos e ledger read-only

## Principios

Credito na V3 deve ser tratado como razao auditavel. A leitura local pode calcular e reportar consistencia, mas nao pode alterar saldo nem simular credito real.

O backend e a fonte da decisao. Frontend apenas renderiza dados sanitizados recebidos da API.

## Acesso

Endpoints de creditos/ledger sao conservadores:

- `ADMIN` com `FINANCEIRO_LER`: permitido;
- `MODERADOR`: negado;
- `COMERCIAL`: negado;
- `USUARIO`: negado;
- sem sessao: negado.

## Regras de sanitizacao

API admin de creditos nao deve retornar:

- txid;
- e2eid;
- copia e cola;
- QR Code;
- payload Pix/Efi;
- identificador de provedor;
- chave operacional bruta;
- valor monetario;
- CPF/documento;
- telefone/WhatsApp/e-mail real;
- storage key ou hash.

Campos permitidos no ledger:

- ids internos sinteticos/local;
- tipo/direcao;
- quantidade de creditos;
- saldo antes/depois;
- origem enum;
- referencia tipo/id;
- flag booleana `chaveOperacionalPresente`;
- data local de criacao.

IDs internos em DTOs administrativos de creditos sao aceitaveis apenas no ambiente local sintetico. Antes de homologacao/producao, revisao Pro deve decidir se esses IDs permanecem, se viram identificadores operacionais opacos ou se exigem outra minimizacao.

## Consistencia

O relatorio local pode apontar:

- saldo projetado diferente do ultimo movimento;
- movimento sem origem;
- chave operacional duplicada;
- credito de origem pagamento sem pagamento vinculado;
- pagamento aprovado sem credito no ledger;
- ajuste administrativo pendente de regra Pro;
- quantidade invalida;
- saldo negativo;
- pagamento referenciado ainda nao aprovado.

`CREDITO_OK` e reservado para estado sem alerta em consultas pontuais.

O scan amplo de `/consistencia` usa dados locais sinteticos e colecoes pequenas. Em fase futura com volume real, ele deve receber paginacao, janela temporal, escopo por usuario/anuncio ou mecanismo assincrono aprovado. Esta regra documental nao autoriza worker, scheduler ou mutation financeira.

## Proibicoes

Antes de fase futura expressa e revisao Pro, continua proibido:

- criar credito real;
- debitar credito real;
- estornar;
- ajustar saldo;
- conciliar pagamento real;
- chamar provider Pix/Efi;
- gerar QR Code ou copia e cola;
- processar webhook real;
- criar worker/scheduler financeiro;
- expor payload financeiro sensivel;
- criar migration ou alterar SQL de schema.

## Pendencias Pro

- Definir escrita segura de ledger com idempotencia real.
- Definir regra juridica/comercial de ajuste e estorno.
- Definir conciliacao Pix/Efi real.
- Definir trilha de auditoria financeira completa.
- Definir relatorio financeiro operacional para homologacao/producao.
