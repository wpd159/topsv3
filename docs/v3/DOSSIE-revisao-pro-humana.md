# DOSSIE - Revisao Pro/humana

## Objetivo

Orientar a revisao Pro/humana antes de qualquer homologacao real. A revisao deve decidir se os contratos locais estao suficientes para avancar para blocos com ambiente real, dados reais/sanitizados ou integracoes externas.

## Materiais de entrada

- `docs/v3/DOSSIE-final-ciclo-local-sintetico.md`
- `docs/v3/HOMOLOGACAO-matriz-prontidao.md`
- `docs/v3/HOMOLOGACAO-go-no-go.md`
- `docs/v3/HOMOLOGACAO-contrato-ambiente.md`
- `docs/v3/HOMOLOGACAO-storage-upload-cdn.md`
- `docs/v3/HOMOLOGACAO-importacao-real-dryrun.md`
- `docs/v3/HOMOLOGACAO-seo-cutover.md`
- `docs/v3/HOMOLOGACAO-financeiro-pix-efi-webhooks.md`
- `docs/v3/HOMOLOGACAO-backup-rollback.md`
- `docs/v3/HOMOLOGACAO-monitoramento-operacional.md`

## Perguntas de revisao

- O Bloco 29 deve ser retomado por novo backup consistente ou correcao da origem/backup?
- A quarentena sem `POST_DATA` permanece apenas como insumo auxiliar?
- Os contratos de homologacao cobrem secrets, CORS, cookies, CSRF, banco isolado e rollback?
- O contrato storage/upload/CDN impede documento privado publicavel?
- O dry-run de importacao cobre slugs, cidades/bairros, status, classificacao, midia, documentos, Premium, metricas, duplicidade e orfaos/FK?
- As 45 URLs desconhecidas de SEO ainda existem? Qual decisao sera tomada?
- Pix/Efi/webhooks possuem idempotencia, conciliacao, ledger e rollback suficientes?
- Monitoramento/auditoria JSON estao suficientes para dados reais/sanitizados?
- A matriz Go/No-Go bloqueia adequadamente producao?

## Itens que exigem decisao formal

- Autorizacao de dados reais/sanitizados.
- Autorizacao de restore completo.
- Autorizacao de staging real.
- Autorizacao de Pix/Efi homologacao.
- Autorizacao de storage/CDN/upload real.
- Autorizacao de importador real.
- Autorizacao de cutover.

## Criterios de aprovacao Pro

- Nenhum segredo ou dado real no Git.
- Backup/rollback planejado e testavel.
- Ambiente de homologacao isolado.
- Dados reais/sanitizados autorizados formalmente quando aplicavel.
- Logs e auditoria sem payload sensivel.
- Documento privado nunca publicavel.
- SEO com mapa e rollback.
- Financeiro com idempotencia e conciliacao.
- Go/No-Go objetivo.

## Saida esperada

- Aprovado para proximo bloco local/documental.
- Aprovado para bloco de homologacao controlada.
- Reprovado com ajustes obrigatorios.
- Bloqueado ate decisao juridica, operacional ou tecnica.
