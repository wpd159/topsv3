# Checklist Bloco 31.1 - hardening dos validadores sinteticos

| Item | Resultado |
| --- | --- |
| README reflete Bloco 31/31.1 | OK |
| Validador API nao aprova por evidencia antiga por padrao | OK |
| Validador SEO nao aprova por evidencia antiga por padrao | OK |
| Backend indisponivel retorna pendente/exit 2 | OK |
| Parametro `-PermitirEvidenciaExistente` e explicito e desativado por padrao | OK |
| Prefixo default do E2E base | OK - `topsv3-e2e-local` |
| Wrapper sintetico preserva prefixo | OK - `topsv3-e2e-sintetico` |
| Bloco 29 segue adiado | OK |
| Quarentena sem `POST_DATA` segue proibida como staging final | OK |
| Dados reais usados | NAO |
| Producao, VPS ou banco de producao acessados | NAO |
| Restore, sanitizacao real ou correcao de orfaos | NAO |
| Docker prune ou compose down | NAO |
| Remote, push ou commit | NAO |

## Pendencias

- `gitleaks` real segue pendente no PATH.
- Pro continua obrigatorio antes de homologacao/cutover real.
- Bloco 29 continua gate adiado para pre-staging/cutover.
