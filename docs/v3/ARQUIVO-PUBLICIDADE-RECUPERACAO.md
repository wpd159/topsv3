# Recuperação do arquivo publicitário: capacidades e limites

## Atualização focal de 26/09/2026: retomada após b68

O proprietário autorizou posteriormente a reprodução e correção focal da retomada, com dados sintéticos e storage simulado. O mestre SEO v1.0.0 foi lido integralmente; o escopo é compatível com suas regras de preservação, retirada e rastreabilidade. As conclusões desta seção são confirmadas no código local e nos ensaios descritos, não em produção. O registro documental original foi conservado abaixo como diagnóstico da revisão `ca8fa6a0`, distinguindo-o da correção posterior.

### Causa reproduzida antes da correção

Os dois testes PostgreSQL novos falharam na implementação anterior: esperavam uma única versão-fonte preservada após retirada/pausa+reativação sem capturador e encontraram três. Evidência: `C:\topsdojob\tmp\pr55-reuso-retomada-20260926\red-focal.log`, testes `retiradaAnteriorAFronteiraSemCapturadorNaoMaterializaExibicaoInexistente` e `retiradaEReativacaoSemCapturadorNaoSeConfundemComExibicaoContinua`.

A reprodução complementar usa a JVM b68 completa já preservada e uma JVM candidata completa anterior à correção. O runner local separado `C:\topsdojob\tmp\pr55-verificacao-20260926\recovery\run-resume.py` reaproveita funções/seed da fixture anterior sem executar seu bloco principal nem modificar suas evidências. PostgreSQL 17.10/Flyway 12.10.0 e MinIO TLS sintético funcionam em rede Docker interna, sem portas publicadas; imagens locais usam `--pull=never`. EFI/SMTP ficam desligados exclusivamente para isolamento. Acervo e planos são semeados, não produzidos pelo capturador nessa fixture; projeção, dependências, hashes, variantes e referências privadas são coerentes com o cenário sintético. Os testes PostgreSQL versionados exercitam o planejador real separadamente.

Na execução `resume-20260926T122632`, em `C:\topsdojob\tmp\pr55-reuso-retomada-20260926`, o resultado foi `REGRESSION_REPRODUCED`. JAR b68: SHA256 `7b062229558f89875f191b6025a82070e6c9e52b417c131245afa513eafd5607`; candidata anterior à correção: `68cd7b454c6a3398659f3a49e5e8304be7e0811351a7e79441b05e8797bfb314`. O cenário contém anúncio com foto e vídeo, benefício de vídeo que vence, período de Story `ANUNCIO`, referências/holds e planos futuros para retirada temporal do vídeo. A fonte tem duas versões de anúncio e uma versão no período atual do Story.

| Cenário executado em b68 antes da fronteira | Anúncio / Story após retomada anterior à correção | Resposta pública do anúncio após retomada |
| --- | --- | --- |
| Controle sem retirada | 3 / 2 versões | 200 |
| Remoção lógica canônica | 3 / 2 versões: derivação indevida adicional | 404 |
| Bloqueio jurídico → desbloqueio → reativação canônica | 3 / 2 versões: derivação indevida apesar da interrupção | 200 |

As operações reais da JVM b68 usaram autenticação e CSRF. O histórico registrou remoção às `15:27:23.621832Z`, bloqueio às `15:27:25.286395Z`, desbloqueio para `PAUSADO` às `15:27:26.569255Z` e reativação às `15:27:27.241136Z`, antes da fronteira `15:28:02.619447Z`. A candidata anterior verificava apenas janela/versões arquivadas: b68 as preservava abertas e o plano continuava elegível ao processamento. Assim, o defeito era criação de histórico sem suporte após interrupção, **não republicação de mídia retirada**; a remoção permaneceu 404.

As 12 cópias privadas sintéticas conservaram os bytes; linhas de mídia, holds e histórico Flyway permaneceram idênticos. Cleanup removeu os quatro containers e a rede próprios, sem remanescentes. A preparação anterior `resume-20260926T122419` falhou porque a seed convertia foto em vídeo mantendo `LIVRE`, violando a constraint existente; corrigiu-se somente a fixture para `RESTRITA_18` e origem privada sintética. A falha e seu cleanup continuam preservados, sem atribuí-la ao serviço.

