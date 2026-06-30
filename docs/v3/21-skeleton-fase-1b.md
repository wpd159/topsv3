# Skeleton da Fase 1B

## Objetivo

A Fase 1B cria um skeleton mínimo local de backend Spring Boot, frontend Next.js, contrato inicial e validação local. A fase não cria domínio funcional, migrations, SQL, Pix real, importador, autenticação completa, painel admin funcional, frontend público real, conexão externa ou deploy.

Correções herdadas fechadas na Fase 1C e 1C.1: o backend usa Java 17 LTS como padrão conservador; os health checks locais incluem readiness e liveness; os perfis `local` e `test` foram documentados em arquivos de configuração próprios; o status do health local foi padronizado como `UP`.

## Backend

Arquivos principais:

- `backend/pom.xml`;
- `backend/src/main/java/br/com/topsdojob/v3/TopsDoJobBackendApplication.java`;
- `backend/src/main/java/br/com/topsdojob/v3/health/HealthController.java`;
- `backend/src/main/resources/application.yml`;
- `backend/src/main/resources/application-local.yml`;
- `backend/src/main/resources/application-test.yml`.

Endpoint local:

```text
GET /api/health
GET /api/health/readiness
GET /api/health/liveness
```

Resposta esperada:

```json
{
  "status": "UP",
  "app": "topsdojob-v3-backend",
  "environment": "local",
  "efiPixMockMode": true
}
```

## Frontend

Arquivos principais:

- `frontend/package.json`;
- `frontend/next.config.mjs`;
- `frontend/src/app/layout.tsx`;
- `frontend/src/app/page.tsx`;
- `frontend/src/app/health/page.tsx`.

As páginas são placeholders de skeleton local. Elas não implementam jornada pública real nem painel admin.

## Contrato

Contrato inicial:

```text
contracts/openapi/topsdojob-v3-local.yaml
```

O contrato cobre os health checks locais do backend.

Componentes transversais de contratos futuros foram documentados na Fase 1C.2. Eles não implementam endpoints novos nem regra de negócio nesta fase.

## Validação

Validação estática local:

```powershell
.\scripts\local\validar-skeleton-local.ps1
```

Essa validação não baixa dependências. Ela confere arquivos obrigatórios, ausência de SQL/migrations/domínio fora de escopo, Compose sem `latest` e variáveis locais obrigatórias.

## Fora de Escopo

Não foram criados:

- migrations;
- SQL;
- schema de domínio;
- entidades JPA de negócio;
- repositories de domínio;
- services de negócio;
- fluxo Pix real;
- integração Efí real;
- importador;
- autenticação funcional completa;
- painel admin funcional;
- frontend público real;
- conexão com produção;
- dados reais;
- seeders;
- Docker de produção;
- deploy.
