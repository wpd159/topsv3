# Relatorio Nginx HML - Bloco 61

## Configuracao registrada

`deploy/hml/nginx-v3-esle-cloud.conf` foi atualizado para refletir a configuracao real aplicada na VPS HML:

- HTTP 80 redireciona para HTTPS.
- HTTPS 443 usa:
  - `/etc/letsencrypt/live/v3.esle.cloud/fullchain.pem`;
  - `/etc/letsencrypt/live/v3.esle.cloud/privkey.pem`.
- `/api/` faz proxy para `http://127.0.0.1:18080`.
- `/` faz proxy para `http://127.0.0.1:13000`.
- `X-Robots-Tag` aplica `noindex, nofollow, noarchive`.
- `/robots.txt` retorna `Disallow: /`.

## Observacoes

- A configuracao versionada nao contem segredo, certificado ou chave privada, apenas caminhos operacionais.
- O bloco registra o estado HML online, mas nao executa deploy.
- O ambiente segue proibido para dados reais e integracoes reais.
