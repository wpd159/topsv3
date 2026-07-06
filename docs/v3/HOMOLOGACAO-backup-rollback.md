# HOMOLOGACAO - Backup e rollback

## Principio

Nenhuma homologacao realista ou cutover pode ocorrer sem backup e rollback testados. Este documento define contrato; nao executa backup nem restore.

## Backups obrigatorios

- Backup antes de homologacao com dados autorizados.
- Backup antes de cutover.
- Backup de configuracoes de deploy.
- Snapshot de sitemap/robots/canonical quando houver mudanca SEO.
- Inventario de versoes de backend/frontend.

## Rollback de app

- Versao anterior conhecida.
- Comando/runbook de retorno documentado fora deste bloco.
- Criterio de acionamento objetivo.
- Responsavel definido.
- Validacao pos-rollback.

## Rollback de banco

- Restauracao testada em ambiente isolado.
- Migrations com plano de reversao ou decisao formal de forward-fix.
- Importacao real com identificador de lote.
- Nenhum rollback direto em producao sem responsavel e janela aprovada.

## Rollback SEO

- Reverter 301/canonical/sitemap/robots quando criterio disparar.
- Preservar mapa anterior.
- Validar URLs criticas apos reversao.
- Monitorar Search Console e trafego.

## Criterios de acionamento

- Erro 5xx acima do limite definido.
- Login/admin indisponivel.
- Pagina publica critica indisponivel.
- Perda de acesso a anuncios principais.
- Falha em Pix/Efi/webhook quando financeiro estiver habilitado.
- Queda SEO anormal dentro da janela de observacao.
- Vazamento de dado sensivel.

## Responsaveis e tempo

- Responsavel tecnico primario.
- Responsavel de produto/negocio.
- Responsavel por SEO.
- Responsavel por financeiro quando aplicavel.
- Tempo maximo aceitavel deve ser definido antes do cutover.

## Bloqueio

Sem backup testado, responsavel e criterio de rollback, o resultado e No-Go.
