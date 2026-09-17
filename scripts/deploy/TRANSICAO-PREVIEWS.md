# Histórico substituído

A preparação global descrita no histórico abaixo não é requisito da rota atual. A integração LOCAL usa VALIDATE fresco da imagem física candidata e gate público selecionado antes de ACTIVATING, sem drenagem global nem APPLY automático. O APPLY permanece independente e explicitamente confirmado, com os guards já revisados. Os gates de CI, as esperas do Compose e a recuperação existente de 300 segundos foram preservados. Não executar o roteiro histórico: o procedimento atual está em `scripts/deploy/TRANSICAO-PREVIEWS.md`. Esta entrega é para auditoria; nenhuma execução produtiva foi autorizada.

## Interface independente proposta — somente para revisão

**Não executar agora.** O helper está separado do deploy e a integração local foi adequada sem remover gates ou esperas do Compose. A futura candidata deve conter o diff auditado e passar os gates de publicação; o HEAD histórico `3748` não contém estas alterações locais. A imagem precisa já existir e seu ID físico deve vir da evidência da candidata avaliada, não ser inferido de uma tag no momento do APPLY. Não há build/pull no helper.

Depois de autorização produtiva específica e conferências frescas de release, configuração, imagem e exclusividade, executar no host autorizado, fora de uma operação de deploy:

```bash
set -euo pipefail
umask 077
: "${CANDIDATE_SHA:?Informe o SHA completo da futura candidata auditada}"
: "${EVALUATED_BACKEND_IMAGE_ID:?Informe o sha256:ID fisico registrado na avaliacao}"
[[ "$CANDIDATE_SHA" =~ ^[a-f0-9]{40}$ ]]
[[ "$EVALUATED_BACKEND_IMAGE_ID" =~ ^sha256:[a-f0-9]{64}$ ]]
test "${OP_ACTIVE:-0}" -eq 0
DEPLOY_ROOT=/opt/topsv3/production
RELEASE_DIR="$DEPLOY_ROOT/releases/$CANDIDATE_SHA"
ENV_FILE=/opt/topsv3/secrets/production.env
HELPER="$RELEASE_DIR/scripts/deploy/executar-backfill-previews-production.sh"
test "$(cat "$RELEASE_DIR/.release-sha")" = "$CANDIDATE_SHA"
REPORT_ROOT="$(mktemp -d "$DEPLOY_ROOT/operations/preview-review-XXXXXXXX")"

# PLAN fresco: SELECT/LIST; não altera metadados nem objetos.
env TOPSV3_PREVIEW_BACKFILL_IMAGE_ID="$EVALUATED_BACKEND_IMAGE_ID" \
  bash "$HELPER" PLAN initial "$CANDIDATE_SHA" "$RELEASE_DIR" "$ENV_FILE" \
  topsv3-production topsv3-production topsv3-production-net "$REPORT_ROOT/plan"

# Conferir o PLAN antes desta chamada única e explicitamente confirmada.
# APPLY faz seu próprio PLAN fresco; o PLAN anterior/histórico não é replayado.
env TOPSV3_PREVIEW_BACKFILL_IMAGE_ID="$EVALUATED_BACKEND_IMAGE_ID" \
  TOPSV3_PREVIEW_BACKFILL_CONFIRM="APPLY:$CANDIDATE_SHA" \
  bash "$HELPER" APPLY delta "$CANDIDATE_SHA" "$RELEASE_DIR" "$ENV_FILE" \
  topsv3-production topsv3-production topsv3-production-net "$REPORT_ROOT/apply"

# VALIDATE fresco: somente leitura; não é um APPLY nem corrige novo delta.
env TOPSV3_PREVIEW_BACKFILL_IMAGE_ID="$EVALUATED_BACKEND_IMAGE_ID" \
  bash "$HELPER" VALIDATE final "$CANDIDATE_SHA" "$RELEASE_DIR" "$ENV_FILE" \
  topsv3-production topsv3-production topsv3-production-net "$REPORT_ROOT/validate"
```

APPLY permite somente os cinco campos de preview dos alvos `DESCONHECIDO + quatro NULL`, com identidade do PLAN, locks/rechecagem de arquivos e todos os vínculos e rollback integral. Não há PUT/DELETE/regeneração nem mudança de elegibilidade. O arquivo `.before.json` e o journal são privados; não enviar a artifacts públicos. Cada chamada cria um job efêmero sem reinício automático, pinado fisicamente, e adquire o mesmo `deploy.lock` de ativação/recuperação. Jobs preexistentes ou journal de deploy não terminal bloqueiam a chamada.

