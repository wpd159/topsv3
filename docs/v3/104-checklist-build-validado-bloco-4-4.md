# Checklist build validado - Bloco 4.4

## Preparacao

- [x] Inventario inicial criado fora do repositorio.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.
- [x] Escopo local confirmado.

## Toolchain

- [x] Java 17 LTS localizado para backend.
- [x] Java 21 no PATH registrado como risco residual controlado.
- [x] `JAVA_HOME` usado apenas no processo do validador.
- [x] Maven 3.9.9 localizado em pasta local do usuario.
- [x] Node detectado.
- [x] npm detectado.
- [x] `frontend/node_modules` criado por `npm install` autorizado.
- [x] `frontend/package-lock.json` criado.

## Backend

- [x] `mvn -version` executado.
- [x] `mvn -q -DskipTests compile` executado.
- [x] `mvn -q test` executado.
- [x] Teste com caminho de exemplo sanitizado corrigido.
- [x] `VALIDATION_RESULT=OK_BACKEND_BUILD_LOCAL`.

## Frontend

- [x] `npm install` executado porque nao havia lockfile.
- [x] `npm run lint` executado.
- [x] `npm run build` executado.
- [x] Script `typecheck` registrado como nao configurado.
- [x] ESLint local configurado.
- [x] Erros de lint corrigidos.
- [x] `VALIDATION_RESULT=OK_FRONTEND_BUILD_LOCAL`.

## Scripts

- [x] `scripts/local/diagnosticar-toolchain-local.ps1` atualizado.
- [x] `scripts/local/validar-build-local.ps1` preservado como agregador.
- [x] `scripts/local/validar-backend-build.ps1` atualizado para Java 17/Maven local e compile/test.
- [x] `scripts/local/validar-frontend-build.ps1` atualizado para scripts existentes e ausentes.
- [x] `scripts/local/validar-migrations-sql-estatico.ps1` ignora saidas de build proibidas no Git.
- [x] `scripts/local/validar-rotas-publicas-seo-local.ps1` ignora saidas de build proibidas no Git.

## Gitignore

- [x] `backend/target/` ignorado.
- [x] `frontend/node_modules/` ignorado.
- [x] `frontend/.next/` ignorado.
- [x] logs/caches/temp de build continuam ignorados.
- [x] `frontend/package-lock.json` permanece versionavel.
- [x] `frontend/eslint.config.mjs` permanece versionavel.

## Proibicoes preservadas

- [x] Nenhuma producao acessada.
- [x] Nenhuma VPS acessada.
- [x] Nenhum banco acessado.
- [x] Nenhuma Efi real acessada.
- [x] Nenhuma API externa de negocio acessada.
- [x] Nenhum dump real usado.
- [x] Nenhum dado real usado.
- [x] Nenhuma migration criada.
- [x] Nenhum SQL alterado.
- [x] Nenhum Flyway executado.
- [x] Nenhum Docker executado para banco/migration.
- [x] Nenhum seed criado.
- [x] Nenhum service/controller/endpoint de dominio criado.
- [x] Nenhum importador real iniciado.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.

## Validacoes finais

- [x] `scripts/local/diagnosticar-toolchain-local.ps1`.
- [x] `scripts/local/validar-build-local.ps1`.
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
- [x] ZIP final gerado na Area de Trabalho.

## Gate

- [x] Gate de build/toolchain local validado.
- [x] Services/controllers/endpoints continuam dependentes de proximo bloco expressamente autorizado.
- [x] Bloco 4.4 nao iniciou fase posterior.
