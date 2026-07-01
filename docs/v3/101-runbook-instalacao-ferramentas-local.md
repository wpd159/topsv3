# Runbook instalacao ferramentas local - Bloco 4.3

## Proposito

Este runbook registra comandos sugeridos para execucao manual/autorizada futura. Nenhum comando desta pagina foi executado no Bloco 4.3.

Antes de executar qualquer instalacao, confirmar autorizacao expressa.

## Java 17 LTS

Opcao winget:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

Opcao choco:

```powershell
choco install temurin17 -y
```

Opcao scoop:

```powershell
scoop install java/temurin17-jdk
```

Apos instalacao manual/autorizada, validar:

```powershell
java -version
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/diagnosticar-toolchain-local.ps1
```

## Maven

Opcao winget:

```powershell
winget install Apache.Maven
```

Opcao choco:

```powershell
choco install maven -y
```

Opcao scoop:

```powershell
scoop install maven
```

Apos instalacao manual/autorizada, validar:

```powershell
mvn -version
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-backend-build.ps1
```

## Maven Wrapper futuro

O Maven Wrapper pode ser uma alternativa futura, mas:

- nao deve ser criado baixando binario sem autorizacao;
- nao deve incluir binario de wrapper obtido automaticamente sem revisao;
- nao deve disparar download oculto durante validacao;
- deve ser revisado antes de entrar no Git.

## Frontend

`frontend/node_modules` esta ausente. Para liberar lint/build frontend, sera necessaria autorizacao futura para instalar dependencias.

Comandos documentais, nao executados:

```powershell
cd frontend
npm install
```

ou, se houver lockfile aprovado:

```powershell
cd frontend
npm ci
```

Apos instalacao manual/autorizada, validar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-frontend-build.ps1
```

## Validacao agregada

Depois de Java 17, Maven/wrapper e dependencias frontend estarem disponiveis localmente:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-build-local.ps1
```

Resultado esperado para liberar proximos blocos funcionais:

- backend build validado com `exit 0`;
- frontend build/lint validado ou pendencia explicitamente aceita;
- nenhum download automatico durante validacao.

## Bloqueio

Enquanto o backend build nao estiver validado ou nao houver decisao expressa documentada, services/controllers/endpoints de dominio permanecem bloqueados.

## Execucao autorizada no Bloco 4.4

No Bloco 4.4 houve autorizacao expressa para regularizar a toolchain local.

Resultado operacional:

- tentativa via `winget` para Java 17 travou em instalador MSI e foi interrompida;
- tentativa via `choco` para Java 17 falhou por permissao/lock local;
- tentativa via `winget` para Maven nao encontrou pacote `Apache.Maven`;
- Java 17 LTS foi localizado por ZIP em pasta do usuario;
- Maven 3.9.9 foi localizado por ZIP em pasta do usuario;
- `npm install` foi executado no frontend.

Os scripts de build usam a toolchain localizada por processo, sem configurar PATH global.

Depois da regularizacao, a validacao agregada passou com:

```text
VALIDATION_RESULT=OK_BUILD_LOCAL
```

Este runbook permanece como referencia para manutencao futura; novas instalacoes ou upgrades ainda exigem autorizacao expressa.
