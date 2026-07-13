# Relatorio e2e local descartavel

- Resultado: OK_E2E_LOCAL_DESCARTAVEL
- Detalhe: PostgreSQL descartavel, migrations, backend local e smoke HTTP especifico passaram.
- PostgreSQL executado: True
- Imagem PostgreSQL local: postgres:16
- Prefixo Docker: topsv3-bloco34-wizard-paridade
- Somente smoke HTTP: True
- API smoke script: C:\topsv3\scripts\local\validar-wizard-anunciar-sintetico-local.ps1
- Fixture sintetica JSON: NAO_INFORMADA
- Porta PostgreSQL efemera: 49266
- Migrations aplicadas: True
- Quantidade de migrations aplicadas: 23
- Dados sinteticos aplicados: False
- Dados admin sinteticos aplicados: False
- Fixture sintetica aplicada: False
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
- Migrations V001-V023 aplicadas via psql ordenado no PostgreSQL descartavel.
- Dados sinteticos nao aplicados por parametro SemDadosSinteticos.
- Fixture sintetica JSON nao informada; overlay Bloco 31 nao aplicado.
- Backend local iniciado em perfil local na porta 18133.
- Backend local respondeu health/readiness.
- Smoke HTTP da API publica local executado com sucesso.
- Modo somente smoke HTTP ativado para validador especifico.
- Schema descartavel inspecionado com 72 tabelas em public.

## Garantias
- Nenhum pull/download de imagem foi executado.
- Nenhum volume persistente foi criado.
- Nenhum recurso Docker fora do prefixo informado foi removido pelo script.
- Nenhum dado real, dump ou arquivo real de entrada foi usado.
- Smoke HTTP cobre midia publica sem bucket, chaveObjeto, provider, hash ou URL publica real.
- Nenhuma producao, VPS, banco de producao, API externa, Efi real ou OpenAI foi acessado.
- Nenhum commit, push ou remote foi executado pelo script de E2E.
