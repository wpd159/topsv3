# Histórico substituído

A preparação global descrita no histórico abaixo não é requisito da rota atual. A integração LOCAL usa VALIDATE fresco da imagem física candidata e gate público selecionado antes de ACTIVATING, sem drenagem global nem APPLY automático. O APPLY permanece independente e explicitamente confirmado, com os guards já revisados. Os gates de CI, as esperas do Compose e a recuperação existente de 300 segundos foram preservados. Não executar o roteiro histórico: o procedimento atual está em `scripts/deploy/TRANSICAO-PREVIEWS.md`. Esta entrega é para auditoria; nenhuma execução produtiva foi autorizada.

---

# PR35: primeira preparação da origem 451 ainda pendente

Escopo: revisão local de logs, cleanup de testes e capacidade de transição.
Não é autorização nem roteiro executável de publicação. Não executar este
documento contra produção. PR35 permanece em rascunho.

## Identidades e limite das evidências

Origem: `451a6cb90dd1e67c5c774a0618c984d6b73735f8`.
Base do PR: `dd6b082b464c83c81121a8081a88e7eb8aebadc0`.
HEAD antes desta revisão: `0f02c3af201046f7e31fe4806f168e2aa894f87e`.

A observação produtiva reaproveitada, de 11/09/2026 às 01:30:28 UTC, não
encontrou configuração de graceful, espera de executor/scheduler ou overrides
JVM na instância antiga. Não é uma nova consulta produtiva. O PLAN histórico
comprovou 1.358 objetos, incluindo os 12 sem metadados, sem ausentes ou
inconsistentes e sem alterar metadados. Não foi repetido nesta revisão; não
congela o estado posterior do sistema.

O teste `scripts/deploy/testar-origem451-spring-production.sh` usa fonte 451
arquivada, sem beans ou alterações de teste no JAR, PostgreSQL efêmero e
configuração sintética. Ele verifica a recusa do observador real para a origem
sem graceful. Não é um positivo integral de coordenador, backfill e ativação.
O overlay produtivo externo `application-production.yml` não foi copiado para
a fixture. A equivalência é focal: fonte 451 e ausência dos controles de
shutdown observados; não se declara configuração integral idêntica à produção.
Os testes de subprocessos com dois canais comprovam o observador de logs;
os cenários do coordenador com fronteiras sintéticas continuam tendo esse
alcance. A fixture Node que retira explicitamente os três hooks de previews
comprova o núcleo de ativação/recuperação, não drenagem Spring.

## Quem pode produzir ou alterar o universo

Referências abaixo são classes da árvore 451 em `backend/src/main/java/br/com/topsdojob/v3`.

| Entrada | Trabalho que precisa terminar |
| --- | --- |
| Aprovação e reclassificação administrativas (`MidiaStorageAprovacaoService` → `MidiaRestritaDerivacaoService`) | Leitura/processamento de original, criação/comparação de preview, transação e compensações depois do commit/rollback. |
| Moderação em lote (`AdminModeracaoFotosLoteService`, `AdminModeracaoFotosLoteItemService`) | Todas as ondas, futures e fila do `applicationTaskExecutor`, transações `REQUIRES_NEW` e callbacks. Uma falha de auditoria pode propagar enquanto outros futures continuam. |
| Upload proprietário/admin, reordenação, exclusão de mídia/anúncio/usuário | Arquivo, vínculo, ordem/seleção pública, transação e cleanup pós-commit. Não basta observar apenas quem cria o preview. |
| Publicação/encerramento de stories e cleanup associado | Referências de arquivo/vínculo e callbacks; não confundir com geração de preview da galeria. |
| Jobs, fixtures e regularização operacional externos | Processo inteiro. A regularização antiga é de homologação, não scheduler produtivo; perfil/flags e ausência efetiva de jobs precisam ser comprovados. |

Os dois schedulers encontrados na origem são outbox de e-mail e conciliação
EFI. Não foi encontrado caminho deles para gerar preview ou escrever vínculo
de mídia; podem manter trabalho externo e sessões SQL. Os executores de
localidades/readiness são de consulta, não produtores de mídia. Nenhum desses
achados autoriza omitir processos externos do guard.

