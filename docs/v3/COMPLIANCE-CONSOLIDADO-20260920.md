# Compliance: lote focal para revisão

Base conferida: `ae37e2f2fd6cfd895fb633202be90b9b300e6a30` (20/09/2026).
Escopo: F02–F07 da auditoria funcional direcionada; P1/P2 apenas preparação de decisões.
Sem publicação, documentos reais, alterações de política de acesso ou de retenção.

## Matriz antes da implementação

| Achado | Evidência inicial | Fronteira mínima | Regressão exigida |
|---|---|---|---|
| F02 | Hub observa `hashchange`/`popstate`; Next Link pode trocar o hash sem esses eventos. Prova anterior era estática. | Página/atalhos do hub; navegação HTML nativa. | Componente real: abrir áreas, URL direta, voltar, histórico e cliques modificados. |
| F03 | Modal perde referência no fechamento; criação anterior depende da chave de idempotência em memória. | Challenge contextual existente, DTO mínimo e modal; nenhum armazenamento de dados pessoais no navegador. | Fechar/reabrir/recarregar, pendência e aprovação, sem upload/desafio duplicado; vínculos incompatíveis negados. |
| F04 | Status pode usar o enum documental sem confrontar o prazo efetivo. | Serviço de status/verificação; preservar renovação mínima administrativa já existente. | Antes/no limite/após vencimento; prazo maior preservado; consulta não renova prazo. |
| F05 | Emissão procura somente tokens ativos, mas o índice é único por desafio/escopo. | Consulta do histórico de tokens e recusa controlada, sem alterar o índice. | Primeira emissão, repetição válida, revogação, repetição negada e novo desafio; PostgreSQL com flush/commit. |
| F06 | Caminho de arquivo documental de visitante não registra ator/referência em auditoria específica; log HTTP não é equivalente. | Auditoria existente e fronteira de leitura privada. | Permitido, negativo, storage indisponível e rollback; ausência de conteúdo e segredos na trilha. |
| F07 | Decisão/abertura KYC leem envio sem serialização explícita. Isso exige reprodução, não prova isoladamente um incidente. | Teste integrado e, se necessário, lock no envio usado pelas operações concorrentes. | Aprovar × rejeitar; decisão × abertura; resultado final e auditoria coerentes. |

Somente F02 foi relatado pelo auditor como reprodução na interface produtiva. As provas deste lote são locais e sintéticas. Resultados de execução e limites serão registrados na entrega da candidata, sem reutilizar aprovação de bytes anteriores.

## Resultado local consolidado

| Achado | Resultado e menor correção |
|---|---|
| F02 | Reproduzido no Next/Chromium: 4 falhas antes; 6 cenários aprovados após usar âncoras nativas no hub/sidebar. URL, área, histórico e cliques modificados conferidos também no build de produção local. |
| F03 | Modal original falhou ao retomar após fechar/reabrir. Modal corrigido passou 6 cenários com React/API/cache reais e HTTP sintético: fechar/reabrir, recarregar/aprovar/confirmar, modal aberto e estados sem autorização. Backend consulta o caso contextual, bloqueio e prazo; documento pendente não é duplicado. |
| F04 | Defeito de código confirmado; regressão com relógio controlado cobre antes, no limite e depois do prazo efetivo, inclusive renovação administrativa e preservação de prazo maior. Status não renova prazo. |
| F05 | Dois cenários PostgreSQL originais falharam por unicidade após revogação, no commit real. Após a correção, primeira emissão/repetição continuam válidas; a repetição revogada retorna 410 também na fronteira HTTP, sem novo token. Novo desafio legítimo funciona. Índice/migrations intactos. |
| F06 | Ausência de trilha equivalente confirmada no caminho de leitura. Auditoria específica reutiliza o repositório existente e transação independente; permitidos, negativas RBAC HTTP, documento fora da área canônica e falha de storage têm provas sintéticas. Registros necessários persistem apesar de rollback externo. |
| F07 | PostgreSQL reproduziu dois sucessos incompatíveis em aprovar/rejeitar e estado rebaixado pela abertura concorrente. Lock do envio nas duas operações corrigiu ambos; as mesmas barreiras e assertions passaram, sem migration. Repetição da decisão permanece 409 conforme contrato. |

