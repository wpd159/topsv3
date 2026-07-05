# Checklist Bloco 32.1 - correcao de texto tecnico publico

| Item | Resultado esperado |
| --- | --- |
| Pro necessario para analise local sintetica | Nao |
| Commit executado neste bloco | Nao |
| Remote Git vazio | Sim |
| Dados reais usados | Nao |
| Producao/VPS/API externa acessadas | Nao |
| Restore, `POST_DATA` ou sanitizacao real | Nao |
| Regra de seguranca alterada no frontend | Nao |
| Estado tecnico `conteudo_autorizado` visivel | Nao |
| Pendencia tecnica de WhatsApp visivel | Nao |
| Enum/status/snake_case tecnico visivel | Nao |
| Anuncio `BLOQUEADO` com WhatsApp publico indevido | Nao |
| Redesign, nova paleta ou nova tipografia | Nao |
| Scroll lock ou `document.body.style.overflow` | Nao |
| Validador renderizado endurecido | Sim |
| Prints desktop/mobile regenerados | Sim |
| ZIP limpo gerado | Sim |

## Validacoes

- Dados sinteticos locais.
- E2E sintetico local.
- Auditoria renderizada publica sintetica.
- API/SEO sinteticos negativos com porta indisponivel retornando pendente/exit 2.
- Toolchain, build, JPA estatico, UI mobile estatica, SEO publico, rotas SEO, layout renderizado, mapa SEO, migrations SQL estatico e fonte de importacao local.
- Scanners de codificacao, arquivos proibidos e secrets.
- Backend compile/test.
- Frontend lint/build.

## Pendencias residuais

- `gitleaks` real segue pendente no PATH quando indisponivel localmente.
- Bloco 29 segue adiado para pre-staging/cutover.
- Quarentena sem `POST_DATA` nao e staging final.
- Pro continua obrigatorio antes de homologacao/cutover real.