## Primeira preparação: barreira antes de interromper a origem

A origem 451 expõe health/info, mas não oferece interface observada para fechar
admissão, provar todos os trabalhos em voo/na fila e mudar o shutdown do JVM
já iniciado. Configurar a próxima instância não modifica a antiga. Iniciar
artificialmente uma 451 com graceful não comprova como parar a 451 atual.

Procedimento condicionado para uma futura preparação revisada:

1. Concluir CI, builds, identidade de imagens/configurações e prova da
   recuperação antes da indisponibilidade. Usar a exclusividade e snapshots do
   mecanismo de operação existente, sem outro sistema de deploy.
2. Um mecanismo adicional, antes revisado e testado, deve fechar todas as
   admissões ao JVM existente: gateway, acesso direto/loopback, rede interna e
   jobs. Não cancelar os trabalhos já aceitos; manter banco/storage disponíveis.
3. Antes de qualquer stop/recriação, obter prova autoritativa vinculada ao boot
   antigo de que requisições, futures em execução/na fila, submissões filhas,
   schedulers pertinentes, transações e callbacks terminaram. A admissão deve
   permanecer fechada durante a prova e a parada.
4. **Parar nesta barreira: o mecanismo que comprova a etapa 3 não existe nas
   interfaces observadas da 451. Preparação da origem pendente.** Gateway
   fechado, SQL momentaneamente ocioso, mensagem de sucesso, espera fixa ou
   confirmação textual do operador não substituem essa prova. Não presumir
   JMX/Attach habilitado nem improvisar instrumentação/encerramento de threads.
5. Somente depois de superar a barreira, testar a primeira parada supervisionada
   e a instalação do controle/configuração preparado. Qualquer nova imagem deve
   ter identidade própria. Ainda será exigido ensaio positivo integral com
   trabalho real em voo: fim dos aceitos → ausência de produtores → PLAN delta →
   um APPLY → VALIDATE → contrato público → ativação.

Esta é uma dependência técnica concreta, não um pedido genérico de autorização.
Não foi implementado um controle apenas na próxima imagem como se resolvesse
retroativamente a origem. O PR não está pronto para publicação enquanto faltar
a prova executável do primeiro salto e o positivo integral acima.

## Prazos, indisponibilidade e recuperação

`R2SigV4Client` da origem configura três minutos por request HTTP. Um trabalho
pode combinar GET, PUT, GET de comparação, múltiplas ondas e compensações.
Não há deadline global demonstrado. Os 120 segundos por fase/executor não
comprovam o prazo total; somá-los também não fornece uma garantia.

O teto externo atual de 150 segundos permanece finito e fail-closed, mas sua
suficiência para trabalho legítimo **não está validada**. Não foi aumentado por
estimativa. Timeout do cliente Docker conserva a ambiguidade do daemon e impede
rollback automático concorrente; não significa que a parada terminou. Um futuro
ensaio deverá medir admissão, último trabalho/callback, saída do JVM e início do
APPLY, incluindo trabalho que sobreviva ao HTTP e esgote o teto.

A janela de 300 segundos é distinta: observação funcional após a restauração
física da aplicação, preservada sem alteração. Falha pós-commit não apaga o
journal COMMITTED nem autoriza reaplicar ou restaurar banco. Recuperar apenas
serviços/imagens/configuração/release, com exclusividade e mutadores terminados.

Haverá indisponibilidade na janela final entre fechamento/parada da origem e
saúde/ativação da candidata (ou recuperação). Sua duração total não foi medida
nem é limitada pelos 150 segundos de stop; inclui backfill, validação e startup.
Não prometer zero downtime.

## Fotos preservadas

Nenhuma exclusão, remoderação, substituição, inventário geral de storage ou
APPLY/VALIDATE produtivo foi realizado por esta revisão. Cleanup trata somente
recursos Docker efêmeros próprios dos testes. Recursos históricos sem atribuição
não são removidos e não constituem evidência de perda de fotos produtivas.