### Correção limitada ao processamento existente

Antes de materializar um plano, `ArquivoPublicidadeTransicaoTemporalService` consulta `anuncio_status_historico` entre a versão-fonte e a fronteira. Transição para estado não publicado invalida a derivação, mesmo que exista reativação posterior e o anúncio esteja novamente `PUBLICADO`. A mesma evidência do anúncio se aplica ao seu plano de Story `ANUNCIO`. Retirada documentada somente depois da fronteira não invalida aquela transição anterior.

O plano incompatível passa a `CANCELADA`, no instante real de processamento. A auditoria existente recebe `ARQUIVO_PUBLICIDADE_TRANSICAO_LACUNA`, resultado `PENDENTE`, com IDs da fonte/plano e evidência histórica; não contém bytes, identidade civil ou conteúdo da peça. Quando o anúncio observado está inelegível e não há histórico suficiente para sustentar a fronteira, registra a lacuna sem inventar o instante da retirada. A versão-fonte, a janela e suas datas, holds e objetos permanecem preservados. Reprocessar o mesmo plano não duplica evento ou versão.

O controle não é uma reconstrução integral do intervalo b68. Ele cobre descontinuidade de status do anúncio e o Story vinculado; não passa a capturar edições, alterações de mídia/benefício ou retiradas próprias de Story que não tenham correspondente descontinuidade do anúncio. Uma janela antiga ainda aberta não prova continuidade pública, e uma lacuna auditada não é uma versão histórica recuperada. A prova anterior de preservação sob b68 continua válida dentro de seus limites; a interrupção de novas capturas sob b68 continua existindo.

Validação posterior à correção: `green-focal-1.log`, na pasta `C:\topsdojob\tmp\pr55-reuso-retomada-20260926`, registra **58 testes, zero falhas, erros ou skips**, incluindo seis testes PostgreSQL de transições temporais, os testes de retirada/Story com PostgreSQL e os testes de consulta administrativa. `green-package.log` registra o empacotamento aprovado. As regressões de retomada estão em `ArquivoPublicidadeTransicaoTemporalPostgres17IntegrationTest`, linhas 302–458, e a proteção em `ArquivoPublicidadeTransicaoTemporalService`, linhas 632–764.

A contraprova com as JVMs completas terminou **PASS** em `resume-20260926T123639/result.json`, na mesma pasta de evidências. JAR corrigido: SHA256 `9146d1a543f413f277e63a661cd3fdac7635004e4f4123e15d07d9ac94af65f1`; JAR b68 reutilizado inalterado. Após a fronteira `2026-09-26T15:38:09.230819Z`, o controle continuou produzindo **3 / 2** versões (anúncio / período atual de Story), enquanto remoção e bloqueio+reativação conservaram **2 / 1**, sem a versão derivada indevida. As respostas públicas permaneceram respectivamente **200, 404 e 200**. As 12 cópias privadas, linhas de mídia, holds e checksums Flyway foram preservados. Ambos os backends ficaram prontos e encerraram normalmente; cleanup removeu os quatro containers e a rede próprios, sem remanescentes. Nenhuma rodada desta tarefa acessou produção, R2 real ou Cloudflare.

## Registro preservado da revisão documental anterior (`ca8fa6a0`)

O texto abaixo descreve as capacidades e limitações examinadas **antes** da reprodução e correção focal acima. Seu diagnóstico de procedimento operacional completo bloqueado não exige que b68 implemente o arquivo novo, nem introduz mecanismo ou gate global neste PR.

Data: 26/09/2026. Revisão examinada: `ca8fa6a0fec08e451cd8773ce864879c83095b6a` (PR55). Revisão anterior examinada: `b68a6c7063f6255d3d5a451421449bee4f435024` (`b68`). Schema do cenário: V057. Este documento resulta exclusivamente de leitura local do código versionado e das evidências já existentes. Não comprova V057 em produção, não autoriza publicação ou recuperação, e não executou JVM, testes, backup, cópia de objetos, R2 ou qualquer ação produtiva.

