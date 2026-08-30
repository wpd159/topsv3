# Incidente - Pipeline Hermetico V2

## Estado

- Status: `OPEN`.
- Data desta consolidacao: 2026-08-30.
- Fonte Git canonica: `C:\topsdojob`.
- Fonte historica nao autorizada para novas mudancas: `C:\topsv3` e suas worktrees.
- Producao preservada: `4e72be6c7fa0b790230e4ed416497c24843f3dcd`.
- PR da Fase 1: #24.
- HEAD tecnico aprovado: `fbb934ec576113711580a619c774e87f2b95e0cf`.

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

## Limites da evidencia

A Fase 1 prova a hermeticidade do laboratorio e a repetibilidade do fluxo `verify`. Ela nao prova candidata real, capacidade da VPS, target guard operacional, backup/restore produtivo, switch Nginx, drenagem ou rollback em producao.

O artefato produzido para o PR #24 e efemero e nao pode ser reutilizado em candidata, switch ou deploy posterior. Cada fase futura deve gerar e certificar seu proprio artefato a partir do SHA expressamente autorizado.

## Proxima fase

A Fase 2 depende de autorizacao separada e deve validar uma candidata real na VPS canonica sem switch e sem trafego publico. Somente depois de evidencia aprovada podera existir uma autorizacao independente para a Fase 3, cobrindo switch, smoke, drenagem e rollback.

## Criterio de encerramento

Este incidente permanece `OPEN` ate que as fases operacionais autorizadas sejam concluidas, as evidencias de candidata e switch sejam registradas e a producao esteja estavel sob o novo pipeline. A aprovacao da Fase 1 ou o merge do PR #24 nao encerram o incidente.

## Rastreabilidade

- [SDD central](SDD.md)
- [Decisoes consolidadas](SDD-decisoes-consolidadas.md)
- [Pendencias e gates](SDD-pendencias-gates.md)
- [Indice de rastreabilidade](SDD-indice-rastreabilidade.md)
