# Checklist - Bloco 62

## Escopo

- [x] SDD e documentos recentes lidos.
- [x] Escopo mantido no frontend e na documentacao.
- [x] Nenhuma migracao de frontend implementada.
- [x] Nenhum arquivo do clone alterado.
- [x] Nenhum backend, banco, migration, auth/RBAC, restore, VPS ou deploy alterado.

## Auditoria

- [x] `C:\clone\topsdojob-frontend` auditado como fonte visual/funcional de producao.
- [x] Estado Git do clone registrado.
- [x] `C:\topsv3\frontend` auditado como frontend V3 atual.
- [x] Rotas publicas, home, header, cards, listagens, detalhe e wizard mapeados.
- [x] SEO, assets, estilos globais, mobile e chamadas de API mapeados.
- [x] Contratos, tipos, adapters, testes e configuracao HML da V3 mapeados.

## Decisao

- [x] Opção B registrada como recomendacao principal.
- [x] Opção A descartada como estrategia principal.
- [x] Plano em fases criado sem iniciar a fase posterior.
- [x] Riscos documentados.

## Proibicoes conferidas

- [x] Sem producao.
- [x] Sem dados reais.
- [x] Sem Pix/Efi real.
- [x] Sem webhook real.
- [x] Sem upload real.
- [x] Sem API externa real.
- [x] Sem push.
- [x] Sem alteracao em `C:\clone`.

## Validacoes locais

- [x] `verificar-codificacao.ps1`.
- [x] `verificar-arquivos-proibidos.ps1`.
- [x] `verificar-segredos.ps1`.
- [x] `git diff --check`.
- [x] `git diff --cached --check`.
- [x] `git status --short`.
- [x] `git remote -v`.