**Resultado: a preservação do acervo sob a JVM b68 foi aprovada na fixture sintética existente; o procedimento operacional completo de permanência em b68 e retomada da candidata permanece BLOQUEADO.** O repositório oferece recuperação da aplicação e retiradas canônicas, mas não foi encontrado controle existente que contenha seletivamente todas as mudanças dependentes de captura durante esse intervalo, nem reconciliação do intervalo anterior ao processamento automático V057. Não se presume um controle externo, não se propõe novo mecanismo neste documento e não se transforma essa lacuna em autorização de bloqueio global.

## Evidências utilizadas e alcance

- [Contrato do arquivo](ARQUIVO-PUBLICIDADE-CONTRATO.md), linhas 32–46, 61 e 65–71: unidade temporal, referências privadas, retirada, proveniência, guarda e proibição de reconstrução histórica por inferência.
- [Revisão focal](ARQUIVO-PUBLICIDADE-REVISAO.md), linhas 24–35: correções prospectivas e limites. A linha 35 descreve uma fixture anterior que não iniciou a JVM completa. A prova local posterior abaixo cobre essa partida em seu próprio ambiente; não converte a antiga fixture em prova de algo que ela não executou.
- Evidência local posterior: `C:\topsdojob\tmp\pr55-verificacao-20260926\recovery\RECUPERACAO-RESULTADO.md`, especialmente linhas 7–12, 25–61 e 75–79; artefatos da execução `run-20260926T001547` na mesma pasta de recuperação.
- Fontes do helper, workflow, controllers e serviços relacionadas ao final deste documento. As referências de código são da candidata, salvo indicação expressa de `b68`; o código anterior foi consultado por `git show`/`git grep`, sem mudar o checkout.

A prova existente iniciou a JVM anterior completa recompilada, com PostgreSQL 17.10/V057, Flyway preservado e runtime Temurin 17.0.13+11. Houve leitura pública, autenticação/CSRF, retirada lógica canônica, preservação das linhas do arquivo/holds/referências/planos/bytes sintéticos e parada normal. Os dados do acervo foram semeados após migrations; não foram produzidos pelo capturador da candidata nessa prova.

O JAR recompilado tem SHA256 `7b062229558f89875f191b6025a82070e6c9e52b417c131245afa513eafd5607`; o JAR produtivo informado naquele relatório tem SHA256 `0a18296da725294c2689eddfe9ebc4a07a62ec82051122f8cc7f4be9dd43a351`. A diferença impede afirmar identidade física. Não foram cobertos frontend/gateway, callback EFI, SMTP, browser, execução do helper produtivo ou configuração produtiva completa. A prova não foi repetida nesta revisão.

## Controles realmente disponíveis

| Capacidade existente | O que comprova ou faz | Limite relevante ao arquivo |
| --- | --- | --- |
| `deploy.lock`, `operations/active.state`, identificação de proprietário/filho/boot, marcador `preview-backfill.pending` | Serializam ativação/recuperação e recusam journal inválido, pendência incompatível ou dono vivo. | Não bloqueiam requisições de negócio, produtores da aplicação ou mudanças de seleção por relógio. `mutating` em `op_run` classifica o comando supervisionado; não é um modo de manutenção do backend. |
| Baseline do helper | Registra release anterior, IDs físicos de imagens, hashes de configuração/Compose/runtime e identidade do PostgreSQL; conserva uma cópia privada de `production.env` quando uma operação de deploy cria seu baseline. | Não é snapshot do acervo, inventário R2 ou prova de captura contínua. Esta revisão não criou baseline nem cópia. |
| `rollback ROOT OPERATION_ID --confirm-daemon-quiescent` | Recupera a release **anterior do journal**, configuração capturada e imagens existentes, verifica runtime e troca `current` atomicamente. | Não escolhe arbitrariamente b68, não restaura banco, não desfaz V054–V057 e não recupera períodos ausentes. Só se aplica a journal cuja `previous` seja b68. |
| `reconcile ROOT OPERATION_ID EXPECTED_SHA --confirm-daemon-quiescent` | Verifica uma release já ativa, configuração e smoke; registra `RECONCILED`. | Não instala a candidata e não reconcilia arquivos publicitários. Mesmo esse modo escreve o journal operacional. |
| `restoration.complete` e janela funcional de recuperação de até 300 segundos | Evitam repetir restauração física já comprovada; verificam identidade, saúde, home e catálogo. | `RECOVERY_WINDOW` mede a observação funcional após restauração; não mede todo o tempo sem captura, nem assegura a cobertura do arquivo. |
| Retirada administrativa canônica e bloqueio jurídico | Há rotas autenticadas, autorização e trilhas operacionais existentes em b68. A retirada lógica foi exercitada na fixture. | b68 não fecha as janelas V054/V055 nem reconcilia os planos V057. A fixture não prova todas as modalidades de retirada ou falhas de storage. |
| Bootstrap e scheduler V057 da candidata | Examinam fontes arquivadas e processam planos temporais, com origem, fronteira e instante posterior de processamento. | Não recebem um intervalo de recuperação, não importam as retiradas ocorridas sob b68 e não substituem revisão da validade dos planos. |

