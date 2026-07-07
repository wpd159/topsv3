# Relatorio - Validacoes Bloco 60

## Validacoes planejadas

- `scripts/local/validar-publico-renderizado-sintetico-local.ps1`.
- `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`.
- `scripts/local/validar-e2e-sintetico-local.ps1`.
- `scripts/local/validar-dados-sinteticos-v3-local.ps1`.
- `scripts/security/verificar-codificacao.ps1`.
- `scripts/security/verificar-arquivos-proibidos.ps1`.
- `scripts/security/verificar-segredos.ps1`.
- `git diff --check`.
- `git diff --cached --check`.
- `npm run lint`.
- `npm run build`.

## Resultado

- `scripts/local/validar-publico-renderizado-sintetico-local.ps1`: OK, com `VALIDATION_RESULT=OK_PUBLICO_RENDERIZADO_SINTETICO_LOCAL`.
- `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`: OK, com `VALIDATION_RESULT=OK_WIZARD_ANUNCIAR_SINTETICO_LOCAL`.
- `scripts/local/validar-e2e-sintetico-local.ps1`: OK, com `VALIDATION_RESULT=OK_E2E_SINTETICO_LOCAL`.
- `scripts/local/validar-dados-sinteticos-v3-local.ps1`: OK, com `VALIDATION_RESULT=OK_DADOS_SINTETICOS_LOCAL`.
- `npm run lint`: OK, sem warnings ou erros ESLint.
- `npm run build`: OK, Next.js compilou e gerou 27 paginas.
- `scripts/security/verificar-codificacao.ps1`: OK apos `git add`.
- `scripts/security/verificar-arquivos-proibidos.ps1`: OK apos `git add`.
- `scripts/security/verificar-segredos.ps1`: OK apos `git add`, com gitleaks real e fallback local sem achados.
- `git diff --check`: OK.
- `git diff --cached --check`: OK.

## Prints esperados

- Home desktop/mobile com logo.
- Cidade desktop/mobile com logo.
- Anuncio livre desktop/mobile com logo.
- `/anunciar` inicio desktop/mobile.
- `/anunciar` etapa intermediaria desktop/mobile.
- `/anunciar` revisao desktop/mobile.
- `/anunciar` pos-envio desktop/mobile.

## Prints gerados

Os prints foram copiados para `docs/v3/evidencias/bloco-60/prints`:

- `desktop-home-logo.png` e `mobile-home-logo.png`;
- `desktop-cidade-goiania-logo.png` e `mobile-cidade-goiania-logo.png`;
- `desktop-anuncio-livre-logo.png` e `mobile-anuncio-livre-logo.png`;
- `desktop-anunciar-inicio.png` e `mobile-anunciar-inicio.png`;
- `desktop-anunciar-intermediaria.png` e `mobile-anunciar-intermediaria.png`;
- `desktop-anunciar-revisao.png` e `mobile-anunciar-revisao.png`;
- `desktop-anunciar-pos-envio.png` e `mobile-anunciar-pos-envio.png`.

## Validacoes de deploy HML

Validacoes planejadas para o bloco de deploy HML:

- `scripts/deploy/validar-deploy-hml-local.ps1`;
- `scripts/security/verificar-codificacao.ps1`;
- `scripts/security/verificar-arquivos-proibidos.ps1`;
- `scripts/security/verificar-segredos.ps1`;
- `git diff --check`;
- `git diff --cached --check`;
- `mvn -q -DskipTests compile`;
- `mvn -q test`;
- `scripts/local/diagnosticar-toolchain-local.ps1`;
- `scripts/local/validar-build-local.ps1`;
- `npm run lint`;
- `npm run build`.

Resultado:

- `scripts/deploy/validar-deploy-hml-local.ps1`: OK, com `VALIDATION_RESULT=OK_DEPLOY_HML_LOCAL`.
- `scripts/security/verificar-codificacao.ps1`: OK apos `git add`.
- `scripts/security/verificar-arquivos-proibidos.ps1`: OK apos `git add`.
- `scripts/security/verificar-segredos.ps1`: OK apos `git add`, com gitleaks real e fallback local sem achados.
- `git diff --check`: OK.
- `git diff --cached --check`: OK apos `git add`.
- `git remote -v`: vazio.
- `scripts/local/diagnosticar-toolchain-local.ps1`: OK, com `VALIDATION_RESULT=OK_DIAGNOSTICO_TOOLCHAIN_LOCAL`; Maven local encontrado em `C:\Users\WpD\.topsv3-toolchain\maven\apache-maven-3.9.9\bin\mvn.cmd`.
- `scripts/local/validar-build-local.ps1`: OK, com `VALIDATION_RESULT=OK_BUILD_LOCAL`; backend compile/test OK e frontend lint/build OK pela toolchain local.
- `mvn -q -DskipTests compile`: coberto por `scripts/local/validar-build-local.ps1`, que usa Maven local existente sem instalar dependencias.
- `mvn -q test`: coberto por `scripts/local/validar-build-local.ps1`, que usa Maven local existente sem instalar dependencias.
- `npm run lint`: OK.
- `npm run build`: OK, Next.js compilou e gerou 27 paginas.

Observacao: `scripts/security/git-staged-utils.ps1` foi ajustado de forma restrita para aceitar referencias externas de secrets do deploy HML e o arquivo textual `deploy/hml/hml.env.example`, sem permitir `.env` real, valor secreto real, chave real, dump, backup ou log bruto.

## Complemento corretivo antes de deploy

- `SPRING_PROFILES_ACTIVE` HML corrigido de `local` para `homologacao`.
- Suporte ao profile `homologacao` confirmado em `backend/src/main/resources/application-homologacao.yml`.
- Endpoint real de health confirmado: `GET /api/health`.
- Workflow reforcado para excluir explicitamente `topsv3-auditoria-local`, `logs-brutos-nao-versionar`, `**/*.dump`, `**/*.backup`, `**/*.log`, `**/.env`, `**/*.pem`, `**/*.key` e `**/*.crt`.
- HTTPS HML registrado como `PENDENTE_HTTPS_HML_ANTES_DO_TESTE_PUBLICO`.
