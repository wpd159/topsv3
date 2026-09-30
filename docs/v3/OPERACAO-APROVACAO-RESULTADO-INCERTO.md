# Aprovação: incidente e conservação de resultado incerto

Complemento do PR58, 29/09/2026. Sem intervenção produtiva. Esta entrega **não resolve a causa histórica da demora ou do erro R2**. A conferência posterior por auditoria agora é somente leitura e depende de evidência positiva da mesma transação; ausência de evidência continua inconclusiva.

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

- Aprovação continua pelo POST canônico. `X-Request-Id` identifica o tráfego HTTP e pode ser substituído pelo proxy. A tentativa de negócio usa UUID próprio no corpo, versão esperada do anúncio e revisão esperada quando conhecida; esses valores não são autorização. O backend mantém o ID HTTP separado e registra a tentativa no snapshot de auditoria da mesma transação.
- Resposta perdida/incompatível ou erro técnico mantém resultado **não confirmado**. O HTTP 200 só é aceito quando o DTO da decisão corresponde ao UUID da operação, versão, recurso, estado e auditoria registrada; um 200 vazio ou apenas idempotente não é atribuído à tentativa. Não há retry automático ou nova aprovação oferecida nessa tela. Edição, remoção, decisão de mídia, alteração do proprietário e Premium ficam bloqueados enquanto esse resultado está incerto; callbacks antigos de Premium também conferem o bloqueio vivo antes de uma nova mutação.
- O botão de conferência consulta `GET /api/admin/anuncios/{anuncioId}/aprovacao-operacoes/{operacaoId}/status`, sob `ANUNCIO_MODERAR`. O backend exige anúncio, revisão quando informada, ator autenticado, versão, operação, decisão, snapshot publicado/aprovado e auditoria da mesma transação física PostgreSQL. A resposta não contém dados da auditoria de outro ator e usa `no-store`.
- Estado atual PUBLICADO ou revisão APROVADA isoladamente não comprovam qual tentativa concluiu. Sem evidência transacional única, a consulta devolve `INCONCLUSIVA`, não libera outro POST e não presume rollback. A regularização legada confirmada é sinalizada como `PUBLICACAO_REGULARIZADA`, distinta de uma nova decisão `CONFIRMADA`.
- Uma resposta canônica bem-sucedida confirma a operação. O estado de revisão `EM_ANALISE` continua elegível, conforme o backend. Erro posterior ao atualizar cache/tela é apresentado separadamente, sem sugerir que a aprovação falhou ou reenviar POST. Callbacks antigos não podem repetir uma aprovação já confirmada do mesmo anúncio; ao navegar para outro anúncio, a confirmação anterior não o bloqueia.
- A referência mínima da operação incerta (UUID, anúncio, revisão e versão) fica em `sessionStorage`, separada por ator/anúncio e removida no logout, novo login autenticado ou resultado confirmado da operação exata. Reload da mesma aba conserva o bloqueio na sessão corrente; outra sessão/dispositivo não ganha proteção global. A persistência local não constitui reconciliação, prova de commit ou autorização. Navegação da fila não pode aplicar callbacks antigos ao anúncio seguinte.

## Histórico de recusa anterior e contrato atual

O primeiro patch backend amplo de observação sanitizada das fases, fingerprint de payload, guard opcional de versão e confirmação por auditoria foi recusado automaticamente:

> The patch makes broad persistent backend/API changes to admin approval and revision handling, while the authorized PR57 work was limited to diagnostic-script evidence preservation and explicitly prohibited application changes.

A recusa aplicou o contexto histórico do PR57, embora o pedido atual autorizasse o desenvolvimento local do complemento. A correlação focal de UUID de operação, versão e revisão no POST foi implementada e testada separadamente. O endpoint somente leitura que comprovaria a conclusão posterior de uma resposta perdida foi recusado; a instrumentação persistente da aprovação/storage também foi recusada em tentativa posterior. Nenhum desses dois patches foi aplicado ou reapresentado por outra ferramenta, agente ou caminho. Não há `confirmacaoOperacao` consultável pela UI.

Com a autorização posterior do PR58, o GET somente leitura e a instrumentação sanitizada foram implementados e testados localmente. O `xmin` comum da decisão/auditoria é exigido como prova da mesma transação PostgreSQL; registros cujo vínculo físico se perca numa restauração permanecem inconclusivos. A observação mede, com relógio monotônico, lock/SQL, preparo/hash, GET, reuso, PUT, confirmação e conclusão/rollback **após** o callback transacional. Logs novos usam apenas UUID técnico, fase, duração, resultado e status R2 numérico reconhecido; não registram conteúdo, chave de storage nem mensagem arbitrária de exceção. Essas medições futuras não identificam retroativamente a causa dos 110,5 segundos ou do erro R2 histórico. Não há fundamento para patch de performance, aumento de timeout ou atribuição causal histórica.

## Provas e alcance

O harness frontend existente reproduziu a resposta perdida antes do ajuste. Com transporte sintético, executa os callbacks reais: resultado incerto, leitura pendente/aprovada sem falsa confirmação, revisão divergente recusada, erro explícito preservado, ramo legado válido, resposta aguardando confirmação, callback antigo e falha de cache após aprovação confirmada. Uma segunda regressão mostrou falso sucesso com HTTP 200 vazio; outra mostrou bloqueio indevido ao navegar para outro anúncio. Os casos corrigidos verificam `EM_ANALISE`, resposta canônica com ID HTTP preservado ou substituído, respostas incompatíveis, reload, logout e navegação A→B sem POST antigo. Três casos exercitam os callbacks reais de ativar/cancelar Premium com bloqueio vivo, permissão revogada e fluxo permitido. GET não envia mutação; POST preserva sessão/CSRF e envia somente os campos de correlação agora suportados. Não há eventos GA4 ou chamadas reais a IndexNow/R2. Operações Premium iniciadas antes de surgir o bloqueio não são canceladas por esse guard de interface.

O complemento atual acrescentou testes PostgreSQL descartáveis para commit, invisibilidade antes do commit, rollback, transações distintas com campos iguais, correlação divergente e regularização legada; testes de método para permissão e `no-store`; e testes de logs sanitizados e conclusão transacional. São provas sintéticas, não uma repetição da aprovação produtiva nem uma reprodução da navegação real do Next no navegador. A causa da ocorrência histórica continua inconclusiva.
