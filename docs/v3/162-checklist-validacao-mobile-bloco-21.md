# Checklist de validacao mobile - Bloco 21

## Estabilidade geral

- [ ] Sem scroll horizontal.
- [ ] Cards dentro da viewport.
- [ ] CTAs dentro do fluxo normal da pagina.
- [ ] Sem elemento flutuante solto.
- [ ] Sem animacao automatica.
- [ ] Sem carrossel automatico.
- [ ] Sem scroll lock.
- [ ] Sem uso de `document.body.style.overflow`.
- [ ] Sem barra fixa cobrindo conteudo.
- [ ] Sem `sticky`, `fixed` ou `absolute` nao justificado.
- [ ] Sem sobreposicao de header, footer, card ou CTA.
- [ ] Sem mudanca brusca de altura apos carregamento.
- [ ] Textos longos com quebra adequada.

## Rotas publicas

- [ ] Pagina de anuncio estavel no mobile.
- [ ] Listagem por cidade estavel no mobile.
- [ ] Listagem por bairro estavel no mobile.
- [ ] Age gate dentro do fluxo e sem bloquear scroll.
- [ ] Stories bloqueados sem deslocar layout.
- [ ] Placeholder de midia com dimensao estavel e sem salto visual.
- [ ] CTA de WhatsApp dentro do fluxo, sem sobrepor midia ou texto.

## Validacao estatica

- [ ] Executar `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-ui-mobile-estatica.ps1`.
- [ ] Registrar alertas, se existirem.
- [ ] Justificar qualquer uso de `position: fixed`, `position: absolute`, `position: sticky`, `100vw`, `@keyframes`, `animation`, `transform` ou `translate`.
- [ ] Confirmar que nenhum alerta representa instabilidade mobile.

## Justificativas registradas

Nenhuma justificativa ativa registrada neste momento.

## Resultado da implementacao do Bloco 21

- [x] Sem scroll horizontal esperado por codigo estatico.
- [x] Cards usam grid responsivo com largura contida.
- [x] CTAs permanecem dentro do fluxo normal da pagina.
- [x] Sem elemento flutuante solto.
- [x] Sem animacao automatica.
- [x] Sem carrossel automatico.
- [x] Sem scroll lock.
- [x] Sem `document.body.style.overflow`.
- [x] Sem barra fixa cobrindo conteudo.
- [x] Sem `sticky`, `fixed` ou `absolute`.
- [x] Sem sobreposicao de header, footer, card ou CTA.
- [x] Textos longos usam quebra adequada.
- [x] Pagina de anuncio usa placeholders e CTA dentro do fluxo.
- [x] Listagem por cidade usa grid responsivo.
- [x] Listagem por bairro usa grid responsivo.
- [x] Age gate permanece dentro do fluxo.
- [x] Stories bloqueados continuam dentro do fluxo.
- [x] Placeholder de midia usa dimensao estavel.
- [x] `scripts/local/validar-ui-mobile-estatica.ps1` retornou `VALIDATION_RESULT=OK_UI_MOBILE_ESTATICA` na validacao inicial da implementacao.

## Complemento Bloco 21.1

- [x] Age gate inicia com data vazia.
- [x] Botao `Confirmar idade` inicia desabilitado sem data valida.
- [x] Stories usam a mesma regra de data vazia.
- [x] Cards e paineis publicos usam `min-width: 0` e `max-width: 100%`.
- [x] Textos longos usam quebra segura.
- [x] Prints mobile gerados sem elemento flutuante do app.
- [x] CTA de WhatsApp permanece dentro do fluxo.
- [x] Placeholder de midia permanece com dimensao estavel.
