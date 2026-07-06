# Relatorio e2e local descartavel

- Resultado: OK_E2E_LOCAL_DESCARTAVEL
- Detalhe: PostgreSQL descartavel, migrations, backend local e smoke HTTP passaram.
- PostgreSQL executado: True
- Imagem PostgreSQL local: postgres:16
- Prefixo Docker: topsv3-admin-sintetico
- API smoke script: C:\topsv3\scripts\local\validar-admin-moderacao-sintetica-local.ps1
- Fixture sintetica JSON: C:\topsv3\backend\src\test\resources\fixtures\v3-dados-sinteticos.json
- Porta PostgreSQL efemera: 51680
- Migrations aplicadas: True
- Quantidade de migrations aplicadas: 17
- Dados sinteticos aplicados: True
- Dados admin sinteticos aplicados: True
- Fixture sintetica aplicada: True
- Backend iniciado: True
- Smoke HTTP OK: True
- Backend encerrado: True
- Container removido: True
- Rede removida: True
- Volume persistente criado: False

## Pendencias
- Nenhuma

## Falhas
- Nenhuma

## Passos
- Validacao e2e local descartavel iniciada.
- Docker daemon disponivel.
- Rede Docker descartavel criada.
- PostgreSQL descartavel iniciado sem volume persistente.
- PostgreSQL descartavel respondeu ao pg_isready.
- Migrations V001-V017 aplicadas via psql ordenado no PostgreSQL descartavel.
- Dados sinteticos publicos e admin minimos aplicados no banco descartavel.
- Fixture sintetica JSON aplicada como overlay no banco descartavel.
- Backend local iniciado em perfil local na porta 18135.
- Backend local respondeu health/readiness.
- Smoke HTTP da API publica local executado com sucesso.
- Smoke HTTP validou outbox admin read-only, preview sanitizado, simulacao local, RBAC e ausencia de envio real.
- Auditoria de moderacao local registrada com 9 eventos sanitizados.
- Auditoria de moderacao local mascarou e-mail, contato e documento em motivos sinteticos.
- Auditoria de simulacao local de outbox registrada sem payload bruto e sem envio externo.
- SOLICITAR_AJUSTE nao registrou decisao final em decisao_moderacao.
- Decisoes finais de revisao registradas em decisao_moderacao: 4.
- Outbox local de moderacao preservou 2 eventos pendentes sem envio externo.
- Outbox local de moderacao teve 1 evento PROCESSADO por simulacao local sem envio real.
- Schema descartavel inspecionado com 68 tabelas em public.

## Garantias
- Nenhum pull/download de imagem foi executado.
- Nenhum volume persistente foi criado.
- Nenhum recurso Docker fora do prefixo informado foi removido pelo script.
- Nenhum dado real, dump ou arquivo real de entrada foi usado.
- Smoke HTTP cobre midia publica sem bucket, chaveObjeto, provider, hash ou URL publica real.
- Nenhuma producao, VPS, banco de producao, API externa, Efi real ou OpenAI foi acessado.
- Nenhum commit, push ou remote foi executado pelo script de E2E.
