# Checklist - Bloco 39 Age Gate e WhatsApp sintetico

- [x] Bloco 38 commitado localmente antes de iniciar validacao.
- [x] `git remote -v` vazio no checkpoint.
- [x] Sem push.
- [x] Sem producao, VPS, restore, dados reais ou API externa.
- [x] Validador Age Gate/WhatsApp criado.
- [x] Anuncio `LIVRE` validado sem age gate.
- [x] Anuncio `BLOQUEADO` protegido antes da confirmacao de idade.
- [x] Menor de 18 anos bloqueado.
- [x] Data invalida bloqueada com status 400.
- [x] Confirmacao adulta sintetica emite cookie HttpOnly SameSite=Lax.
- [x] WhatsApp liberado apenas via backend.
- [x] Frontend nao monta `wa.me`.
- [x] Stories exigem confirmacao de idade quando rota local existe.
- [x] Sem `document.body.style.overflow`, scroll lock ou storage de idade no frontend publico.
- [x] Sem numero real, e-mail real, upload real, Pix/Efi real, pagamento real ou webhook real.
- [ ] Homologacao/cutover com dados reais ou sanitizados segue pendente de revisao Pro futura.