Validações locais: 72 testes backend do conjunto e 25 testes focais de fechamento, todos sem falhas/skips (há classes repetidas para validar somente o delta posterior). Build Next 15.5.24, typecheck, lint, 6 cenários do hub e 6 do modal aprovados. Após estabilizar callbacks do modal, typecheck/lint focal e os 6 cenários foram novamente aprovados; o CI valida o build final. Não houve Maven completo ou suíte operacional local. A pipeline existente ganhou somente a habilitação das duas classes PostgreSQL e as chamadas dos dois harnesses de navegador, mantendo os demais gates.

O primeiro CI (`35547681894`, head `90e8fb80`) aprovou Maven, mas revelou uma fixture de imagem com estado `CREATED`, que nem a base nem o backend atual emitem. Corrigida somente para `CHALLENGE_ACTIVE`: os mesmos 17 cenários passaram localmente, sem tentativas protegidas anônimas e com cleanup aprovado. O contrato estático do modal foi ajustado para acompanhar a chamada contextual após a consulta de autorização; 45 checks aprovados. As falhas originais foram preservadas, sem remover assertions ou ampliar prazos. Esse CI inicial não aprova a candidata posterior; o resultado do SHA final será conferido separadamente no PR.

O CI seguinte (`35548997760`, head `ccdb4bcf`) aprovou Maven, build e paginação, mas uma espera do popup do hub expirou. A captura original registra somente a aba de origem: não permite concluir o estado do popup Linux. Uma prova local controlada demonstrou que o observador baseado em `requestAnimationFrame` expira mesmo com URL/hash/título corretos quando os frames estão suspensos. A mesma prova e a rodada normal passaram 6/6 após usar polling de 50 ms, preservando o prazo de 5 segundos e todas as assertions. O harness passou também a registrar estado conjunto e captura do popup. Isso corrige um defeito comprovado do observador, sem apresentar como observada a condição original não capturada no CI. Referências do comportamento de polling: [Playwright](https://playwright.dev/docs/api/class-page#page-wait-for-function), [requestAnimationFrame](https://developer.mozilla.org/en-US/docs/Web/API/Window/requestAnimationFrame).

Os arquivos alterados se concentram em: hub/sidebar e seu harness (F02); modal, DTO/API, serviços de challenge/documento/status/risco e consultas de contexto/histórico (F03–F05); serviço/controlador de leitura e auditoria/advice restrito (F06); serviço/repositório KYC (F07); testes dessas fronteiras, este documento e chamadas do CI. Não há mudança de schema, dependências ou configuração produtiva.

### Limites das provas

- As fixtures usam PostgreSQL local com migrations canônicas e storage/HTTP sintéticos; não são testes de produção nem de documentos reais.
- `BYTES_PREPARADOS` comprova apenas que o serviço obteve e preparou o corpo. Não comprova conclusão da transmissão HTTP nem visualização humana. Falhas posteriores de transmissão não são apresentadas como entrega auditada.
- Recusas por permissão que chegam ao método da rota documental são auditadas. Recusas de autenticação no filtro anterior ao mapeamento continuam no mecanismo de segurança existente; não foram transformadas em acesso documental identificado.
- Uma verificação estática extra, `test-saneamento-contratual.mjs:42`, encontrou expectativa antiga de sitemap fora do delta; teste e quatro inputs pertinentes são idênticos à base. Não foi alterada nem convertida em PASS. Os gates obrigatórios seguem intactos.
- Evidências originais vermelhas e verdes foram preservadas em `tops-compliance-consolidado-20260920`; CI e SHA finais constam no PR/relatório de entrega. P1/P2 abaixo continuam abertos.

## P1 — titularidade e suficiência: pendente de decisão

### O que existe e o que isso não prova

`CpfVisitanteValidator` delega ao validador de formato/dígitos de CPF. `ComplianceVisitorVerificationService` compara as datas declaradas, calcula maioridade, exige aceites e aplica risco. Sem exigência documental, o fluxo pode emitir acesso sem consulta a uma fonte que vincule os dados à pessoa que opera a sessão. Uma assinatura de cookie comprova a integridade do estado emitido pelo servidor, não a identidade civil do visitante.

O fallback guarda arquivo privado para análise administrativa. Aprovação registra uma decisão, mas não demonstra, por si só, autenticidade do documento ou sua titularidade pelo apresentante. Não foi identificada, nas fronteiras examinadas, integração de comprovação de titularidade, OCR, selfie, prova de vida ou credencial externa de idade. Não se afirma que nenhum processo humano externo exista; sua existência, responsável e evidências não foram comprovados nesta entrega.

O KYC do anunciante é separado: possui envio, análise e decisão documental, além de dados cadastrais. Não é autorização intercambiável com o acesso do visitante e não será reutilizado implicitamente para elevá-lo.

### Requisito de referência

O art. 9º da Lei 15.211/2025 exige mecanismos confiáveis de verificação e veda autodeclaração como mecanismo suficiente nesse contexto. Checksum e idade declarada não comprovam o atendimento a esse requisito. [Fonte primária](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2025/lei/l15211.htm).

Os arts. 15–17 e 24 do Decreto 12.880/2026 tratam de eficácia, proporcionalidade, confiabilidade e minimização. Essas referências não autorizam presumir que biometria seja obrigatória ou que qualquer arquivo documental resolva a lacuna. [Fonte primária](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2026/decreto/d12880.htm).

### Alternativas concretas para decisão posterior

1. **Reaproveitar a análise administrativa**, com um protocolo aprovado que especifique qual evidência liga a aferição à pessoa/sessão, critérios de aceitação, contestação, responsabilidade, capacidade operacional e descarte. É o menor reaproveitamento técnico; a inspeção do mesmo arquivo enviado, sem evidência adicional adequada, não fecha a lacuna. Exigir tal decisão para outros acessos altera política e não foi implementado.
2. **Integrar uma comprovação de faixa etária minimizada**, após demonstrar um mecanismo disponível, adequado e autorizado. Reaproveitar challenge, vínculos e validação de assinatura/prazo; não transportar identidade civil quando bastar o resultado etário. Depende de contrato técnico, avaliação jurídica/privacidade, confiabilidade, disponibilidade e eventual custo ainda não aprovados. Nenhum fornecedor foi selecionado ou contratado.

**Decisão necessária do proprietário e responsável jurídico/privacidade:** qual evidência/protocolo é suficiente para o risco do serviço, quem responde pela análise e qual regra será aplicada enquanto a suficiência não estiver comprovada. A recomendação técnica é não certificar o mecanismo atual como prova de titularidade. Este PR corrige defeitos do fluxo, não toma essa decisão nem altera os níveis de autorização.

## P2 — retenção e descarte: pendente de implementação e política específica

### Estado demonstrado

- A revisão editorial anterior preservou a obrigação de descarte; não transformou ausência de rotina em autorização de conservação.
- Documentos de visitantes (`compliance_visitor_documento`) têm status PENDING/APPROVED/REJECTED e referência privada. Decidir, expirar challenge ou revogar token não exclui o objeto. Não há, nessa entidade, controle de expurgo equivalente ao de documento de usuário.
- `DocumentoUsuarioEntity` possui política, `retencaoAte`, `removidoEm` e `expurgadoEm`; o envio KYC usa `ENQUANTO_HOUVER_ANUNCIO` e pode não ter data definida. A presença dos campos não prova que exista executor de descarte nem aprova juridicamente esse prazo/finalidade.
- `ObjectStorage.delete` está disponível. As chamadas examinadas em upload/KYC tratam compensação de operações mal sucedidas, não descarte ao terminar a aferição. Ocultar documentos de contas excluídas é controle de consulta, não prova de eliminação física.
- Não foi apresentada evidência de execução de expurgo documental, responsável nominal, inventário de cópias ou tratamento de versões/backups. Não foram consultados documentos/storage reais para procurar essa prova.

O art. 24, §3º, do Decreto 12.880/2026 prevê eliminação imediata e irreversível da imagem/cópia após capturar a informação necessária à aferição. As hipóteses gerais de conservação da LGPD não devem ser aplicadas por suposição a uma finalidade distinta. Finalidade e necessidade precisam de análise específica; não foi inventado prazo de dias nem fundamento de retenção. [Decreto](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2026/decreto/d12880.htm), [LGPD, arts. 15–16](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm).

### Menor plano verificável para implementação futura

1. Identificar, por finalidade, o instante de conclusão da aferição e a regra para envio abandonado/inconclusivo; designar executor e responsável de revisão. Separar visitantes de KYC de anunciantes, sem presumir conservação por haver anúncio.
2. Reaproveitar o serviço de storage privado e a auditoria para uma operação restrita aos objetos do caso, com rechecagem de vínculo/concorrência. Manter somente resultado e evidência mínima de execução permitidos pela política aprovada.
3. Não marcar expurgo concluído se a exclusão falhar ou for incerta. Diferenciar indisponibilidade da consulta, remoção de metadados e confirmação da eliminação do objeto/cópias. Um erro de GET isolado não prova eliminação.
4. Validar em PostgreSQL e storage descartáveis com arquivos fictícios: caso pendente, decisão final, abandono conforme política, objeto ausente, erro/timeout de exclusão, repetição idempotente, corrida com leitura/decisão e isolamento entre casos. Exigir estado e trilha coerentes sem conteúdo documental.
5. Conferir separadamente retenção/versionamento/cópias/backups do mecanismo que vier a ser usado. Um teste de armazenamento sintético não comprova descarte produtivo ou de backups. Preparar aplicação focal para revisão, jamais expurgo geral automático por esta autorização.

**Decisão necessária:** responsável, finalidades efetivas, momento de eliminação e eventual fundamento específico de conservação distinta, inclusive tratamento de cópias. O contato institucional existente não é prova de designação nominal desse responsável. Nenhum objeto real foi excluído e nenhuma política foi alterada.

## Limite de aprovação

F02–F07 aprovados em teste, quando comprovados, não encerram P1/P2 e não equivalem a conformidade integral. A revisão conjunta destas pendências permanece necessária antes de autorizar publicação. Blog, GA4, lógica de consentimento, SEO, filas, fotos, pagamentos e textos institucionais permanecem fora do delta.

## Complemento visual autorizado: modal de maioridade/cookies

O proprietário autorizou acrescentar o ajuste visual mínimo ao mesmo lote antes do push seguinte. Dois `className` locais de `age-gate-modal.tsx` substituem a altura móvel fixa por altura automática limitada a `100dvh`; a área do corpo mantém um mínimo legível, e o próprio modal pode rolar em telas muito baixas. Os overrides `sm:` preservam o desktop. Nenhum handler, condição de abertura, escolha, mensagem, Dialog compartilhado ou mecanismo de consentimento foi alterado.

O teste existente `test-age-gate-mobile-layout.mjs` foi atualizado e ganhou `--browser`, usando React/Radix e CSS Tailwind reais, transporte sintético bloqueado antes da navegação e emulação com toque. Cobre conteúdo curto, aviso conjunto, personalização, baixa altura, erro, foco, escolha preexistente/recusa e desktop. As capturas locais mostram cookies-only de 844 para 458 px numa tela de 390×844; na tela de 320×320 o corpo deixa de colapsar para zero e mantém 96 px roláveis. As duas capturas desktop conservaram os mesmos hashes. Não houve Android físico, coleta GA4 real ou alteração produtiva. A chamada utiliza o job frontend e Playwright já existentes; os demais gates não foram retirados.
