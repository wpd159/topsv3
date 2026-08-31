# Incidente - Pipeline Hermetico V2

## Estado

- Status: `OPEN`.
- Data desta consolidacao: 2026-08-30.
- Fonte Git canonica: `C:\topsdojob`.
- Fonte historica nao autorizada para novas mudancas: `C:\topsv3` e suas worktrees.
- Producao preservada: `4e72be6c7fa0b790230e4ed416497c24843f3dcd`.
- PR da Fase 1: #24.
- HEAD tecnico aprovado: `fbb934ec576113711580a619c774e87f2b95e0cf`.
- Estado Git canonico pos-merge: `9bc083dadeba09f7de9c4d48f94810f7fd6df28f`.
- CI pos-merge: `33324582326` (`PASS`).

## Ocorrencia

O pipeline anterior acumulou premissas nao garantidas sobre imagens, cache, ferramentas, permissoes, stdin SSH, Docker Compose e bootstrap da aplicacao. Os harnesses parciais nao constituiam prova fim a fim de deploy, portanto novos merges e deploys foram bloqueados ate a construcao de uma cadeia hermetica verificavel.

O PR #24 implementa somente a Fase 1. Ele constroi backend, frontend e gateway uma unica vez, certifica o conteudo por manifesto e executa o mesmo artefato em runners limpos. O workflow aceita apenas `mode=verify` e nao possui caminho mutavel para producao.

## Evidencias da Fase 1

- Run diagnostico: `33296880586`, aprovado com artefato certificado do mesmo run.
- Run canonico dos runners limpos: `33297674253`.
- Runners limpos: 3/3 aprovados.
- Candidate gates sinteticos: 14/14 em cada runner.
- Testes criticos ignorados: `criticalSkipped=0`.
- Integridade da fixture: ao menos um anuncio publico canonico valido, com localizacao e documento de busca; contadores invalidos em zero.
- Respostas do laboratorio: backend direto, frontend direto, gateway Home e gateway listagem em HTTP 200; HTTP 500/502/504 em zero.
- Manifesto: `manifest-verify` aprovado, sem rebuild nos runners de verificacao.
- Acesso produtivo: zero.

## Classificacao dos relatorios anteriores

Os diagnosticos produzidos antes do fechamento da Fase 1 permanecem preservados como linha do tempo forense pre-Fase 1. Eles nao podem ser usados como retrato operacional atual nem substituir este registro canonico.

Estao superados pela evidencia pos-merge: o bloqueio de `ubuntu:24.04`, a ausencia de tres runners limpos, a falta de execucao dos 14 gates, `criticalSkipped` diferente de zero e a ausencia do Pipeline V2. As falhas e tentativas que levaram a essas correcoes nao foram apagadas.

## Limites da evidencia

A Fase 1 prova a hermeticidade do laboratorio e a repetibilidade do fluxo `verify`. Ela nao prova candidata real, capacidade da VPS, target guard operacional, backup/restore produtivo, switch Nginx, drenagem ou rollback em producao.

O artefato produzido para o PR #24 e efemero e nao pode ser reutilizado em candidata, switch ou deploy posterior. Cada fase futura deve gerar e certificar seu proprio artefato a partir do SHA expressamente autorizado.

## Fase 2 implementada, nao executada

O modo `candidate` foi implementado em branch propria para exigir SHA e run de certificacao exatos, validar manifesto/hashes/digests, executar target guard antes do upload, restaurar uma copia read-only do banco em PostgreSQL 17 isolado, usar somente servicos externos locais, executar os mesmos 14 gates e remover a candidata graciosamente.

Essa implementacao ainda nao havia sido executada contra a VPS e nao constituia evidencia de candidata real. `switch` e deploy completo continuam bloqueados. Uma execucao `candidate` exige autorizacao separada; somente depois de sua evidencia aprovada podera existir uma autorizacao independente para a Fase 3, cobrindo switch, smoke, drenagem e rollback.

## Ocorrencia de layout do artifact na Fase 2

O `verify` `33341100028` certificou corretamente o SHA `b23c323ccf6b26d6cc77c28ca20a207bd9f2c83c` e publicou o artifact `9740656605`. No run `candidate` `33342261801`, a action oficial baixou esse artifact e extraiu `release-manifest.json` diretamente no diretorio de destino. O workflow, entretanto, procurava exclusivamente na profundidade 2 e encerrou ao obter zero resultados.

A falha ocorreu antes do target guard, do SSH e de qualquer acesso a VPS. Nao houve candidata, banco isolado, switch, deploy ou alteracao da producao. O artifact, seu manifesto e a certificacao nao foram a causa.

O contrato corretivo elimina profundidade fixa: um resolvedor unico ancora a raiz no unico manifesto, exige `artifactLayoutVersion=1`, valida arquivos regulares, `realpath`, ausencia de symlinks e traversal, perfis nominais sem extras, checksums, hashes e payloads. O candidate seleciona o artifact certificado por ID e exige digest valido antes do target guard. Um workflow pequeno faz round-trip real entre as mesmas actions pinadas de upload e download em jobs diferentes.

Como workflow, controlador e manifesto mudaram, o artifact `9740656605` e o run `33341100028` nao podem ser promovidos. Depois do merge corretivo, a sequencia minima volta a ser novo `verify`, novo artifact do SHA resultante e nova execucao `candidate` sem switch, cada uma com autorizacao propria.

## Criterio de encerramento

Este incidente permanece `OPEN` ate que as fases operacionais autorizadas sejam concluidas, as evidencias de candidata e switch sejam registradas e a producao esteja estavel sob o novo pipeline. A aprovacao da Fase 1 ou o merge do PR #24 nao encerram o incidente.

## Rastreabilidade

- [SDD central](SDD.md)
- [Decisoes consolidadas](SDD-decisoes-consolidadas.md)
- [Pendencias e gates](SDD-pendencias-gates.md)
- [Indice de rastreabilidade](SDD-indice-rastreabilidade.md)
