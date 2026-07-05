# Relatorio - hardening validadores sinteticos Bloco 31.1

## Resultado

OK - os validadores sinteticos foram corrigidos para nao aprovar por evidencia antiga quando o backend local esta indisponivel.

## API sintetica

- Arquivo: `scripts/local/validar-api-publica-sintetica-local.ps1`.
- Backend indisponivel por padrao: `VALIDATION_RESULT=PENDENTE_API_PUBLICA_SINTETICA_LOCAL`.
- Exit code esperado: 2.
- Reutilizacao de evidencia existente: somente com `-PermitirEvidenciaExistente`.
- Alerta obrigatorio nesse modo: `ALERTA_EVIDENCIA_EXISTENTE_REUTILIZADA`.

## SEO sintetico

- Arquivo: `scripts/local/validar-seo-sintetico-local.ps1`.
- Backend indisponivel por padrao: `VALIDATION_RESULT=PENDENTE_SEO_SINTETICO_LOCAL`.
- Exit code esperado: 2.
- Reutilizacao de evidencia existente: somente com `-PermitirEvidenciaExistente`.
- Alerta obrigatorio nesse modo: `ALERTA_EVIDENCIA_EXISTENTE_REUTILIZADA`.

## Limites

Os scripts permanecem locais, sinteticos e sem acesso a producao, VPS, banco de producao, dados reais, Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real ou API externa real.
