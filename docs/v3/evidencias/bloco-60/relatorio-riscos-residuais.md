# Relatorio - Riscos residuais Bloco 60

## Riscos

- O gate `BLOQUEADO_PARIDADE_VISUAL_PRODUCAO` permanece aberto ate revisao visual completa.
- Footer publico e admin, quando aplicavel, seguem fora deste bloco.
- A paridade final do wizard ainda depende de revisao humana/Pro contra a producao atual.
- O wizard local permanece sintetico e nao substitui homologacao real.

## Bloqueios preservados

- Homologacao real nao aprovada.
- Cutover/producao bloqueados.
- Restore completo do Bloco 29 segue pendente.
- Quarentena sem `POST_DATA` continua proibida como staging final.
- Dados reais, Pix/Efi real, pagamento, upload real, API externa real, VPS e producao seguem fora do escopo.

## Riscos adicionais do deploy HML

- O workflow esta pronto, mas nao foi executado neste bloco.
- O primeiro deploy depende de preparar manualmente usuario `topsv3`, Docker, Nginx e `/opt/topsv3/secrets/hml.env` na VPS.
- `PENDENTE_HTTPS_HML_ANTES_DO_TESTE_PUBLICO`: o Nginx versionado e bootstrap HTTP; o ambiente nao deve ser testado publicamente antes de certificado/HTTPS.
- O ambiente HML nasce bloqueado para indexacao; ainda assim `v3.esle.cloud` nao deve ser divulgado antes de revisao humana/Pro.
- Seed sintetico HML navegavel nao foi executado; se necessario, deve ser bloco proprio.
- Nenhum dado real, Pix/Efi real, webhook real, upload real, e-mail real ou WhatsApp real pode ser usado em HML sem autorizacao expressa e novo gate.
