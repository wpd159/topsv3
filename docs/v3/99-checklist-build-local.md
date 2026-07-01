# Checklist build local - Bloco 4.2

## Gate inicial

- [x] Inventario inicial criado fora do repositorio.
- [x] Inventario inicial validado como UTF-8 com BOM.
- [x] Inventario inicial confirmado fora do Git.

## Deteccoes

- [x] Java detectado.
- [ ] Java 17 validado.
- [x] Pendencia `PENDENTE_JAVA_17_LOCAL` registrada.
- [x] Maven local detectado ou pendencia registrada.
- [x] Maven Wrapper detectado ou pendencia registrada.
- [x] Backend build executado ou pendencia registrada.
- [x] Node/npm detectado ou pendencia registrada.
- [x] Frontend `node_modules` detectado ou pendencia registrada.
- [x] Frontend build/lint executado ou pendencia registrada.

## Proibicoes preservadas

- [x] Nenhum download automatico executado.
- [x] Nenhuma dependencia instalada.
- [x] Nenhum Maven Wrapper criado.
- [x] Nenhum arquivo binario de wrapper criado.
- [x] Nenhuma migration criada.
- [x] Nenhum SQL alterado.
- [x] Nenhuma entidade nova criada.
- [x] Nenhum repository novo criado.
- [x] Nenhum service/controller criado.
- [x] Nenhum endpoint criado.
- [x] Nenhum importador real criado.
- [x] Nenhum banco acessado.
- [x] Nenhuma API externa acessada.
- [x] Nenhum dado real usado.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.

## Validacoes

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

## Bloqueio para proximos blocos funcionais

- [x] Services/controllers permanecem bloqueados ate build backend validado ou decisao expressa documentada.

## Complemento Bloco 4.3

- [x] Diagnostico de toolchain local criado.
- [x] Java 17 procurado fora do PATH.
- [x] Maven procurado fora do PATH.
- [x] winget/choco/scoop detectados apenas informativamente.
- [x] Runbook de instalacao manual/autorizada criado.
- [x] Nenhuma instalacao, download ou alteracao de PATH executada.

## Complemento Bloco 4.4

- [x] Java 17 LTS localizado.
- [x] Maven 3.9.9 localizado.
- [x] `npm install` executado com autorizacao expressa da fase.
- [x] `frontend/package-lock.json` criado.
- [x] `frontend/eslint.config.mjs` criado.
- [x] Backend compile/test validado.
- [x] Frontend lint/build validado.
- [x] `typecheck` registrado como nao configurado.
- [x] `VALIDATION_RESULT=OK_BUILD_LOCAL`.
- [x] Gate de build/toolchain liberado para proximo bloco tecnico autorizado.
- [x] Services/controllers/endpoints nao foram criados nesta fase.
