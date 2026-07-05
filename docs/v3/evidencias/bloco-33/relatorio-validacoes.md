# Relatorio - validacoes Bloco 33

## Resultado geral

- Resultado: OK
- Commit de checkpoint executado: `f6189f0`
- Remote configurado: nao
- Push executado: nao
- Dados reais usados: nao
- Producao/VPS/API externa acessadas: nao
- Banco de producao acessado: nao
- Quarentena usada como staging final: nao
- Fase posterior iniciada: nao

## Validacoes sinteticas e renderizadas

- `scripts/local/validar-dados-sinteticos-v3-local.ps1`: OK
- `scripts/local/validar-e2e-sintetico-local.ps1`: OK
- `scripts/local/validar-publico-renderizado-sintetico-local.ps1`: OK
- `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`: OK
- API sintetica com `BaseUrl=http://127.0.0.1:19999`: PENDENTE esperado, `EXIT_CODE=2`
- SEO sintetico com `BaseUrl=http://127.0.0.1:19999`: PENDENTE esperado, `EXIT_CODE=2`

## Wizard `/anunciar`

- Total de checks automatizados do wizard: 132
- Falhas: 0
- Desktop inicio/intermediaria/revisao/pos-envio: OK
- Mobile inicio/intermediaria/revisao/pos-envio: OK
- Stores ausentes: OK
- Upload real ausente: OK
- Pagamento/Pix/Efi/checkout ausentes: OK
- Premium obrigatorio ausente: OK
- E-mail/WhatsApp real ausentes: OK
- Autopublicacao ausente: OK
- Sem scroll horizontal, scroll lock ou `document.body.style.overflow`: OK

## Validacoes estaticas e build

- `scripts/local/diagnosticar-toolchain-local.ps1`: OK
- `scripts/local/validar-build-local.ps1`: OK
- `scripts/local/validar-persistencia-jpa-estatica.ps1`: OK
- `scripts/local/validar-ui-mobile-estatica.ps1`: OK
- `scripts/local/validar-seo-publico-local.ps1`: OK
- `scripts/local/validar-rotas-publicas-seo-local.ps1`: OK
- `scripts/local/validar-layout-publico-renderizado.ps1`: OK
- `scripts/local/validar-mapa-preservacao-seo-local.ps1`: OK
- `scripts/local/validar-migrations-sql-estatico.ps1`: OK
- `scripts/local/validar-fonte-importacao-local.ps1`: OK
- Backend `mvn -q -DskipTests compile`: OK
- Backend `mvn -q test`: OK
- Frontend `npm run lint`: OK
- Frontend `npm run build`: OK

## Scanners

- `scripts/security/verificar-codificacao.ps1`: executado antes do staging; sem staged no momento inicial
- `scripts/security/verificar-arquivos-proibidos.ps1`: executado antes do staging; sem staged no momento inicial
- `scripts/security/verificar-segredos.ps1`: fallback local executado; `gitleaks` pendente no PATH
- Scanners finais serao repetidos apos `git add .` antes do ZIP

## Docker

- `topsv3-e2e-sintetico-*`: usado em E2E sintetico, container/rede removidos
- `topsv3-render-sintetico-*`: usado em render publico sintetico, container/rede removidos
- `topsv3-wizard-sintetico-*`: usado em validacao do wizard, container/rede removidos
- Volume persistente criado: nao
- TopsWI/cripto/terceiros alterados: nao
- `docker prune` ou `docker compose down`: nao

## Observacoes

- O validador do wizard configura CORS local apenas para permitir o frontend de teste em porta isolada.
- `gitleaks` real permanece pendente se nao estiver instalado; fallback local segue obrigatorio.
- Pro continua gate antes de homologacao/cutover real, dados reais/sanitizados, restore completo, financeiro, Pix/Efi, webhooks, importador real ou producao.
