# Relatorio - validacoes Bloco 35

## Validacoes executadas

- `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`: OK.
- `scripts/local/validar-publico-renderizado-sintetico-local.ps1`: OK.
- `scripts/local/validar-e2e-sintetico-local.ps1`: OK.
- `scripts/local/validar-dados-sinteticos-v3-local.ps1`: OK.
- `scripts/local/validar-admin-moderacao-sintetica-local.ps1`: OK.
- `scripts/local/validar-e2e-local-descartavel.ps1` via wrapper Bloco 35: OK.
- `scripts/local/validar-api-publica-sintetica-local.ps1 -BaseUrl http://127.0.0.1:19999`: PENDENTE esperado por backend indisponivel.
- `scripts/local/validar-seo-sintetico-local.ps1 -BaseUrl http://127.0.0.1:19999`: PENDENTE esperado por backend indisponivel.
- `scripts/local/diagnosticar-toolchain-local.ps1`: OK.
- `scripts/local/validar-build-local.ps1`: OK.
- `scripts/local/validar-persistencia-jpa-estatica.ps1`: OK.
- `scripts/local/validar-ui-mobile-estatica.ps1`: OK.
- `scripts/local/validar-seo-publico-local.ps1`: OK.
- `scripts/local/validar-rotas-publicas-seo-local.ps1`: OK apos atualizar o gate para o admin local validado no Bloco 35.
- `scripts/local/validar-layout-publico-renderizado.ps1`: OK.
- `scripts/local/validar-mapa-preservacao-seo-local.ps1`: OK.
- `scripts/local/validar-migrations-sql-estatico.ps1`: OK.
- `scripts/local/validar-fonte-importacao-local.ps1`: OK.
- `scripts/security/verificar-codificacao.ps1`: OK antes do stage; repetido apos stage.
- `scripts/security/verificar-arquivos-proibidos.ps1`: OK antes do stage; repetido apos stage.
- `scripts/security/verificar-segredos.ps1`: OK antes do stage com fallback quando `gitleaks` nao estava no PATH; repetido apos stage.
- Backend direto: `mvn -q -DskipTests compile`: OK.
- Backend direto: `mvn -q test`: OK.
- Frontend direto: `npm run lint`: OK.
- Frontend direto: `npm run build`: OK.
- `git diff --check`: OK.
- `git diff --cached --check`: OK antes do checkpoint e sera repetido apos stage.
- `git remote -v`: vazio.

## Resultado admin/moderacao

- Smoke HTTP base: 1089 verificacoes OK.
- Auditoria local de moderacao: 9 eventos sanitizados.
- Decisoes finais de revisao: 4.
- Outbox local de moderacao: 2 eventos pendentes preservados e 1 processado por simulacao local.
- Prints gerados: desktop/mobile de `/admin` e `/admin/moderacao`.
- Scroll horizontal admin: ausente.
- Scroll lock admin: ausente.
- Texto tecnico/sensivel renderizado admin: ausente.

## Docker e processos

- Docker usado em E2E/render/wizard/admin apenas com recursos descartaveis e prefixos locais proprios.
- `topsv3-admin-sintetico-*`: container e rede removidos ao final.
- Recursos `cripto-*`/TopsWI: detectados apenas por listagem e preservados.
- Nenhum `docker prune`, `docker compose down`, volume prune, network prune ou alteracao em recurso de outro projeto foi executado.

## ZIP

Este relatorio sera atualizado implicitamente pelo pacote final com `RELATORIO-VALIDACOES.md` gerado por `scripts/entrega/criar-pacote-revisao.ps1`.
