# HOMOLOGACAO - Protocolo VPS restore integral

## Objetivo

Definir protocolo seguro para eventual VPS isolada de restore/importacao integral, sem executar a VPS neste bloco e sem usar producao como bancada.

## Requisitos minimos

- VPS isolada, criada apenas para restore/importacao controlada.
- Firewall restrito por IP autorizado.
- Nenhum servico publico exposto.
- Sem dominio publico apontado para a VPS.
- Dump/backup sempre fora do repositorio.
- Credenciais sempre fora do Git.
- `.env`, certificado, token e chave fora do Git.
- Snapshot/disco protegido por acesso restrito.
- Logs brutos fora do repositorio e fora de ZIP oficial.
- Relatorios versionados apenas com status, contagens e agregados sanitizados.

## Restore

- Usar PostgreSQL compativel com o formato do backup.
- Conferir hash do backup antes de qualquer restore.
- Montar dump/backup em local fora do repositorio.
- Executar `pg_restore` com `--single-transaction` quando aplicavel.
- Usar `--no-owner`, `--no-privileges` e `--exit-on-error` salvo decisao Pro/humana documentada.
- Se `POST_DATA`/FK falhar, parar imediatamente.
- Nao corrigir orfaos sem revisao Pro/humana.
- Nao usar flags corretivas por tentativa.
- Nao promover restore parcial a staging final.

## Sanitizacao e importacao

- Sanitizacao deve ocorrer somente em banco/container/instancia destinada a sanitizacao.
- Banco bruto nunca pode ser usado pela aplicacao V3.
- Documento privado, midia real, contato, IP, user-agent, payload financeiro, storage key, URL privada e segredo devem ser tratados como sensiveis.
- Importacao real exige dry-run, relatorio de divergencias e decisao Pro/humana.

## Retencao ou destruicao da VPS

- Definir antes da execucao se a VPS sera destruida, pausada ou retida por prazo curto.
- Registrar responsavel e criterio de destruicao/retencao.
- Remover credenciais e acessos quando a VPS for encerrada.
- Confirmar que nenhum backup/dump/log bruto foi copiado para `C:\topsv3`, Git ou ZIP oficial.

## Proibicoes

- Producao nunca e bancada.
- Sem escrita em producao.
- Sem `git pull` em producao.
- Sem deploy.
- Sem migration em producao.
- Sem SQL de escrita em producao.
- Sem expor secrets em terminal, log, docs ou ZIP.
- Sem `POST_DATA` parcial como staging final.
