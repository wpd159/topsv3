# Build local backend/frontend - Bloco 4.2

## Objetivo

Preparar validacao local reproduzivel de build backend/frontend antes de qualquer service, controller, endpoint ou fluxo funcional.

Esta fase nao instala ferramentas, nao baixa dependencias, nao cria Maven Wrapper, nao executa `npm install`, nao executa `npm ci`, nao acessa banco, nao acessa rede externa, nao cria migration e nao altera SQL.

## Scripts criados

- `scripts/local/validar-build-local.ps1`;
- `scripts/local/validar-backend-build.ps1`;
- `scripts/local/validar-frontend-build.ps1`.

Contratos de exit code:

- `0`: build/validacao executavel passou;
- `1`: build executado e falhou;
- `2`: pendencia operacional, ferramenta ou dependencia local ausente.

## Estado local detectado

Backend:

- `backend/pom.xml`: presente;
- Java detectado: `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe`;
- versao detectada: `openjdk version "21.0.11" 2026-04-21 LTS`;
- requisito do projeto: Java 17 LTS;
- status Java: `PENDENTE_JAVA_17_LOCAL`;
- Maven local: `PENDENTE_MAVEN_LOCAL`;
- `backend/mvnw.cmd`: ausente;
- `mvnw.cmd` na raiz: ausente;
- Maven Wrapper sem download comprovado: `PENDENTE_MAVEN_WRAPPER_LOCAL`;
- build backend: `PENDENTE_BACKEND_BUILD_LOCAL`.

Frontend:

- `frontend/package.json`: presente;
- Node detectado: `C:\Program Files\nodejs\node.exe`;
- versao Node: `v24.16.0`;
- npm detectado: `C:\Program Files\nodejs\npm.ps1`;
- versao npm: `11.13.0`;
- `frontend/node_modules`: ausente;
- build/lint frontend: `PENDENTE_FRONTEND_BUILD_LOCAL`;
- motivo: `PENDENTE_NODE_MODULES_LOCAL`.

## Comandos seguros

Validacao agregada:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-build-local.ps1
```

Backend isolado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-backend-build.ps1
```

Frontend isolado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-frontend-build.ps1
```

Os scripts podem executar build/testes apenas quando as ferramentas e dependencias ja estiverem disponiveis localmente. O backend usa Maven em modo offline quando houver executor disponivel. O frontend usa scripts existentes em `package.json` apenas quando `node_modules` ja existir.

## Comandos proibidos sem autorizacao futura

- `npm install`;
- `npm ci`;
- criacao ou download de Maven Wrapper;
- instalacao de Maven;
- execucao de build que baixe dependencias automaticamente;
- acesso a banco;
- Flyway;
- Docker;
- API externa;
- producao;
- VPS;
- Efi real.

## Bloqueio antes de services/controllers

Nenhum proximo bloco funcional deve iniciar services, controllers, endpoints ou importador real sem uma destas condicoes:

- build backend local validado com Java 17 LTS e Maven/wrapper disponivel sem download; ou
- decisao expressa documentada aceitando o risco operacional.

Maven Wrapper pode ser preparado futuramente, mas nao deve incluir binario baixado nem provocar download sem autorizacao.

## Pendencias

- `PENDENTE_JAVA_17_LOCAL`;
- `PENDENTE_MAVEN_LOCAL`;
- `PENDENTE_MAVEN_WRAPPER_LOCAL`;
- `PENDENTE_BACKEND_BUILD_LOCAL`;
- `PENDENTE_NODE_MODULES_LOCAL`;
- `PENDENTE_FRONTEND_BUILD_LOCAL`.

Essas pendencias nao autorizam instalar ferramentas nesta fase.

## Complemento Bloco 4.3

O Bloco 4.3 adiciona o diagnostico:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/diagnosticar-toolchain-local.ps1
```

Resultado local:

- Java 21 LTS no PATH;
- Java 8 encontrado fora do PATH;
- Java 17 LTS pendente;
- Maven pendente;
- Node/npm detectados;
- `frontend/node_modules` pendente;
- winget e choco detectados apenas informativamente; scoop pendente.

O diagnostico retorna `0` porque nao instala nem altera nada. Ele apenas documenta o estado local e comandos sugeridos para execucao manual/autorizada futura.

## Complemento Bloco 4.4

O Bloco 4.4 recebeu autorizacao expressa para localizar Java 17 LTS, localizar Maven, executar `npm install` no frontend e baixar dependencias publicas necessarias ao build local.

Estado atualizado:

- Java 17 LTS localizado em `.topsv3-toolchain\jdks` e usado apenas por processo;
- Maven 3.9.9 localizado em `.topsv3-toolchain\maven`;
- Node `v24.16.0` e npm `11.13.0` detectados;
- `frontend/node_modules` criado por `npm install` autorizado;
- `frontend/package-lock.json` criado e versionavel;
- `backend/target`, `frontend/node_modules` e `frontend/.next` permanecem ignorados pelo Git.

Resultado:

- backend: `mvn -version`, `mvn -q -DskipTests compile` e `mvn -q test` OK;
- frontend: `npm run lint` e `npm run build` OK;
- frontend: `typecheck` nao aplicavel porque nao existe script configurado;
- validacao agregada: `VALIDATION_RESULT=OK_BUILD_LOCAL`.

O gate de build/toolchain local esta validado para o proximo bloco tecnico. Services, controllers e endpoints de dominio continuam exigindo autorizacao expressa de fase futura.

## Complemento Bloco 5

O Bloco 5 usa o gate liberado pelo Bloco 4.4 para criar somente API publica de leitura.

Build esperado:

- backend `mvn -q -DskipTests compile`;
- backend `mvn -q test`;
- frontend `npm run lint`;
- frontend `npm run build`.

Nenhum build deste bloco deve acessar banco, Flyway, Docker, producao, VPS, API externa ou dados reais.

## Complemento Bloco 7

O Bloco 7 executou validacao end-to-end local com PostgreSQL descartavel, alem do build local.

Resultado:

- backend compile/test OK;
- frontend lint/build OK;
- backend local iniciou em profile `local` contra PostgreSQL descartavel;
- `ddl-auto: validate` passou apos ajustes de mapeamento JPA para campos `char(n)`;
- smoke HTTP da API publica passou.

Nenhuma ferramenta nova foi instalada nesta etapa.

## Complemento Bloco 12

O backend passou a depender de `spring-boot-starter-security` e `spring-security-test`.

A validacao local continua usando toolchain local ja disponivel. O bloco permite baixar dependencias Maven publicas necessarias ao build, sem instalar ferramenta, sem acessar producao e sem API externa de negocio.
