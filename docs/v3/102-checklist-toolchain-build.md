# Checklist toolchain build - Bloco 4.3

## Gate inicial

- [x] Inventario inicial criado fora do repositorio.
- [x] Inventario inicial validado como UTF-8 com BOM.
- [x] Inventario inicial confirmado fora do Git.

## Diagnostico

- [x] Java no PATH detectado.
- [x] Java 17 local procurado.
- [x] Java 17 pendente registrado.
- [x] Maven local procurado.
- [x] Maven pendente registrado.
- [x] Node/npm detectado.
- [x] `frontend/node_modules` verificado.
- [x] `frontend/node_modules` pendente registrado.
- [x] winget/choco/scoop detectados apenas informativamente.
- [x] Comandos sugeridos documentados.

## Proibicoes preservadas

- [x] Nenhuma instalacao executada.
- [x] Nenhum download executado.
- [x] Nenhum PATH alterado.
- [x] Nenhum Maven Wrapper binario criado.
- [x] Nenhum `npm install` executado.
- [x] Nenhum `npm ci` executado.
- [x] Nenhum service/controller criado.
- [x] Nenhum endpoint criado.
- [x] Nenhuma migration criada.
- [x] Nenhum SQL alterado.
- [x] Nenhum banco acessado.
- [x] Nenhuma API externa acessada.
- [x] Nenhum dado real usado.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.

## Validacoes

- [x] `scripts/local/diagnosticar-toolchain-local.ps1`.
- [x] `scripts/local/validar-build-local.ps1` executado e retornou pendencia operacional esperada.
- [x] `scripts/local/validar-persistencia-jpa-estatica.ps1`.
- [x] `scripts/security/verificar-codificacao.ps1`.
- [x] `scripts/security/verificar-arquivos-proibidos.ps1`.
- [x] `scripts/security/verificar-segredos.ps1`.
- [x] `scripts/local/validar-rotas-publicas-seo-local.ps1`.
- [x] `scripts/local/validar-migrations-sql-estatico.ps1`.
- [x] `scripts/local/validar-fonte-importacao-local.ps1`.
- [x] `git diff --check`.
- [x] `git diff --cached --check`.
- [x] `git status --short`.
- [x] `git remote -v`.
- [x] ZIP final criado e validado.

## Bloqueio restante

- [x] Services/controllers permanecem bloqueados ate build backend validado ou decisao expressa documentada.

## Complemento Bloco 4.4

- [x] Autorizacao expressa para regularizar toolchain recebida.
- [x] Java 17 LTS localizado.
- [x] Maven 3.9.9 localizado.
- [x] `npm install` executado no frontend.
- [x] `frontend/node_modules` ignorado pelo Git.
- [x] `frontend/.next` ignorado pelo Git.
- [x] `backend/target` ignorado pelo Git.
- [x] Backend `compile` OK.
- [x] Backend `test` OK.
- [x] Frontend `lint` OK.
- [x] Frontend `build` OK.
- [x] `typecheck` nao aplicavel por ausencia de script.
- [x] Gate de toolchain/build validado para proximo bloco tecnico autorizado.
- [x] Nenhum service/controller/endpoint foi criado no Bloco 4.4.
