# Relatorio - Deploy HML Bloco 60

## Status

Configuracao local de deploy HML criada. O deploy nao foi executado.

Complemento corretivo aplicado antes de qualquer deploy: o backend HML nao usa mais profile `local`; o profile ativo esperado e `homologacao`.

## Arquivos criados

- `.github/workflows/deploy-hml.yml`;
- `deploy/hml/docker-compose.yml`;
- `deploy/hml/nginx-v3-esle-cloud.conf`;
- `deploy/hml/hml.env.example`;
- `scripts/deploy/validar-deploy-hml-local.ps1`;
- `docs/v3/272-bloco-60-deploy-homologacao-online.md`;
- `docs/v3/273-checklist-bloco-60-deploy-homologacao-online.md`;
- `docs/v3/HOMOLOGACAO-deploy-github-actions-vps.md`;
- `docs/v3/evidencias/bloco-60/relatorio-deploy-hml.md`.

## Secrets GitHub necessarios

- `HML_HOST`;
- `HML_USER`;
- `HML_SSH_PORT`;
- `HML_SSH_PRIVATE_KEY`;
- `HML_DOMAIN`;
- `HML_DEPLOY_PATH`.

## Recursos HML esperados na VPS

- Usuario: `topsv3`.
- Deploy path: `/opt/topsv3/app/current`.
- Secrets externos: `/opt/topsv3/secrets/hml.env`.
- Nginx habilitado para `v3.esle.cloud`.
- Docker Compose disponivel para o usuario `topsv3`.

## Protecoes

- Pacote SSH exclui `.git`, `node_modules`, `.next`, `target`, dumps, backups, `.env`, logs brutos, `topsv3-auditoria-local`, `logs-brutos-nao-versionar` e arquivos de chave/certificado.
- Nginx aplica `X-Robots-Tag: noindex, nofollow, noarchive`.
- `/robots.txt` retorna `Disallow: /`.
- `EFI_PIX_MOCK_MODE=true`.
- Ambiente usa `APP_ENV=homologacao`.
- Backend usa `SPRING_PROFILES_ACTIVE=homologacao`.
- Profile `homologacao` suportado por `backend/src/main/resources/application-homologacao.yml`, com datasource via variaveis externas e `ddl-auto=validate`.
- Health check confirmado em `GET /api/health`, existente em `HealthController`.

## HTTPS HML

- O Nginx HML permanece em HTTP apenas como bootstrap operacional.
- Antes de qualquer teste publico navegavel em `v3.esle.cloud`, e obrigatorio preparar HTTPS/certificado e validar reload seguro.
- Status: `PENDENTE_HTTPS_HML_ANTES_DO_TESTE_PUBLICO`.

## Nao executado

- Nenhum SSH.
- Nenhum deploy.
- Nenhum push.
- Nenhum acesso a producao/VPS.
- Nenhum dado real.
- Nenhum Pix/Efi real, webhook real, upload real, e-mail real ou WhatsApp real.
