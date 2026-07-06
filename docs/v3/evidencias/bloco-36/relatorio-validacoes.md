# Relatorio - validacoes Bloco 36

## Validacoes executadas

- `scripts/local/validar-premium-beneficios-sintetico-local.ps1`: OK.
- `scripts/local/validar-dados-sinteticos-v3-local.ps1`: OK.
- `scripts/local/validar-e2e-sintetico-local.ps1`: OK.
- `scripts/local/validar-publico-renderizado-sintetico-local.ps1`: OK.
- `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`: OK.
- `scripts/local/validar-admin-moderacao-sintetica-local.ps1`: OK.
- `scripts/security/verificar-codificacao.ps1`: OK.
- `scripts/security/verificar-arquivos-proibidos.ps1`: OK.
- `scripts/security/verificar-segredos.ps1`: OK com fallback local quando `gitleaks` nao estava no PATH.
- Backend: `mvn -q -DskipTests compile`: `mvn` global pendente no PATH; executado com Maven local `C:\Users\WpD\.topsv3-toolchain\maven\apache-maven-3.9.9\bin\mvn.cmd`: OK.
- Backend: `mvn -q test`: `mvn` global pendente no PATH; executado com Maven local `C:\Users\WpD\.topsv3-toolchain\maven\apache-maven-3.9.9\bin\mvn.cmd`: OK.
- Frontend: `npm run lint`: OK.
- Frontend: `npm run build`: OK.
- `git diff --check`: OK.
- `git diff --cached --check`: OK.
- `git remote -v`: vazio.

## Observacoes

- `gitleaks` real segue pendente no PATH; o fallback conservador local foi executado sem achados.
- Os recursos Docker usados pelo validador Premium foram descartaveis e usaram prefixo `topsv3-premium-sintetico-*`.
- Nenhum recurso TopsWI/cripto foi alterado.