Em PLAN/APPLY/VALIDATE, o override privado fixa também `user` no UID/GID efetivo do executor do helper, obtido no host, sem assumir UID 1000 nem herdar root da imagem. O pin confere a configuração Compose resolvida antes do job; a observação/cleanup reconfirma o usuário do mesmo container e do executor. A captura anterior permanece em `0600` e seu proprietário/grupo e hash precisam ser comprovados pelo observador sem privilégios adicionais. Não usar chmod/chown ou leitura privilegiada para corrigir uma captura já escrita. O usuário dos serviços permanentes e as permissões dos arquivos de configuração/segredos não são alterados; a fixture local deve comprovar que o job consegue iniciar e ler sua configuração com essa identidade.

Antes de iniciar o job, o helper persiste `operations/preview-backfill.pending`. Somente sucesso completo, prova terminal e cleanup com ownership liberam esse bloqueio. Falha conserva o erro original, o container e a pendência; `outcome.json` registra `COMMITTED`, `ROLLED_BACK`, `UNKNOWN` ou `NOT_STARTED`, sempre sem autorizar retry. Em erro após commit/observação incerta, não repetir APPLY, trocar diretório para tentar novamente, apagar o marcador ou restaurar banco: apurar manualmente ID/estado do job, journal, captura anterior e estado atual, sob autorização própria. PLAN/VALIDATE não liberam essa pendência automaticamente.

## Publicação: validação fresca, nunca regularização automática

Antes do build frontend e do upload, `Require successful main CI for deploy SHA` executa `python3 ./scripts/deploy/validar-ci-main-production.py "${DEPLOY_SHA}"`. O gate reutiliza somente o Maven backend integral do workflow confiável `ci.yml` deste repositório, em evento `push` da `main`, no SHA exato solicitado. Confere conclusão dos jobs/steps necessários e seus uploads, identidade/validade dos artefatos, configuração e cobertura dos relatórios Surefire existentes. SHA/evento divergente, execução incompleta, skip obrigatório, configuração/cobertura ausente ou evidência insuficiente bloqueiam a publicação; não há fallback para CI antigo nem execução automática de uma suíte substituta.

Somente a repetição de Maven sai do deploy. A preparação das imagens, o contrato PowerShell, todas as suítes operacionais sintéticas e as dez execuções de backup de teste continuam no deploy, com os mesmos cenários, assertions, cleanup e prazos. O backup/restauração de validação para migrations produtivas pendentes permanece inalterado. A build frontend pública e seus gates também permanecem porque usam configuração diferente da build bloqueada do CI.

A prova Maven reutilizada não atesta o estado atual da produção: identidade/configuração, PostgreSQL/volume, migrations, previews, ativação, saúde, smoke e recuperação permanecem frescos e obrigatórios no procedimento abaixo. Não executar APPLY/backfill para fazer um gate passar.

A regressão de ownership é executada no host Linux não root do CI por `RestrictedMediaPreviewBackfillApplyPostgres17IntegrationTest#runnerRealComObservadorNaoRootPreservaCapturaEConcluiApply`, com `PREVIEW_BACKFILL_POSTGRES17_ENABLED=true` e `PREVIEW_JOB_USER_INTEGRATION_ENABLED=true`. Reutiliza PostgreSQL/seed da fixture, JVM real e inventário sintético somente leitura. O script `testar-backfill-previews-job-user-integrado.sh` integra as funções reais de execução/observação/cleanup do helper com o mutex numa árvore temporária própria; não substitui nem relaxa os prechecks da entrada canônica, cobertos pela suíte isolada existente. Não usa sudo, observador root ou socket do host em containers. Confere dois updates, configuração privada, captura JVM0600/hash/terminal, cleanup, identidade divergente e regressão do observador exato ed57 (captura root ilegível, COMMITTED no journal, UNKNOWN observado e pendência preservada). O teardown dessa regressão remove apenas recursos sintéticos próprios, não autoriza retry produtivo. O CI publica somente `evidence/`; configurações, inventário e capturas ficam privados e são removidos após todas as assertions, sem chmod/chown.

