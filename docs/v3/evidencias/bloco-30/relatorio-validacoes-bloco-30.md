# Relatorio - validacoes Bloco 30

## Validacoes executadas com sucesso

- `scripts/local/gerar-dados-sinteticos-v3-local.ps1`: OK.
- `scripts/local/validar-dados-sinteticos-v3-local.ps1`: OK.
- `scripts/local/diagnosticar-toolchain-local.ps1`: OK.
- `scripts/local/validar-build-local.ps1`: OK.
- `scripts/local/validar-persistencia-jpa-estatica.ps1`: OK.
- `scripts/local/validar-ui-mobile-estatica.ps1`: OK.
- `scripts/local/validar-seo-publico-local.ps1`: OK.
- `scripts/local/validar-rotas-publicas-seo-local.ps1`: OK.
- `scripts/local/validar-layout-publico-renderizado.ps1`: OK.
- `scripts/local/validar-mapa-preservacao-seo-local.ps1`: OK.
- `scripts/local/validar-migrations-sql-estatico.ps1`: OK.
- `scripts/local/validar-fonte-importacao-local.ps1`: OK.
- `git diff --check`: OK.
- `git diff --cached --check`: OK.
- Backend direto com Maven local: `mvn -q -DskipTests compile`: OK.
- Backend direto com Maven local: `mvn -q test`: OK.
- Frontend direto: `npm run lint`: OK.
- Frontend direto: `npm run build`: OK.

## Scanners

Antes do stage do Bloco 30, os scanners foram executados e nao havia arquivos staged para inspecionar. A repeticao apos `git add .` deve constar no ZIP final e no resumo de entrega.

- `scripts/security/verificar-codificacao.ps1`: sem staged antes do stage.
- `scripts/security/verificar-arquivos-proibidos.ps1`: sem staged antes do stage.
- `scripts/security/verificar-segredos.ps1`: `gitleaks` ausente no PATH; fallback local sem staged antes do stage.

## Validacoes nao executaveis por politica do Bloco 30

- `scripts/local/validar-e2e-local-descartavel.ps1`: nao executado porque o Bloco 30 proibe mexer em Docker.

## Validacao HTTP pendente

- `scripts/local/validar-api-publica-local.ps1`: executado, mas ficou sem retorno ate timeout operacional e foi encerrado cirurgicamente.
- Evidencia adicional: `Test-NetConnection 127.0.0.1:8080` retornou `TcpTestSucceeded=False`.
- Motivo: a API local nao estava em execucao e o perfil `local` depende de PostgreSQL; o Bloco 30 proibe mexer em Docker e nao autoriza criar ambiente descartavel para esse smoke.

## Resultado final

`STATUS_VALIDACOES_BLOCO_30=OK_EXECUTAVEL_COM_PENDENCIA_OPERACIONAL_API_E_E2E_DOCKER`

As validacoes executaveis sem Docker, sem banco descartavel e sem producao passaram. O E2E descartavel e o smoke HTTP completo ficam adiados para bloco posterior que autorize ambiente local descartavel.
