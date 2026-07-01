# Relatorio build local - Bloco 4.4

## Objetivo

Regularizar a toolchain local e validar build backend/frontend sem acessar banco, sem aplicar migrations, sem criar SQL, sem criar services/controllers/endpoints de dominio e sem iniciar importador real.

## Inventario inicial

Inventario criado fora do repositorio antes das alteracoes:

```text
C:\Users\WpD\AppData\Local\Temp\topsv3-inventario-inicial-2026-06-30-150008-323\INVENTARIO-INICIAL.csv
```

O inventario foi usado como base para o pacote final do Bloco 4.4.

## Toolchain localizada

Java:

- Java no PATH permanece como Java 21 LTS;
- Java 17 LTS foi localizado para uso do build backend;
- caminho usado pelo validador: `C:\Users\WpD\.topsv3-toolchain\jdks\temurin-17.0.19_10\jdk-17.0.19+10`;
- `JAVA_HOME` foi definido apenas no processo do validador, sem alterar PATH global.

Maven:

- Maven no PATH global continua ausente;
- Maven local usado pelo build: `C:\Users\WpD\.topsv3-toolchain\maven\apache-maven-3.9.9\bin\mvn.cmd`;
- versao validada: Apache Maven 3.9.9.

Node/npm:

- Node: `v24.16.0`;
- npm: `11.13.0`;
- dependencias frontend instaladas localmente com `npm install`, porque nao havia `package-lock.json` antes da execucao.

Tentativas operacionais registradas:

- `winget` para Java 17 iniciou instalador MSI e precisou ser interrompido por travamento operacional;
- `choco` para Java 17 falhou por permissao/lock local;
- `winget` para Maven nao encontrou pacote `Apache.Maven`;
- Java 17 e Maven foram entao localizados via ZIP em pasta do usuario, sem configurar PATH global.

## Backend

Comandos executados em `backend` com Java 17 e Maven local:

```powershell
mvn -version
mvn -q -DskipTests compile
mvn -q test
```

Resultado:

- `mvn -version`: OK, Maven 3.9.9 com Java 17.0.19;
- `mvn -q -DskipTests compile`: OK;
- `mvn -q test`: OK apos correcao estrutural de caminho em teste.

Correcao aplicada:

- `PacoteEntradaImportacaoTest` passou a resolver o arquivo de exemplo sanitizado a partir da raiz real do repositorio quando executado dentro de `backend`.

Essa correcao nao cria regra de negocio, nao acessa dump, nao acessa banco e nao inicia importacao real.

## Frontend

Comandos executados em `frontend`:

```powershell
npm install
npm run lint
npm run build
```

Resultado:

- `npm install`: OK, gerou `frontend/package-lock.json`;
- `npm run lint`: OK apos criacao de `frontend/eslint.config.mjs` e correcoes de lint;
- `npm run build`: OK, Next.js gerou build local;
- `npm run typecheck`: nao aplicavel, script ausente em `frontend/package.json`.

Avisos:

- `npm install` registrou vulnerabilidades conhecidas no conjunto instalado;
- nenhuma correcao automatica de dependencias foi executada nesta fase;
- revisao/upgrade de dependencias fica para fase futura propria.

Correcoes aplicadas:

- configuracao ESLint local para Next.js;
- remocao de variavel `error` nao usada no cliente de API local;
- renomeacao de identificador local `module` para `adminModule`;
- normalizacao gerada pelo Next.js em `frontend/next-env.d.ts`.

## Scripts atualizados

Scripts ajustados para refletir o estado local real:

- `scripts/local/diagnosticar-toolchain-local.ps1`;
- `scripts/local/validar-backend-build.ps1`;
- `scripts/local/validar-frontend-build.ps1`;
- `scripts/local/validar-build-local.ps1`;
- `scripts/local/validar-migrations-sql-estatico.ps1`;
- `scripts/local/validar-rotas-publicas-seo-local.ps1`.

O backend agora valida:

- descoberta de Java 17 local;
- descoberta de Maven local;
- `mvn -version`;
- `mvn -q -DskipTests compile`;
- `mvn -q test`.

O frontend agora valida scripts existentes em `package.json` e registra scripts ausentes como nao configurados.

Os validadores de SQL/rotas continuam bloqueando SQL fora de `backend/src/main/resources/db/migration`, mas passaram a ignorar copias geradas em saidas de build versionadamente proibidas, como `backend/target`, `frontend/node_modules`, `frontend/.next`, `frontend/out` e `frontend/dist`.

## Resultados consolidados

Resultado agregado:

```text
VALIDATION_RESULT=OK_BUILD_LOCAL
BACKEND_EXIT=0
FRONTEND_EXIT=0
```

O gate de toolchain/build local fica liberado para o proximo bloco tecnico, desde que haja autorizacao expressa nova para criar services/controllers/endpoints.

## Limites preservados

Nao houve:

- producao;
- VPS;
- banco;
- Efi real;
- OpenAI;
- API externa de negocio;
- dump real;
- dado real;
- migration nova;
- SQL novo;
- Flyway;
- Docker;
- seed;
- service de negocio;
- controller de dominio;
- endpoint de dominio;
- importador real;
- remote;
- push;
- commit.

## Arquivos gerados por build

Diretorios locais ignorados pelo Git:

- `backend/target/`;
- `frontend/node_modules/`;
- `frontend/.next/`.

Arquivos versionaveis criados pelo gate:

- `frontend/package-lock.json`;
- `frontend/eslint.config.mjs`.

## Riscos residuais

- Java 21 permanece no PATH global; os scripts usam Java 17 local por processo.
- Maven nao esta no PATH global; os scripts localizam Maven em `.topsv3-toolchain`.
- `frontend/package.json` nao possui script `typecheck`.
- `npm install` apontou vulnerabilidades; nao foi executado `npm audit fix`.
- Validacao runtime JPA com banco continua fora desta fase.
- Services/controllers/endpoints ainda exigem fase propria autorizada.

## Complemento Bloco 7

O build local continuou valido apos a execucao e2e:

- backend `mvn -q -DskipTests compile` OK;
- backend `mvn -q test` OK;
- frontend `npm run lint` OK;
- frontend `npm run build` OK.

O e2e usou a toolchain local ja regularizada e nao instalou nem baixou ferramenta nova.
