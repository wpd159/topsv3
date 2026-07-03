# Premium, créditos e ranking

## Objetivo

Definir benefícios premium, durações, créditos e ranking de forma auditável, sem prioridade escondida e sem saldo financeiro ambíguo.

## Benefícios premium

Benefícios iniciais recomendados:

- `ANUNCIO_TOPO`
- `POSICAO_GARANTIDA_TOP20`
- `CARROSSEL_FOTOS`
- `FOTOS_EXTRA_5`
- `VIDEO_1`
- `STORIES`
- `WHATSAPP_CARD`
- `OCULTAR_IDADE`

Cada benefício deve ter:

- código estável;
- nome em português;
- descrição;
- escopo;
- efeito visível;
- durações permitidas;
- custo em créditos;
- regra de compatibilidade;
- se afeta ranking ou não;
- versão da regra.

## Duração por benefício

A duração não deve ficar escondida em código.

Modelo:

- benefício;
- opção de duração em dias;
- custo em créditos;
- preço de referência, se houver;
- data de vigência;
- ativo/inativo;
- versão da regra.

Mudanças futuras de preço ou duração não podem alterar ativações já realizadas. A ativação deve guardar snapshot.

## Origem da ativação

Toda ativação deve registrar origem:

- `COMPRA`
- `CREDITO`
- `CORTESIA`
- `CAMPANHA`
- `ADMIN`
- `IMPORTACAO`

Campos obrigatórios:

- benefício;
- opção/duração;
- usuário;
- anúncio;
- ator;
- origem;
- início;
- fim;
- custo snapshot;
- idempotency key;
- motivo;
- status;
- revogação, se houver.

## Auditoria

Devem ser auditados:

- ativação;
- renovação;
- revogação;
- alteração de catálogo;
- ajuste manual;
- uso de cortesia;
- importação;
- alteração que afete ranking.

Auditoria deve registrar ator, IP, user-agent, recurso, resultado e metadados sem segredo.

## Razão de créditos

A razão de créditos é a fonte de verdade.

### Movimento de crédito

Cada movimento deve registrar:

- usuário;
- tipo: entrada, saída, ajuste ou estorno;
- quantidade;
- saldo antes;
- saldo depois;
- origem;
- referência;
- idempotency key;
- ator;
- observação;
- data.

Regras:

- Saldo é projeção.
- Toda saída deve apontar para benefício, pagamento, ajuste ou estorno.
- Toda entrada por pagamento deve apontar para pagamento aprovado.
- Idempotência é obrigatória.
- Concorrência deve ser controlada por lock ou versão.
- Ajuste manual exige justificativa.

## Pagamentos e créditos

Regras:

- Efí Bank/Efí Pay é a integração Pix ativa inicial para compra de créditos.
- Mercado Pago permanece apenas como legado descartado/importável, sem novo fluxo ativo.
- Nome de tabela legada relacionada a Mercado Pago não define provedor: o importador futuro deve classificar por evidência real.
- Registros Efí localizados em tabela legada com nome Mercado Pago devem continuar Efí quando houver evidência suficiente.
- Pagamento aprovado gera uma única entrada de crédito.
- Webhook deve ser autenticado e idempotente.
- Confirmação por webhook pode exigir consulta ativa ao provedor antes de conceder créditos.
- Reprocessamento não duplica crédito.
- Pagamento cancelado/estornado gera movimento de estorno quando aplicável.
- CPF/dados financeiros devem ser minimizados, protegidos e com retenção definida.
- QR Code e Pix cópia e cola não devem ser registrados em logs.
- Saldo projetado não é prova de pagamento.

## Ranking

Ranking deve ser explicável internamente. Uma posição exibida deve poder ser explicada por:

- filtros aplicados;
- localidade;
- status do anúncio;
- relevância textual;
- recência;
- benefício premium ativo;
- seed de aleatoriedade da janela;
- regras de desempate.