O coordenador de previews efetivamente chamado no workflow atual executa validação antes da ativação; seu código explicita que não drena produtores nem para serviços. Trechos históricos de `TRANSICAO-PREVIEWS.md` sobre outra sequência não constituem uma capacidade de contenção seletiva do arquivo. A configuração Nginx produtiva é montada de arquivo externo ao repositório; nenhum bloqueio seletivo nela foi comprovado por esta revisão local.

## Delimitação do intervalo sem captura

Os marcos abaixo são definições para interpretar evidências de uma eventual ocorrência. Não são campos já produzidos pelo helper e não indicam que a ocorrência aconteceu:

| Marco | Definição e evidência necessária | O que não basta |
| --- | --- | --- |
| Início da possível lacuna | Primeiro instante comprovado em que a candidata deixou de capturar enquanto ainda podiam ocorrer efeitos públicos; relacionar aos logs/identidades do runtime e à operação. Se o início exato for desconhecido, conservar a última captura comprovada e a primeira observação sem capturador como limites, marcando a incerteza. | Horário da decisão humana, início do smoke de recuperação ou troca de `current` isolada. |
| Primeira disponibilidade de b68 | Primeira observação da identidade física b68 e de suas respostas, ligada ao UUID operacional e aos resultados de probes. | Só a tag da imagem ou só health `UP`. |
| Retiradas e outras mudanças durante o intervalo | Instantes efetivos registrados nas trilhas existentes, sujeitos/atores/request IDs e resultados observados, com a precisão e o alcance originais. | Presumir ausência de operações porque o operador não acionou a interface. |
| Fim da lacuna para novas capturas | Evidência de candidata ativa com captura prospectiva funcional e avaliação das pendências do intervalo concluída. O material disponível nesta revisão não estabelece esse marco para produção. | `ROLLED_BACK`, `RECONCILED`, bootstrap concluído ou readiness isolados. |

O intervalo é distinto da janela de 300 segundos do helper. Pode começar antes de b68 responder e continuar depois de uma primeira resposta saudável da candidata. Sem evidência do último marco, o intervalo deve permanecer aberto ou com fim desconhecido; não se atribui uma hora por conveniência.

## Operações descobertas durante a permanência em b68

Os pontos de captura da candidata demonstram mudanças que a revisão anterior executa sem seus registros novos: publicação/aprovação/regularização e reativação de anúncio; edição de conteúdo e identidade; upload/moderação/reclassificação/reordenação que afete a peça pública; ativação, compra, renovação ou cancelamento de benefícios; publicação e retomada de Story; desbloqueios e outras transições que restabeleçam elegibilidade. Há também encerramentos por pausa, retirada, exclusão ou bloqueio que b68 não propaga para as tabelas do arquivo.

Não foi localizado modo versionado em b68, no helper ou na configuração examinada que suspenda somente as operações dependentes de novas capturas. Deixar de usar botões administrativos não contém os demais usuários ou caminhos da API. Revogar `ANUNCIO_MODERAR` também retiraria a autorização da remoção lógica/bloqueio, enquanto mantê-la permite reativação/desbloqueio: a permissão existente não separa essas finalidades.

Há mudanças sem comando mutante HTTP: elegibilidade dos benefícios é calculada para o instante consultado; o feed de Story filtra `inicioEm`/`fimEm` e a seleção administrativa por validade. Expiração de fotos extras/vídeo pode mudar a seleção pública, e Story `ANUNCIO` pode acompanhar essa seleção; `MIDIA_UPLOAD` tem sua própria janela. Um gateway que filtrasse verbos ou a suspensão de um scheduler, além de não ser uma solução existente comprovada aqui, não congelaria esses relógios. O prazo contratual também não pode ser prolongado para ocultar a lacuna.