Depois dos prechecks, migrations/gates existentes e build supervisionado, `op_expect_candidate` captura as identidades físicas. `op_preview_validate_before_activation` executa somente VALIDATE/final pelo runner existente e depois o SQL público selecionado. O gate revalida ownership, imagem, término, journal e outcome do mesmo job; metadados ausentes/inconsistentes, UNKNOWN ou erro impedem ACTIVATING. A limpeza exige prova de job VALIDATE próprio e terminal (inclusive exit não zero) ou ausência verificada independentemente; falha de inspect isolada não prova ausência. Nenhum serviço de origem é parado por esse coordenador.

O cleanup preserva o erro original e nunca transforma reprovação em PASS. Identidade/término desconhecidos ou falha de limpeza mantêm a ambiguidade no journal e exigem apuração pelo procedimento existente. A ambiguidade já registrada pelo supervisor não é apagada apenas porque a limpeza terminou. A recuperação e sua janela de 300 segundos não foram alteradas; não há restauração automática de banco. `preview-backfill.pending` continua bloqueando operações incompatíveis, sem remoção pelo deploy.

Na ativação e na recuperação, backend, frontend e gateway são iniciados nessa ordem por chamadas individuais `compose up -d --no-deps --force-recreate --no-build --pull never`, cada uma supervisionada por `op_run`. Assim, o gateway pode encaminhar o callback durante o registro do webhook na inicialização do backend. Um `up` conjunto ainda aguardaria as dependências saudáveis entre os serviços selecionados. O Compose capturado, seus healthchecks e as imagens permanecem intactos, inclusive na recuperação de releases antigas. Partida não significa aprovação: o `op_smoke` e as verificações finais existentes continuam obrigatórios, com a mesma janela de recuperação de 300 segundos. Pix, registro automático, mTLS e autenticação não são alterados.

A fixture Bash existente agora se autoisola. Nos workflows, a preparação explícita acrescenta `python:3.12-slim-bookworm` e registra seu ID físico em `TOPSV3_BACKFILL_FIXTURE_IMAGE_ID`; o lançador não faz pull/build. Localmente, reutilizar uma imagem já disponível com Bash/Python/flock/coreutils e informar seu ID avaliado. O filho exige rede none, ausência de docker.sock, rootfs/source RO, `/tmp` em tmpfs executável e `/opt/topsv3` em tmpfs noexec; roda sem capabilities e sem adquirir novos privilégios. Exemplo LOCAL já validado (não é comando produtivo):

```bash
TOPSV3_BACKFILL_FIXTURE_IMAGE_ID=sha256:0fa86fa9b8a28f41ced37ffaa6b87fe9424194ad82828ed5063fa825d39ef84c \
  bash scripts/deploy/testar-backfill-previews-production.sh
```

O CI conserva todos os gates, inclusive a regressão isolada do observador da origem 451. A fixture de recuperação executa o corpo integral do workflow, com fronteiras de VALIDATE/SQL explicitamente sintéticas, sem remover linhas do gate ou alterar seus prazos. Isso não equivale a ensaio produtivo/Spring nem dispensa os gates completos da futura publicação.

Ainda faltam auditoria final e gates obrigatórios sobre a futura candidata congelada. A origem pode criar novos registros sem metadados entre as chamadas; qualquer delta/reprovação fresca bloqueia promoção, não justifica ignorar o gate nem repetir APPLY automaticamente. As observações frescas não são um congelamento global dos dados.

---

# Histórico substituído — não executar a sequência abaixo

O workflow de publicação continua sendo o único ativador. **Esta entrega é para revisão, sem autorização de novo deploy.** A origem `451a6cb9` inspecionada não possui shutdown gracioso comprovado e deve ser recusada antes de qualquer alteração de configuração ou serviços. É necessária preparação e revisão adicional do controle dessa origem; não substituir a prova por gateway fechado, SQL ocioso, parada forçada ou uma variável de confirmação.

Para uma origem cujo controle seja comprovado, o caminho é:

