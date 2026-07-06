# Relatorio de pendencias para homologacao e cutover

## Pendencias obrigatorias

- Bloco 29 permanece materialmente aberto e adiado para pre-staging/cutover.
- Quarentena sanitizada sem `POST_DATA` continua proibida para staging final.
- E necessario novo backup consistente ou decisao segura aprovada para restauracao completa antes de staging final.
- Dados reais/sanitizados exigem gate proprio, revisao Pro quando aplicavel e proibicao de versionar dados sensiveis.
- Flyway real e schema final seguem dependentes dos gates de homologacao.
- Autenticacao/RBAC de producao exige CSRF, cookie seguro, rate limit, politica de credencial, auditoria e logs revisados.
- Auditoria JSON completa permanece pendencia Pro antes de homologacao/producao.
- CDN/storage publico exige politica aprovada, separacao de midia publica/privada e protecao de documentos.
- Pix/Efi real exige homologacao propria, credenciais fora do Git, webhook revisado e logs sem payload sensivel.
- Importador real exige fonte autorizada, diretorio fora do workspace e plano de rollback.
- SEO real exige mapa completo de URLs, 301 testado, canonical/sitemap/robots aprovados, Search Console e rollback.
- Backup, rollback, observabilidade e plano operacional de cutover precisam ser aprovados antes de producao.

## Bloqueios explicitos

- Nao usar banco de quarentena como staging final.
- Nao usar producao como bancada de teste.
- Nao executar restore, sanitizacao, importador real, Pix/Efi real, webhooks, upload real, e-mail real, WhatsApp real ou API externa sem bloco proprio e autorizacao expressa.