Não deve existir prioridade escondida fora de benefícios explicitamente marcados como capazes de afetar ranking.

## Aleatoriedade auditável

A aleatoriedade deve ser controlada por janela e seed registrável.

Exemplo de regra:

- gerar seed por localidade, filtro e janela de tempo;
- aplicar shuffle determinístico dentro de grupos elegíveis;
- registrar versão do algoritmo;
- permitir reproduzir uma ordenação para auditoria.

Não usar aleatoriedade totalmente opaca em queries críticas de SEO/comercial.

## `ANUNCIO_TOPO`

Regra proposta:

- Destaca o anúncio em área ou grupo explicitamente identificado como topo.
- Tem duração definida.
- Pode influenciar bloco de destaque.
- Deve registrar exposições ou pelo menos janelas de elegibilidade.
- Não deve furar filtros de localidade, status ou segurança.
- Não deve prometer posição fixa se o contrato comercial não disser isso.

## `POSICAO_GARANTIDA_TOP20`

Regra proposta:

- Benefício explícito.
- Garante elegibilidade dentro das 20 primeiras posições da listagem aplicável, respeitando filtros e inventário.
- Não promete posição 1.
- Deve ter limite por localidade/janela para evitar promessa impossível.
- Deve registrar janelas em que foi aplicado.
- Quando houver mais anúncios elegíveis que espaço, a regra comercial deve definir fila, rotação ou bloqueio de venda.

## Benefícios que não podem afetar ranking

Benefícios como `CARROSSEL_FOTOS`, `FOTOS_EXTRA_5`, `VIDEO_1`, `STORIES`, `WHATSAPP_CARD` e `OCULTAR_IDADE` não devem aumentar prioridade de ranking, salvo se forem transformados formalmente em benefício de ranking.

Regra:

- melhoria visual não é prioridade;
- benefício de conteúdo não é prioridade;
- benefício de privacidade não é prioridade;
- qualquer exceção exige código de benefício, descrição, duração, custo e auditoria.

## Importação premium/créditos

Pendências obrigatórias:

- `PREMIUM_INCONSISTENTE` quando ativação não reconciliar;
- `CREDITO_INCONSISTENTE` quando saldo/movimento/pagamento divergir;
- `PAGAMENTO_APROVADO_SEM_CREDITO` quando pagamento aprovado não tiver entrada;
- `CREDITO_SEM_PAGAMENTO` quando entrada financeira não tiver pagamento;
- `PAGAMENTO_COM_CREDITO_DUPLICADO` quando uma aprovação gerar mais de uma entrada;
- `PAGAMENTO_MERCADO_PAGO_LEGADO` quando o registro for legado descartado.

Não criar saldo final sem registrar como ele foi obtido.

## Gate

A V3 não pode virar produção se:

- qualquer divergência financeira individual ou total bloqueia a promoção e o go-live;
- pagamento aprovado puder gerar crédito duplicado;
- premium ativo não tiver início/fim;
- ranking tiver prioridade escondida;
- `POSICAO_GARANTIDA_TOP20` puder vender mais posições que o inventário permite;
- ajustes manuais não forem auditados.
## Complemento Bloco 22 - leitura local

O Bloco 22 implementa apenas leitura/calculo local de Premium e beneficios. Ele nao cria compra, checkout, cobranca, Pix/Efi funcional, credito real, ativacao real por dinheiro, job de expiracao, migration ou SQL de schema.

Regras consolidadas:

- Premium atual deve ser preservado;
- recursos Premium novos so podem ser aditivos;
- gratuito continua util;
- nao ha limite comercial diario de clique, contato ou WhatsApp no gratuito;
- beneficios em grupo/pacote/campanha devem expirar de forma coerente no calculo;
- se grupo expirou, todos os beneficios vinculados sao tratados como expirados;
- inconsistencias sao reportadas, nao corrigidas automaticamente neste bloco.