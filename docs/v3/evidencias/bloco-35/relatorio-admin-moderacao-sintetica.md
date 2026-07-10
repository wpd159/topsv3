# Relatorio admin/moderacao sintetica local

- Resultado: OK_ADMIN_MODERACAO_SINTETICA_LOCAL
- Backend local: http://127.0.0.1:18135
- Frontend local: http://127.0.0.1:18335
- Smoke base: validar-api-publica-local.ps1
- Smoke base OK: True
- UI admin OK: True
- Prefixo Docker operacional esperado no wrapper: topsv3-admin-sintetico-*
- Recursos topsv3-bloco29 usados como staging: nao
- TopsWI/cripto detectado: 2
- TopsWI/cripto alterado: nao
- Docker prune/compose down executado: nao
- Producao/VPS/dados reais usados: nao

## Fluxos cobertos pelo smoke base

- Login admin local com sessao/cookie.
- Bloqueio de admin sem sessao.
- RBAC para ADMIN, MODERADOR, COMERCIAL e USUARIO sinteticos.
- Wizard /api/public/anunciar cria anuncio pendente e revisao aberta, sem publicacao automatica.
- Admin le anuncio criado pelo wizard como PENDENTE_REVISAO e revisao ABERTA.
- Listagem/detalhe de anuncios, midias e revisoes administrativas.
- APROVAR/REPROVAR revisao local, com auditoria.
- REPROVAR exige motivo.
- SOLICITAR_AJUSTE local sem decisao final indevida.
- Remeter anuncio para revisao local.
- Outbox read-only, preview sanitizado e simulacao local sem envio externo.
- Auditoria mascara e-mail, contato e documento em motivos sinteticos.
- Sem hard delete, upload, e-mail real, WhatsApp real, pagamento real, Pix/Efi real ou API externa.

## Saida do smoke base

- Validacao smoke HTTP da API publica local
- BaseUrl=http://127.0.0.1:18135
- Total de verificacoes: 1080
- Verificacoes OK: 1080
- Verificacoes com falha: 0
- VALIDATION_RESULT=OK_API_PUBLICA_LOCAL

## Resultado do wrapper descartavel

- Relatorio E2E descartavel: C:\topsv3\docs\v3\evidencias\bloco-35\relatorio-e2e-admin-moderacao-sintetica.md
- Exit code E2E: 0
- Prefixo Docker usado: topsv3-admin-sintetico-*
- Recurso topsv3-bloco29 usado: nao
- TopsWI/cripto alterado: nao
