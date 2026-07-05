# Bloco 31.1 - hardening dos validadores sinteticos

## Objetivo

Corrigir de forma cirurgica os validadores sinteticos do Bloco 31 para evitar aprovacao por evidencia antiga quando o backend local estiver indisponivel.

## Contexto

- Bloco 31 concluiu E2E/API/SEO sintetico local.
- ZIP auditado do Bloco 31: `C:\Users\WpD\Desktop\topsv3-fase-BLOCO-31-2026-07-05-020816-742.zip`.
- SHA-256 auditado: `5a79fd40502b69a86b7eb8907c84cddd869df011f458250253b8c3334066451a`.
- Commit local do Bloco 30: `2acc60b`.
- Bloco 29 segue adiado para pre-staging/cutover.
- Quarentena sem `POST_DATA` segue proibida para staging final.

## Correcoes

- `README.md` passa a abrir com o estado real: Bloco 31 concluido e Bloco 31.1 como correcao de confiabilidade antes de checkpoint.
- `validar-api-publica-sintetica-local.ps1` nao retorna OK por relatorio antigo quando o backend esta indisponivel.
- `validar-seo-sintetico-local.ps1` nao retorna OK por relatorio antigo quando o backend esta indisponivel.
- Ambos retornam pendente com exit code 2 por padrao quando o backend local nao responde.
- Compatibilidade por evidencia existente fica restrita ao parametro explicito `-PermitirEvidenciaExistente`, com alerta documentado.
- `validar-e2e-local-descartavel.ps1` passa a usar prefixo default `topsv3-e2e-local`.
- O wrapper sintetico continua usando explicitamente `topsv3-e2e-sintetico`.

## Prefixos Docker

O E2E local descartavel aceita apenas prefixos com `topsv3-*`, bloqueia nomes vazios/genericos e recusa `cripto`, TopsWI ou nomes fora do espaco V3.

Recursos Docker de TopsWI/cripto, `topsv3-bloco29-*` e terceiros nao devem ser parados, removidos, conectados ou reaproveitados.

## Limites

Este bloco nao cria novo desenvolvimento funcional, migration, SQL, dados reais, restore, sanitizacao real, Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real, API externa, remote, push ou commit.

Pro nao e necessario para analisar este bloco local/sintetico, mas continua obrigatorio antes de homologacao/cutover real com dados reais/sanitizados, financeiro, Pix/Efi, webhooks ou producao.