**Consequência:** não existe, entre as capacidades comprovadas, uma etapa executável que cumpra simultaneamente “conter todas as novas mudanças abrangidas”, “continuar servindo o estado elegível” e “manter retiradas urgentes”. Esse é um bloqueio do procedimento completo; não se apresenta promessa operacional como controle técnico disponível.

## Retiradas urgentes e evidências contemporâneas

As rotas existentes em b68 incluem `POST /api/admin/anuncios/{id}/remocao-logica`, `POST /api/admin/anuncios/{id}/bloqueio-juridico` e `POST /api/admin/anuncios/{id}/bloqueio-juridico/usuario`. Elas exigem sessão e `ADMIN` com `ANUNCIO_MODERAR`; CSRF continua aplicável no ambiente protegido. A escolha depende do caso e das regras já presentes no serviço; não é uma nova API de recuperação. Não se fornece comando HTTP genérico que omita motivo, autorização ou contexto jurídico.

A remoção lógica registra status anterior/novo, motivo, ator, request ID, instante e resultados do cleanup em `anuncio_status_historico`/`auditoria_evento`. O serviço tem caminhos de falha de cleanup anteriores à mudança de status; portanto a disponibilidade da rota não significa êxito universal. A fixture prova somente seu caso sintético aprovado. Rejeição ou falha devem conservar seu resultado original; resposta de erro não comprova retirada concluída.

Retirada urgente não pode esperar que surja uma captura histórica inexistente. Também não se deve desligar globalmente a aplicação, fechar todos os verbos mutantes ou remover a permissão necessária e tratar isso como solução deste procedimento. Remoção parcial de mídia merece atenção adicional: a seleção dos sobreviventes pode promover uma peça antes fora do limite; a supressão baseada no arquivo implementada na candidata não existe em b68. A prova de retirada lógica do anúncio inteiro não cobre esse cenário.

Conservar as evidências **já existentes**: UUID e journal da operação, logs de runtime/probes com request IDs e horários, históricos de status, decisões de moderação/bloqueio, registros de Story/benefício, linhas/holds/referências/planos e objetos privados já confirmados. Correlacionar por IDs e fontes sem copiar credenciais, documentos KYC ou conteúdo privado para relatório público. Este documento não manda produzir novo backup, snapshot, objeto ou cópia, nem realizar teste R2.

Registrar documentalmente o alcance de cada fonte: auditoria de ação não substitui bytes da peça; prazo de benefício/Story não prova uma sondagem independente do último segundo de exibição; resultado HTTP observado não prova recebimento por todos os usuários ou invalidação de todos os caches. Uma janela que permanece aberta no arquivo sob b68 não comprova que a peça continuou pública após sua retirada operacional. Se os dados disponíveis forem insuficientes, preservar a lacuna como desconhecida, sem preenchê-la a partir do estado atual.

## Recuperação mecânica disponível, condicionada ao journal

Esta seção descreve a interface já implementada. **Não é autorização de execução e não supera o bloqueio operacional acima.** Um rollback manual só corresponde ao cenário b68/V057 se a operação existente identificar a candidata esperada e `previous=b68a6c7063f6255d3d5a451421449bee4f435024`, com baseline completo e imagens físicas preservadas. Sem esse journal, o helper não fornece comando de rollback arbitrário para b68.

1. Identificar a raiz efetiva, o helper da release verificada e o UUID de `operations/active.state`. Conferir estado/snapshots existentes, release anterior, imagens/configuração, PostgreSQL, pendências e erro original. Não apagar journal ou `preview-backfill.pending`, fabricar UUID, retaggear imagens ou reconstruir baseline para satisfazer a entrada.
2. A confirmação `--confirm-daemon-quiescent` exige apuração real da ausência de mutação concorrente do Docker; ausência de PID/CLI não basta. Se proprietário/filho continuam vivos, o mutex está ocupado ou há ambiguidade não resolvida, o helper recusa ou conserva `INCOMPLETE`. Não há confirmação automática derivada do tempo decorrido.
3. Somente numa recuperação futura autorizada, com essas precondições comprovadas, a interface existente é a seguinte. Os valores entre `<...>` são placeholders, não caminhos/UUIDs reais:

   ```bash
   bash '<RELEASE_DO_HELPER_VERIFICADO>/scripts/deploy/proteger-operacao-production.sh' rollback '<ROOT_VERIFICADO>' '<OPERATION_ID_DO_ACTIVE_STATE>' --confirm-daemon-quiescent
   ```

