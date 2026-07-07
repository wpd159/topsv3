# Bloco 61 - Validacao HML online

## Objetivo

Registrar a configuracao HML real de Nginx aplicada para `v3.esle.cloud` e validar o ambiente online por requisicoes publicas de leitura, sem deploy neste bloco, sem push e sem uso de dados reais.

## Escopo

- Atualizar `deploy/hml/nginx-v3-esle-cloud.conf` para refletir HTTP 80 redirecionando para HTTPS 443.
- Registrar certificados esperados em `/etc/letsencrypt/live/v3.esle.cloud/`.
- Confirmar proxies HML:
  - `/api/` para `http://127.0.0.1:18080`;
  - `/` para `http://127.0.0.1:13000`.
- Validar `X-Robots-Tag: noindex, nofollow, noarchive`.
- Validar `/robots.txt` com `Disallow: /`.
- Validar `GET /api/health`.

## Resultado

HML online em `https://v3.esle.cloud`, com bloqueio de indexacao ativo e health backend respondendo `UP`.

## Limites

- Nao houve alteracao de producao.
- Nao houve dado real.
- Nao houve Pix/Efi real.
- Nao houve webhook real.
- Nao houve upload real.
- Nao houve push.
- Nao houve fase posterior.
