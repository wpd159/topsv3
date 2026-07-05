# Checklist - Bloco 33.1 correcao textual wizard

## Escopo

- [x] Corrigir apenas textos publicos do wizard.
- [x] Preservar IDs internos sem acento.
- [x] Preservar rotas, DTOs, contratos e validacoes.
- [x] Preservar regra de negocio e seguranca.
- [x] Nao criar migration.
- [x] Nao alterar SQL de schema.
- [x] Nao executar commit.
- [x] Nao executar push.
- [x] Nao configurar remote.

## Textos criticos

- [x] `Revisao final` corrigido para `Revisão final`.
- [x] `Solicitacao recebida` corrigido para `Solicitação recebida`.
- [x] `Confirmacoes` corrigido para `Confirmações`.
- [x] `Etapa concluida.` corrigido para `Etapa concluída.`.
- [x] `botao final` corrigido para `botão final`.
- [x] Outros textos publicos simples do wizard revisados.

## Validador

- [x] `scripts/local/validar-wizard-anunciar-sintetico-local.ps1` reforcado para reprovar textos publicos criticos sem acento.
- [x] Validacao limitada ao texto renderizado.
- [x] IDs internos, rotas, nomes de arquivo e conteudo tecnico nao renderizado nao sao alvo da regra textual.

## Evidencias

- [x] `docs/v3/evidencias/bloco-33-1/relatorio-correcao-textual-wizard.md`
- [x] `docs/v3/evidencias/bloco-33-1/relatorio-validacoes.md`
- [x] `docs/v3/evidencias/bloco-33-1/relatorio-riscos-residuais.md`
- [x] Prints regenerados pelo validador sintetico do wizard.

## Pendencias

- [ ] Checkpoint local do Bloco 33/33.1 permanece pendente para bloco posterior.
- [ ] `gitleaks` real permanece pendente se nao estiver instalado no PATH; fallback local segue obrigatorio.
- [ ] Pro continua gate antes de homologacao/cutover real, dados reais/sanitizados, restore completo, financeiro, Pix/Efi, webhooks, importador real ou producao.
