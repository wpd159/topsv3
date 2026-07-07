# Bloco 60 - Deploy de homologacao online

## Escopo

Configurar deploy automatico de homologacao para `v3.esle.cloud` usando GitHub Actions, SSH e usuario `topsv3`.

Este bloco nao executa deploy, nao acessa VPS, nao envia codigo, nao cria staging real com dados reais e nao usa producao.

## Arquivos criados

- `.github/workflows/deploy-hml.yml`;
- `deploy/hml/docker-compose.yml`;
- `deploy/hml/nginx-v3-esle-cloud.conf`;
- `deploy/hml/hml.env.example`;
- `scripts/deploy/validar-deploy-hml-local.ps1`;
- `docs/v3/HOMOLOGACAO-deploy-github-actions-vps.md`;
- `docs/v3/evidencias/bloco-60/relatorio-deploy-hml.md`.

## Politicas HML

- Dominio: `v3.esle.cloud`.
- Usuario SSH esperado: `topsv3`.
- Caminho remoto esperado: `/opt/topsv3/app/current`.
- Secrets externos esperados em `/opt/topsv3/secrets/hml.env`.
- Dados reais proibidos.
- Pix/Efi real proibido; `EFI_PIX_MOCK_MODE=true`.
- Upload real, e-mail real, WhatsApp real, webhook real e API externa real proibidos.
- Indexacao proibida por Nginx e robots: `X-Robots-Tag: noindex, nofollow, noarchive` e `Disallow: /`.

## Workflow

O workflow roda em:

- `workflow_dispatch`;
- `push` na branch `main`.

Secrets obrigatorios no GitHub:

- `HML_HOST`;
- `HML_USER`;
- `HML_SSH_PORT`;
- `HML_SSH_PRIVATE_KEY`;
- `HML_DOMAIN`;
- `HML_DEPLOY_PATH`.

O workflow falha se:

- `HML_USER` nao for `topsv3`;
- `HML_DOMAIN` nao for `v3.esle.cloud`;
- `HML_DEPLOY_PATH` nao for `/opt/topsv3/app/current`;
- `/opt/topsv3/secrets/hml.env` nao existir na VPS.

## Empacotamento SSH

O envio exclui:

- `.git`;
- `node_modules`;
- `.next`;
- `target`;
- dumps;
- backups;
- `.env`;
- logs brutos;
- chaves, certificados e arquivos sensiveis comuns.

## Docker Compose

Servicos HML:

- `topsv3-hml-postgres`;
- `topsv3-hml-flyway`;
- `topsv3-hml-backend`;
- `topsv3-hml-frontend`.

Recursos:

- network `topsv3-hml-net`;
- volume `topsv3-hml-postgres-data`.

As imagens usam tags fixas, sem `latest`.

## Observacao sobre dados sinteticos

O ambiente esta configurado para operar apenas com homologacao/mock. Este bloco nao executa seed. Caso a homologacao online precise conteudo navegavel, a carga sintetica devera ser feita em bloco posterior com script controlado, sem dados reais.

## Status

Workflow pronto para revisao local. Nao foi executado deploy.
