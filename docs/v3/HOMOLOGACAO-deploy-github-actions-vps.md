# Homologacao - Deploy GitHub Actions para VPS

## Objetivo

Preparar deploy automatico da V3 em `v3.esle.cloud`, via GitHub Actions e SSH, sem usar producao, dados reais ou integracoes reais.

O deploy HML usa `SPRING_PROFILES_ACTIVE=homologacao`. O profile e suportado por `backend/src/main/resources/application-homologacao.yml` e depende de variaveis externas em `/opt/topsv3/secrets/hml.env`.

## Secrets GitHub obrigatorios

- `HML_HOST`: host/IP da VPS de homologacao.
- `HML_USER`: deve ser `topsv3`.
- `HML_SSH_PORT`: porta SSH.
- `HML_SSH_PRIVATE_KEY`: chave privada SSH dedicada ao deploy HML.
- `HML_DOMAIN`: deve ser `v3.esle.cloud`.
- `HML_DEPLOY_PATH`: deve ser `/opt/topsv3/app/current`.

## Preparacao manual da VPS antes do primeiro deploy

Executar na VPS, como usuario com privilegio administrativo, antes de habilitar o workflow:

```bash
sudo adduser --disabled-password --gecos "" topsv3
sudo usermod -aG docker topsv3
sudo install -d -m 0750 -o topsv3 -g topsv3 /opt/topsv3/app/current
sudo install -d -m 0750 -o topsv3 -g topsv3 /opt/topsv3/secrets
sudo install -d -m 0755 /etc/nginx/sites-available /etc/nginx/sites-enabled
```

Criar `/opt/topsv3/secrets/hml.env` manualmente, fora do Git, usando `deploy/hml/hml.env.example` como base:

```bash
sudo install -m 0640 -o topsv3 -g topsv3 /dev/null /opt/topsv3/secrets/hml.env
sudoedit /opt/topsv3/secrets/hml.env
```

Garantir que o usuario `topsv3` consiga executar Docker e que o reload do Nginx esteja preparado para automacao controlada:

```bash
docker compose version
sudo nginx -t
```

Se a politica local permitir, configurar sudoers especifico para Nginx sem senha:

```text
topsv3 ALL=(root) NOPASSWD: /usr/sbin/nginx -t, /bin/systemctl reload nginx, /usr/bin/install, /bin/ln
```

## Arquivo `/opt/topsv3/secrets/hml.env`

Valores obrigatorios, sempre fora do Git:

```bash
HML_DOMAIN=v3.esle.cloud
HML_BACKEND_PORT=18080
HML_FRONTEND_PORT=13000
APP_ENV=homologacao
APP_CANONICAL_DOMAIN=https://v3.esle.cloud
APP_CORS_ALLOWED_ORIGINS=https://v3.esle.cloud
APP_ADMIN_SESSION_COOKIE_SECURE=true
EFI_ENABLED=false
TOPSV3_POSTGRES_DB=topsv3_hml
TOPSV3_POSTGRES_USER=topsv3_hml
DATABASE_PASSWORD=<preencher fora do Git>
APP_EVENT_HASH_SALT=<preencher fora do Git>
APP_AGE_GATE_SIGNING_VALUE=<preencher fora do Git>
```

O HML nao recebe credenciais, certificado, chave Pix nem webhook Efi durante o freeze. O Compose fixa `EFI_ENABLED=false`; a configuracao externa e a homologacao real formam gate da fase de importacao/preparacao do cutover.

## Nginx

O workflow instala `deploy/hml/nginx-v3-esle-cloud.conf` em:

- `/etc/nginx/sites-available/v3-esle-cloud.conf`;
- `/etc/nginx/sites-enabled/v3-esle-cloud.conf`.

Regras obrigatorias:

- `/` proxy para frontend local na porta `13000`;
- `/api/` proxy para backend local na porta `18080`;
- `X-Robots-Tag: noindex, nofollow, noarchive`;
- `/robots.txt` retorna `Disallow: /`.

## HTTPS obrigatorio antes de teste publico

O arquivo Nginx versionado permanece HTTP apenas como bootstrap inicial. Antes de qualquer teste publico navegavel em `v3.esle.cloud`, preparar certificado/HTTPS e registrar a validacao.

Status atual: `PENDENTE_HTTPS_HML_ANTES_DO_TESTE_PUBLICO`.

## Health

O health real do backend usado pelo workflow e:

```text
GET /api/health
```

O endpoint existe em `HealthController` e nao deve ser substituido por endpoint novo neste bloco.

## Proibicoes

- Nao usar `topsdojob.com`.
- Nao usar banco de producao.
- Nao restaurar backup real.
- Nao usar dados reais/sanitizados operacionais.
- Nao usar Pix/Efi real, webhook real, upload real, e-mail real ou WhatsApp real.
- Nao publicar sem revisao humana/Pro.

## Pendencias

- Seed sintetico HML navegavel, se necessario, deve ser bloco proprio.
- HTTPS/certificado de `v3.esle.cloud` deve ser preparado na VPS antes de divulgar o ambiente.
- Revisao humana/Pro continua obrigatoria antes de qualquer homologacao/cutover real.
