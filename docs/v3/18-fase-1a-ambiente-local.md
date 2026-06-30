# Fase 1A - Ambiente Local

## Objetivo

A Fase 1A prepara a base local de desenvolvimento do Tops do Job V3 sem criar aplicação, schema de domínio, migration, SQL, integração Efí real, deploy ou conexão externa.

## Componentes

- PostgreSQL local isolado, sem tabelas de domínio.
- MinIO local S3-compatible para storage futuro.
- Mailpit para captura local de e-mail.
- Diretórios `backend/` e `frontend/` reservados, sem aplicação.
- Diretórios `infra/local/`, `infra/staging/` e `infra/producao/` separados por ambiente.
- Scripts locais em `scripts/local/`.

## Arquivos Principais

- `.env.local.example`: variáveis de exemplo, somente placeholders.
- `infra/local/docker-compose.local.yml`: serviços locais.
- `scripts/local/iniciar-infra-local.ps1`: inicia a infraestrutura local.
- `scripts/local/parar-infra-local.ps1`: para a infraestrutura local.
- `scripts/local/status-infra-local.ps1`: lista containers do projeto local.
- `scripts/local/logs-infra-local.ps1`: exibe logs locais.
- `scripts/local/validar-config-local.ps1`: valida a configuração do Compose.

## Uso Local

Copie `.env.local.example` para `.env.local` somente na máquina local se quiser sobrescrever portas ou placeholders. `.env.local` é ignorado pelo Git.

Comandos:

```powershell
.\scripts\local\validar-config-local.ps1
.\scripts\local\iniciar-infra-local.ps1
.\scripts\local\status-infra-local.ps1
.\scripts\local\logs-infra-local.ps1
.\scripts\local\parar-infra-local.ps1
```

Os scripts exigem Docker já instalado e configurado manualmente. Esta fase não instala Docker, não baixa ferramenta por fora dos comandos futuros do usuário e não inicia serviços automaticamente durante a entrega.

## Portas Locais

- PostgreSQL: `54329`.
- MinIO API: `9000`.
- MinIO Console: `9001`.
- Mailpit SMTP: `1025`.
- Mailpit HTTP: `8025`.

## Volumes Locais

Os dados de runtime ficam sob:

```text
storage-local/postgres
storage-local/minio
storage-local/mailpit
```

`storage-local/` é ignorado pelo Git e não entra em ZIP de revisão.

## Limites da Fase

Não foram criados:

- aplicação Spring Boot;
- aplicação Next.js;
- controllers, services, entities ou repositories;
- migrations;
- SQL de schema;
- tabelas;
- banco de domínio;
- dados reais;
- conexão externa;
- integração Efí real;
- deploy, remote, commit ou push.

## Segurança

Todos os valores sensíveis em exemplos usam `CHANGE_ME`. Qualquer credencial local real deve ficar apenas em `.env.local`, fora do Git.

Produção, VPS, banco de produção e Efí real não devem ser acessados nesta fase.
