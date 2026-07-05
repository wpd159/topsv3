# Checklist - Bloco 29.6

## Diagnostico inicial

- [x] Inventario inicial salvo fora do repositorio.
- [x] `git status --short` executado.
- [x] `git remote -v` vazio.
- [x] Docker diagnosticado em modo somente leitura.
- [x] Recursos `cripto*`/TopsWI detectados e preservados.
- [x] Scanners iniciais sem bloqueios.

## Correcoes documentais

- [x] README corrigido para refletir o estado real do Bloco 29.5/29.6.
- [x] Checklist do Bloco 29.5 corrigido conforme validacoes reais.
- [x] Banco de quarentena marcado como nao staging final.
- [x] `POST_DATA` marcado como nao restaurado.
- [x] Staging final marcado como nao aprovado.
- [x] Decisao Pro/humana marcada como pendente.

## Alertas residuais

- [x] 153 colunas sensiveis candidatas tratadas registradas.
- [x] 4 erros agregados de sanitizacao registrados sem valores reais.
- [x] Alerta `ALERTA_SANITIZACAO_AGREGADA_NAO_CLASSIFICADA` documentado.
- [x] `total_sensivel=0` preservado como validacao posterior por padroes.
- [x] Aprovacao final com dados sanitizados mantida como pendente.

## Hardening de scripts

- [x] `producao-restore-quarentena-sem-postdata.ps1` exige nomes exatos de container, volume, network e banco.
- [x] `producao-sanitizar-db-quarentena-local.ps1` exige container/banco exatos.
- [x] `validar-dados-quarentena-sanitizada-local.ps1` exige container/banco exatos.
- [x] `diagnosticar-fks-quarentena-sanitizada-local.ps1` exige container/banco exatos.
- [x] `validar-seo-quarentena-sanitizada-local.ps1` exige container/banco exatos.
- [x] Nenhum `docker prune`, `docker compose down` ou comando destrutivo amplo foi adicionado.

## Decisao tecnica

- [x] Opcao A consolidada como obrigatoria para homologacao/cutover.
- [x] Opcao B limitada a insumo auxiliar de SEO/agregados.
- [x] Opcao C bloqueada ate revisao Pro/humana em novo bloco.
- [x] Quarentena proibida para importacao definitiva.
- [x] Quarentena proibida para validacao transacional final.
- [x] Bloco 29 permanece materialmente aberto.

## Fechamento

- [x] Parser PowerShell dos scripts de quarentena executado.
- [x] Scanners de seguranca executados.
- [x] Validadores locais obrigatorios executados.
- [x] Backend compile/test executados.
- [x] Frontend lint/build executados.
- [x] `git diff --check` e `git diff --cached --check` OK.
- [x] Arquivos staged para pacote de revisao.
- [x] ZIP de revisao gerado na Area de Trabalho.
- [x] Sem restore novo, sanitizacao nova, producao, VPS, banco de producao, API externa, remote, push ou commit.