4. O helper verifica os artefatos capturados, restaura `production.env` do baseline existente, recria backend/frontend/gateway individualmente sem build/pull, verifica identidade, troca `current`, marca restauração física e executa o smoke. Não executa `down`, restore do banco ou remoção das tabelas/objetos privados. Preservar falha original, resultado e limites do smoke. `ROLLED_BACK` é resultado operacional do helper, não certificação do arquivo.
5. Se a restauração física já foi confirmada, o helper revalida esse mesmo estado antes de observar; não se deve repetir comandos de recriação por fora. A interface de reconciliação abaixo apenas confirma uma identidade **já ativa** admitida pelo journal, escrevendo seu resultado operacional:

   ```bash
   bash '<RELEASE_DO_HELPER_VERIFICADO>/scripts/deploy/proteger-operacao-production.sh' reconcile '<ROOT_VERIFICADO>' '<OPERATION_ID_DO_ACTIVE_STATE>' '<EXPECTED_SHA_JA_ATIVO>' --confirm-daemon-quiescent
   ```

Nenhum dos comandos acima foi executado. A imutabilidade de V054/V055, das migrations aditivas e do histórico Flyway permanece; rollback da aplicação não é permissão para reparar checksums, reverter schema, restaurar backup antigo ou apagar evidências posteriores. O helper verifica identidade/saúde do PostgreSQL, mas não comprova sozinho a invariância de todas as linhas ou bytes do arquivo.

## Retomada da candidata: limite que impede encerrar o procedimento

`reconcile` não ativa a candidata. O ativador versionado é o workflow manual `deploy-production.yml`, com ref/SHA explícitos, seus gates, build, migrações/validação, smoke, promoção e journal. Nenhuma execução de workflow, CI, push ou deploy integra esta revisão documental.

Na candidata, `ArquivoPublicidadeTransicaoTemporalService` possui `ApplicationRunner` e scheduler periódico. O bootstrap seleciona fontes com `transicoes_reconciliadas_em IS NULL`; não realiza uma varredura universal de mudanças ocorridas sob b68. O scheduler processa planos `PENDENTE` vencidos. `processarPlano` verifica o fim da janela **arquivada**, a versão-fonte/hash e a cadeia de versões do arquivo, e materializa a projeção congelada com `fronteiraEm`/`processadoEm` distintos. Não consulta a auditoria da retirada sob b68 para corrigir automaticamente seu significado.

Assim, se b68 retirar uma peça antes de uma fronteira pendente sem encerrar sua janela arquivada, essas verificações podem continuar satisfeitas. Retomar o processo normal não comprova que o plano ainda descreve o intervalo público real. Isso é uma inferência direta do fluxo de código e da invariância observada na fixture, não uma nova reprodução executada nesta revisão.

As fontes contemporâneas precisariam permitir distinguir, por sujeito/janela/plano, uma expiração ainda derivável da evidência congelada de uma retirada/edição/cancelamento que invalide essa derivação. Mudança não capturada, encerramento sem vínculo suficiente, promoção de mídia ou período perdido permanecem lacunas. Não foi encontrado comando existente que faça essa apuração e seu tratamento antes do bootstrap/scheduler, nem modo comprovado que permita iniciar a candidata com tal apuração concluída sem processar pendências. Aumentar `app.arquivo.publicidade.transicoes.poll-delay-ms` não resolve: é intervalo do scheduler, não supressão do runner ou reconciliação do intervalo.

Por isso não há passo executável de “retomar e encerrar a lacuna” a oferecer com os controles atuais. Não registrar versões retroativas a partir de anúncio/mídia/identidade atuais; não converter uma observação feita na retomada em captura do período b68; não estender janelas antigas para encobrir pausas; não cancelar/processar planos por SQL improvisado. A retomada operacional completa permanece bloqueada, com as evidências existentes preservadas e os limites documentados.

## Rastreabilidade do código consultado

Referências de linha relativas à revisão identificada no início; os caminhos abaixo partem da raiz do repositório:

