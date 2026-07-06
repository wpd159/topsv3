# Relatorio de riscos residuais do Bloco 44

## Riscos residuais

- `gitleaks` real esta instalado localmente, mas depende do PATH de usuario/maquina estar carregado em novos shells.
- `frontend/.next` foi removido como artefato ignorado; novo build frontend pode recriar a pasta e novos scans `--source . --no-git` podem incluir artefatos gerados se eles existirem.
- Fallback local continua secundario, nao substitui `gitleaks` real.
- Bloco 29, restore completo, staging, dados reais/sanitizados, Pix/Efi real, webhooks, API externa e producao continuam fora do escopo.

## Mitigacao

- `scripts/security/verificar-segredos.ps1` recarrega PATH de Machine/User antes de procurar `gitleaks`.
- Scans finais devem ser executados apos limpar artefatos gerados ignorados quando a intencao for validar o repositorio fonte.
- Manter `gitleaks` real como gate antes de homologacao/producao.
