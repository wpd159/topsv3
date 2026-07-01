# Gate toolchain build local - Bloco 4.3

## Objetivo

Fechar o gate operacional de toolchain local antes de qualquer service, controller, endpoint ou importador real.

Esta fase cria diagnostico, runbook e checklist. Ela nao instala Java, Maven, Maven Wrapper, dependencias npm, nao altera PATH, nao baixa arquivos, nao acessa banco e nao acessa rede externa.

## Estado detectado

Java no PATH:

- `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe`;
- versao: `openjdk version "21.0.11" 2026-04-21 LTS`;
- status: Java 21 LTS detectado, mas nao libera build por si so.

Java local fora do PATH:

- `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe`;
- `C:\Program Files\Java\jre1.8.0_491\bin\java.exe`;
- Java 17 LTS: `PENDENTE_JAVA_17_LOCAL`.

Maven:

- `PENDENTE_MAVEN_LOCAL`;
- Maven nao foi encontrado no PATH nem nos locais comuns pesquisados.

Frontend:

- Node: `C:\Program Files\nodejs\node.exe`, `v24.16.0`;
- npm: `C:\Program Files\nodejs\npm.cmd`, `11.13.0`;
- `frontend/node_modules`: ausente;
- status: `PENDENTE_NODE_MODULES_LOCAL`.

Gerenciadores detectados apenas informativamente:

- winget: `C:\Users\WpD\AppData\Local\Microsoft\WindowsApps\winget.exe`;
- choco: `C:\ProgramData\chocolatey\bin\choco.exe`;
- scoop: pendente.

## Scripts

Criado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/diagnosticar-toolchain-local.ps1
```

Atualizado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-build-local.ps1
```

O diagnostico retorna `0` porque e somente leitura. Ele pode encontrar pendencias, mas nao tenta resolver automaticamente.

## Gate

Services, controllers e endpoints continuam bloqueados ate uma das condicoes abaixo:

- build backend validado localmente com Java 17 LTS e Maven ou Maven Wrapper disponivel sem download automatico; ou
- decisao expressa documentada aceitando o risco operacional.

Java 21 LTS detectado nao substitui a decisao de projeto por Java 17 LTS.

Maven Wrapper pode ser criado em fase futura, mas nao deve baixar binario sem autorizacao.

## Proibicoes preservadas

- nenhuma instalacao;
- nenhum download;
- nenhuma alteracao de PATH;
- nenhum Maven Wrapper binario;
- nenhum `npm install`;
- nenhum `npm ci`;
- nenhum banco;
- nenhuma API externa;
- nenhuma migration;
- nenhum SQL;
- nenhum service/controller/endpoint.

## Complemento Bloco 4.4

O Bloco 4.4 recebeu autorizacao expressa para regularizar a toolchain local e executar build.

Estado final do gate:

- Java 17 LTS encontrado e usado pelo backend;
- Maven 3.9.9 encontrado e usado pelo backend;
- `npm install` executado no frontend;
- `frontend/node_modules` presente, mas ignorado pelo Git;
- backend `compile` e `test` OK;
- frontend `lint` e `build` OK;
- `VALIDATION_RESULT=OK_BUILD_LOCAL`.

O gate de build/toolchain esta validado. A criacao de services/controllers/endpoints de dominio permanece fora do Bloco 4.4 e depende de autorizacao especifica no proximo bloco.