| Fonte | Linhas verificadas e conclusão |
| --- | --- |
| [`scripts/deploy/proteger-operacao-production.sh`](../../scripts/deploy/proteger-operacao-production.sh) | 89–155: journal/mutex/pendência; 242–295: baseline e identidade; 363–459: smoke e janela de 300 s; 522–618: início/supervisão/ambiguidade; 632–691: restauração física; 746–821: interfaces `rollback`/`reconcile` e precondições. |
| [`.github/workflows/deploy-production.yml`](../../.github/workflows/deploy-production.yml) | 4: disparo manual; 448–449: helper/journal; 564–589: gates de banco/Flyway; 591–655: validação de previews, ativação, smoke, promoção e conclusão. |
| [`scripts/deploy/coordenar-transicao-previews-production.sh`](../../scripts/deploy/coordenar-transicao-previews-production.sh) | 25–69: validação e cleanup; 45: ausência de drenagem/parada nesse coordenador. |
| [`deploy/production/docker-compose.yml`](../../deploy/production/docker-compose.yml) | 105–110: encerramento gracioso/executores; 131–146: configuração de storage; 266–267: configuração externa do gateway. Não contém contenção seletiva do arquivo. |
| [`AdminAnuncioRemocaoController.java`](../../backend/src/main/java/br/com/topsdojob/v3/web/admin/anuncio/AdminAnuncioRemocaoController.java) e [`AdminAnuncioJuridicoController.java`](../../backend/src/main/java/br/com/topsdojob/v3/web/admin/anuncio/AdminAnuncioJuridicoController.java) | Remoção 21–38; jurídico 22–82: rotas e autorização. Os mesmos controllers foram conferidos em b68. Reativar/desbloquear e retirar/bloquear compartilham `ANUNCIO_MODERAR`. |
| [`AdminAnuncioRemocaoService.java`](../../backend/src/main/java/br/com/topsdojob/v3/application/admin/anuncio/AdminAnuncioRemocaoService.java) | No código **b68**, 58–115: validação/cleanup/transição; 117–169: auditoria. A linha 112 da candidata acrescenta captura, ausente no serviço anterior. |
| [`SecurityConfig.java`](../../backend/src/main/java/br/com/topsdojob/v3/security/config/SecurityConfig.java) | 29–53: CSRF desabilitado apenas em `local`; não se aplica essa exceção ao rollback protegido. |
| [`MeuAnuncioAtualizacaoService.java`](../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MeuAnuncioAtualizacaoService.java), [`MinhasMidiasService.java`](../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MinhasMidiasService.java) e [`MinhaContaStoriesPublicacaoService.java`](../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MinhaContaStoriesPublicacaoService.java) | Respectivamente 180, 167/212–226 e 247/318: captura de edição/mídia/Story na candidata. |
| [`AdminPremiumOperacaoService.java`](../../backend/src/main/java/br/com/topsdojob/v3/application/admin/premium/AdminPremiumOperacaoService.java) e [`MinhaContaPremiumService.java`](../../backend/src/main/java/br/com/topsdojob/v3/application/publico/premium/MinhaContaPremiumService.java) | 205/313/391 e 240: captura de ativação/cancelamento/compra de benefício. |
| [`StoryFeedPublicoService.java`](../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/StoryFeedPublicoService.java) e [`BeneficioAnuncioConsultaService.java`](../../backend/src/main/java/br/com/topsdojob/v3/application/admin/premium/BeneficioAnuncioConsultaService.java) | Story 216–219/602–609: seleção por relógio; benefícios 60–114: cálculo no instante consultado. |
| [`ArquivoPublicidadeTransicaoTemporalService.java`](../../backend/src/main/java/br/com/topsdojob/v3/application/arquivo/ArquivoPublicidadeTransicaoTemporalService.java) | 112–175: runner/fontes legadas; 178–205: aviso de lacuna sem reconstrução; 211–231: scheduler; 565–700: processamento sobre janelas/fontes/planos arquivados. Não há tratamento do intervalo b68. |

A busca local incluiu os helpers de deploy, workflow, Compose, configuração Spring e código anterior; não substitui inspeção autorizada da configuração externa. Esta entrega adiciona somente este documento, sem alteração de código, migrations, configurações, mestre SEO ou evidências preexistentes.
