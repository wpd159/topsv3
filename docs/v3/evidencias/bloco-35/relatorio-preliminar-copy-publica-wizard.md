# Relatorio - preliminar de copy publica do wizard

## Motivo

O Bloco 34 ficou pendente de checkpoint porque a UI publica do wizard `/anunciar` ainda exibia textos de bastidor:

- `V3 local`;
- `dados sinteticos locais`;
- `A producao destaca`;
- `producao observavel`;
- `ambiente local`.

## Correcoes aplicadas

- Status inicial do wizard:
  - antes: `Comece seu cadastro com dados sinteticos locais.`
  - depois: `Comece seu cadastro preenchendo as informacoes principais.`
- Descricao inicial:
  - antes: texto citando producao e V3 local.
  - depois: `Crie seu anuncio em etapas simples. O envio e gratuito e passa por revisao antes de qualquer publicacao.`
- Lateral de confianca:
  - antes: `Fotos, videos e pagamentos ficam fora deste fluxo local.`
  - depois: `Fotos, videos e pagamentos ficam fora deste cadastro inicial.`
- Etapa "Fotos e videos":
  - antes: `A producao valoriza midia revisada, mas esta etapa local nao envia arquivo real.`
  - depois: `As midias passam por revisao antes de aparecerem publicamente.`

## Escopo

Alteracao apenas de apresentacao publica. Nenhuma rota, DTO, contrato, regra de negocio, backend, seguranca, RBAC, migration ou SQL foi alterado.

## Validacoes

- Busca nos componentes publicos do wizard: sem termos de bastidor.
- Validador do wizard reforcado para reprovar `A producao valoriza`, `esta etapa local` e `arquivo real` quando renderizados ao visitante.
- Wizard sintetico: OK.
- Render publico sintetico: OK.
- E2E sintetico: OK.
- Dados sinteticos: OK.

## Confirmacoes

- Producao alterada: nao.
- Dados reais usados: nao.
- Upload/pagamento/Pix/Efi/e-mail/WhatsApp real: nao.
- Publicacao automatica: nao.
- Checkpoint corrigido do Bloco 34: `9b677ea`.