1. Gates longos; snapshot, journal e mutex existentes; preflight dos valores efetivos e identidades, antes de IndexNow, Flyway ou serviços. Exige Java direto, argumentos conhecidos, shutdown gracioso de 120 s e espera de 120 s tanto do scheduler quanto do `applicationTaskExecutor`, sem overrides de maior prioridade. Todos os containers em execução são inspecionados; produtores na rede, banco ou imagem pertinente e processos não atribuídos bloqueiam.
2. Build das imagens com a origem atendendo. Repetir preflight e registrar identidade física da origem.
3. `op_run` para o Docker real, `stop --time=-1`: sem fallback para SIGKILL. O limite externo de 150 s interrompe apenas a espera do cliente; timeout ou falha do Docker deixa o daemon potencialmente ativo e o journal INCOMPLETE, sem recuperação automática concorrente.
4. Provar mesmo container, início e imagem, término natural sem OOM ou restart, marcador de graceful completo apenas no corte temporal dessa parada, sem abort ou timeout do executor. SQL READ ONLY com `statement_timeout=5s`, `lock_timeout=1s` e ROLLBACK complementa a prova exigindo nenhuma sessão residual; não a substitui.
5. PLAN/delta, APPLY/delta e VALIDATE/final, cada qual com diretório novo dentro do UUID operacional. Antes de cada job, override privado fixa `image` no ID físico de `candidate.images.tsv`, remove `build` com `!reset` e define `pull_policy: never`; a configuração efetiva é verificada em memória antes da execução. Compose sem suporte a esse contrato bloqueia a execução. O helper é carregado no mesmo shell e delega o comando Docker real (`--pull never`, sem `--build`) a `op_run`; não instala outro trap de EXIT. APPLY isolado é recusado.
6. Conservar containers terminais até comprovar ID, imagem, label, exit, journal completo e vínculo SHA256 ao snapshot privado `.before.json`. Falha após commit conserva o estado real; nenhuma etapa repete APPLY ou restaura banco automaticamente. Os recibos não apagam ambiguidade do Docker.
7. Gate SQL integral de previews públicos selecionados: ordem Java UUID, limite 4/10 antes do filtro, benefícios, elegibilidade, preferência LIVRE no card, identidade, tipo, pipeline, status, confirmação e base. Exige totais explícitos coerentes, zero recusas e sentinela de transporte completo. Revalidar ausência dos produtores antigos antes da ativação; limpar somente jobs próprios terminais comprovados.
8. Ativação, smoke, current, finish e recuperação seguem o helper existente. Falha posterior à parada da origem usa os snapshots comuns; nenhuma restauração do banco. Erro original, vigia, mutex, grupos, `restoration.complete` e janela funcional de 300 s permanecem intactos.

Os sidecars privados anteriores ao APPLY contêm identificadores e metadados e não devem ser enviados a artifacts ou logs do CI. Relatórios públicos contêm somente contagens, IDs operacionais, imagens e hashes. A indisponibilidade da janela final é explícita; isto não é blue-green nem garantia de zero downtime.

Testes: `testar-transicao-previews-production.py`, `testar-backfill-previews-production.sh`, `testar-coordenador-transicao-previews-production.sh`, `testar-gate-previews-publicos-production.sh`, contratos e suítes operacionais são obrigatórios. Só a configuração da candidata nova é ampliada para aguardar o executor de moderação; isso não altera retroativamente a origem `451a6cb9` em execução.

### Escopos da cobertura operacional

A fixture de containers usa servidores Node sintéticos, não uma aplicação Spring com produtores de previews. O corpo integral extraído do workflow, com coordenador e runtime reais, deve abortar antes de qualquer mutação porque essa origem não satisfaz o contrato. O teste conserva o journal negativo e verifica ausência de recibo de aprovação, preservação de imagem, configuração, release, PostgreSQL e identidade/estado dos serviços. A inspeção real do Node também é recusada pelo predicado de entrypoint; somente o nome do namespace privado é normalizado nessa prova específica.

Os 11 cenários de ativação/recuperação exercitam o helper central e containers reais numa projeção explicitamente limitada a esse escopo. A projeção exclui somente as três linhas exatas dos hooks de previews, exige ocorrência única e ordem conhecida e preserva todos os demais bytes. Não redefine os gates como sucesso. As assertions e os prazos reais de 32, 183 e 300 segundos permanecem. Isso não é um ensaio positivo integral da transição de previews em produção.

O teste focal do coordenador carrega sua implementação real e declara as fronteiras sintéticas de observação/execução; verifica a sequência e a interrupção diante de falhas sem afirmar que uma origem real foi drenada. As provas complementares do runtime, do journal, do backfill Java com PostgreSQL, do predicado público 4/10 e da supervisão de timeout/recuperação continuam separadas e obrigatórias. Essa composição não dispensa o preflight efetivo da origem antes de qualquer futura publicação.
