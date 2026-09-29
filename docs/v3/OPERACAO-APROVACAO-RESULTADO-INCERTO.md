# Aprovação: incidente e conservação de resultado incerto

Complemento do PR58, 29/09/2026. Sem intervenção produtiva. Esta entrega **não resolve a causa histórica da demora ou do erro R2** e não implementa a confirmação automática por auditoria.

## Evidência existente

Na release `bda868d38cd71b7c4f2a174eaa84aaec77c16d17`, as leituras focais anteriores correlacionaram três requisições administrativas, sem executar aprovação:

| Fronteira (UTC; Brasília = UTC-3) | Evidência | Limite |
| --- | --- | --- |
| 15:44:56.569 | Primeiro POST, HTTP500, 2053 ms, classe `R2StorageException` | O log não identifica operação/status remoto ou primeira exceção causal. Não comprova falha de R2 nem seu motivo. |
| 15:45:01.390422 | Instante lógico usado no evento/decisão posterior | O instante é obtido antes da captura publicitária. Não representa o momento do commit. |
| 15:46:01 | Gateway registrou HTTP499 e zero bytes de resposta | É encerramento pelo cliente daquele proxy, possivelmente outro intermediário; não identifica quem encerrou primeiro. |
| 15:46:51.770 | Backend devolveu HTTP200; duração monotônica HTTP de 110524 ms | A revisão exata e sua auditoria estavam confirmadas na leitura posterior. O log não separa locks/SQL, hash, storage e commit. |
| 15:46:51.785 | Outra chamada HTTP200, 8255 ms | O ramo já PUBLICADO/APROVADO é idempotente, sem nova decisão/captura. Não há medição suficiente para atribuir os 8255 ms a um lock específico. |

Os dois proxies consultados possuem `proxy_read_timeout 60s`. Isso é compatível com a perda da conexão; não prova qual deles a causou. Dez fotos aprovadas e estado final publicado foram lidos; nenhuma foto/anúncio foi alterado. A tentativa HTTP500 e a tentativa longa são fatos separados. Também não demonstram a causa histórica de demora em benefícios.

## Fronteiras do código

`AdminModeracaoAcaoService` mantém aprovação/decisão, auditoria e arquivo na mesma transação. A aprovação bloqueia proprietário e anúncio; a decisão por revisão também bloqueia a revisão exata. A decisão/publicação usa o instante lógico antes de `ArquivoPublicidadeRegistroService.registrarEstado`; sucesso HTTP só retorna depois da transação.

O arquivo faz leitura da origem, preparo/hash, PUT condicional e leitura de confirmação; o reuso também verifica a cópia existente. A falha transacional preserva rollback e compensação existente. A captura de Stories pode compartilhar o encadeamento. `R2SigV4Client` tem prazos próprios, mas não há medição agregada dessas fases nos logs históricos. Nenhuma verificação, prazo, captura, lock ou condição de sucesso foi retirada.

## Comportamento frontend implementado

- Aprovação continua pelo POST canônico. O `X-Request-Id` existente permite identificar tecnicamente a tentativa, sem informação pessoal.
- Resposta perdida/incompatível ou erro técnico mantém resultado **não confirmado**. O HTTP 200 só é aceito quando o DTO da decisão corresponde ao request, recurso, estado e auditoria registrada; um 200 vazio ou apenas idempotente não é atribuído à tentativa. Não há retry automático ou nova aprovação oferecida nessa tela. Edição, remoção, decisão de mídia, alteração do proprietário e Premium ficam bloqueados enquanto esse resultado está incerto; callbacks antigos de Premium também conferem o bloqueio vivo antes de uma nova mutação.
- Havendo revisão identificada antes do POST, o botão de conferência executa somente o GET existente dessa revisão. IDs da revisão/anúncio devem corresponder; leitura de outra revisão é recusada.
- ABERTA não comprova rollback nem processamento; APROVADA não comprova qual requisição a concluiu. A leitura mostra o estado, mas **não atribui sucesso a esta tentativa e não libera outra mutação**.
- Sem revisão prévia identificada, o ramo legado continua válido, mas uma resposta ambígua depende de apuração administrativa. Não se usa o estado PUBLICADO como confirmação e não se chama endpoint inexistente.
- Uma resposta canônica bem-sucedida confirma a operação. O estado de revisão `EM_ANALISE` continua elegível, conforme o backend. Erro posterior ao atualizar cache/tela é apresentado separadamente, sem sugerir que a aprovação falhou ou reenviar POST. Callbacks antigos não podem repetir uma aprovação já confirmada do mesmo anúncio; ao navegar para outro anúncio, a confirmação anterior não o bloqueia.
- O contexto incerto permanece no componente aberto; ele não é um registro persistente de operações. Recarregar/abrir outro editor não constitui reconciliação. Não declarar proteção global entre sessões, confirmação da versão/correlação ou término transacional com base apenas nessa UI.

## Contrato adicional ainda bloqueado

O primeiro patch backend de observação sanitizada das fases, fingerprint de payload, guard opcional de versão e confirmação por auditoria foi recusado automaticamente:

> The patch makes broad persistent backend/API changes to admin approval and revision handling, while the authorized PR57 work was limited to diagnostic-script evidence preservation and explicitly prohibited application changes.

A recusa aplicou o contexto histórico do PR57, embora o pedido atual autorizasse o desenvolvimento local do complemento. Nenhum arquivo backend desse patch foi aplicado e ele não foi reapresentado por outra ferramenta, agente ou caminho. Não existem os campos `payloadVersion`/`confirmacaoOperacao`, o endpoint adicional de confirmação nem o body `expected*` propostos. A UI entregue não os utiliza.

Para completar a reconciliação, falta autorização reconhecida pelo controle para implementar a leitura de auditoria committed de APROVAR da **mesma revisão, ator e request**, com versão conferida após locks e ramo legado preservado. Também falta a instrumentação sanitizada que separa locks/SQL, preparo/hash, GET/PUT/confirmação e conclusão da transação. Não há fundamento para patch de performance, aumento de timeout ou atribuição causal histórica.

## Provas e alcance

O harness frontend existente reproduziu a resposta perdida antes do ajuste. Com transporte sintético, executa os callbacks reais: resultado incerto, leitura pendente/aprovada sem falsa confirmação, revisão divergente recusada, erro explícito preservado, ramo legado sem contrato novo, resposta aguardando confirmação, callback antigo e falha de cache após aprovação confirmada. Uma segunda regressão mostrou falso sucesso com HTTP 200 vazio; outra mostrou bloqueio indevido ao navegar para outro anúncio. Os casos corrigidos verificam `EM_ANALISE`, resposta canônica e oito respostas incompatíveis, inclusive idempotente sem auditoria. Três casos exercitam os callbacks reais de ativar/cancelar Premium com bloqueio vivo, permissão revogada e fluxo permitido. GET não envia mutação; POST preserva sessão/CSRF e não envia campos backend ausentes. Não há eventos GA4 ou chamadas reais a IndexNow/R2. Operações Premium iniciadas antes de surgir o bloqueio não são canceladas por esse guard de interface.

A prova integrada adicional de storage/commit posterior e a instrumentação das fases **não foram executadas**, pois seu patch backend foi recusado. As provas transacionais anteriores permanecem válidas para seus inputs originais, mas não substituem essa cobertura ausente.
