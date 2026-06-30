# Infraestrutura Local

Ambiente local isolado para desenvolvimento futuro da V3.

Componentes preparados:

- PostgreSQL local sem schema de domínio;
- MinIO local S3-compatible para storage;
- Mailpit para captura local de e-mail;
- volumes locais sob `storage-local/`, ignorados pelo Git.

Use os scripts em `scripts/local/` para operar o ambiente:

- `validar-ambiente-local.ps1`;
- `subir-local.ps1`;
- `status-local.ps1`;
- `logs-infra-local.ps1`;
- `parar-local.ps1`;
- `limpar-local.ps1`.

Os scripts antigos da Fase 1A foram mantidos como aliases. A execução exige Docker já disponível na máquina; esta fase não instala ferramentas.
