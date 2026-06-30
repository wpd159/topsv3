# Runbook Local

## Objetivo

Concentrar os comandos locais do Tops do Job V3 para desenvolvimento futuro. Todos os comandos são locais e não acessam produção, VPS, banco de produção ou Efí real.

## Pré-requisitos

- Docker instalado manualmente quando a infraestrutura local for usada.
- Dependências de backend e frontend instaladas somente após autorização explícita.
- `.env.local` opcional fora do Git, baseado em `infra/local/.env.local.example`.

## Infraestrutura Local

Validar configuração:

```powershell
.\scripts\local\validar-ambiente-local.ps1
```

Subir serviços locais:

```powershell
.\scripts\local\subir-local.ps1
```

Ver status:

```powershell
.\scripts\local\status-local.ps1
```

Ver logs:

```powershell
.\scripts\local\logs-infra-local.ps1
```

Parar serviços:

```powershell
.\scripts\local\parar-local.ps1
```

Limpar runtime local:

```powershell
.\scripts\local\limpar-local.ps1 -Confirmar
```

## Aliases Mantidos

Os scripts da Fase 1A continuam disponíveis como aliases:

- `validar-config-local.ps1`;
- `iniciar-infra-local.ps1`;
- `parar-infra-local.ps1`;
- `status-infra-local.ps1`.

## Skeleton Local

Validar o skeleton sem baixar dependências:

```powershell
.\scripts\local\validar-skeleton-local.ps1
```

Backend futuro:

```powershell
cd backend
mvn spring-boot:run
```

Frontend futuro:

```powershell
cd frontend
npm run dev
```

Esses comandos de backend/frontend dependem de autorização futura para instalar ou baixar dependências.
