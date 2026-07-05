# Bloco 33 - validacao sintetica do wizard Anuncie gratis

## Objetivo

Validar localmente o wizard publico `/anunciar` com dados sinteticos, em ambiente descartavel, depois do checkpoint local do Bloco 32.1.

Esta validacao nao exige Pro porque usa somente codigo local, fixture sintetica e analise visual/UX local. Pro continua obrigatorio antes de homologacao, cutover real, restore completo com dados reais/sanitizados, financeiro, Pix/Efi, webhooks, importador real ou producao.

## Checkpoint local

Antes das alteracoes do Bloco 33, foi criado o commit local:

- hash: `f6189f0`;
- mensagem: `test: valida render publico sintetico ate bloco 32.1`;
- remote: vazio;
- push: nao executado.

O README foi ajustado antes desse commit para refletir que o Bloco 32.1 estava concluido e aprovado localmente, mantendo os gates do Bloco 29, da quarentena e da revisao Pro.

## Escopo executado

- Auditoria do wizard `/anunciar` em desktop e mobile.
- Fluxo guiado validado da etapa inicial ate a etapa recebida.
- Validacao de mensagens amigaveis de campos obrigatorios.
- Preenchimento com dados sinteticos reservados.
- Etapa de midia confirmada como placeholder sem upload real.
- Revisao final confirmada sem enum tecnico de categoria.
- Submissao local confirmada como solicitacao nao publicada.
- API local confirmada sem publicacao automatica, upload real, pagamento, credito, Premium obrigatorio, e-mail real ou WhatsApp real.

## Ajustes pequenos permitidos

Foram feitos apenas ajustes leves e justificados:

- a revisao do wizard passou a exibir a categoria como texto publico (`Acompanhante`) em vez do valor tecnico;
- os checkboxes de confirmacao receberam `name` para melhorar semantica e permitir validacao automatizada;
- o validador local do wizard configura CORS local para a porta isolada do frontend de teste.

Nao houve redesign, nova paleta, nova tipografia, animacao automatica, botao flutuante, scroll lock ou mudanca estrutural de UX.

## Script criado

Script de validacao:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-wizard-anunciar-sintetico-local.ps1
```

Comportamento:

- sem `BaseUrl`, usa o wrapper E2E descartavel com prefixo Docker `topsv3-wizard-sintetico`;
- com `BaseUrl`, valida somente backend/frontend locais em localhost;
- nao instala dependencias;
- nao acessa producao, VPS, banco de producao, API externa, Efi real ou OpenAI;
- nao usa backup, dump, quarentena ou dado real.

## Evidencias

- E2E descartavel: `docs/v3/evidencias/bloco-33/relatorio-e2e-wizard-anunciar.md`
- Auditoria do wizard: `docs/v3/evidencias/bloco-33/relatorio-auditoria-wizard-anunciar.md`
- UI desktop/mobile: `docs/v3/evidencias/bloco-33/relatorio-ui-mobile-desktop-wizard.md`
- Validacoes: `docs/v3/evidencias/bloco-33/relatorio-validacoes.md`
- Checkpoint: `docs/v3/evidencias/bloco-33/relatorio-checkpoint-bloco-32-1.md`
- Riscos residuais: `docs/v3/evidencias/bloco-33/relatorio-riscos-residuais.md`
- Prints: `docs/v3/evidencias/bloco-33/prints/`

Prints gerados:

- `desktop-inicio.png`
- `desktop-intermediaria.png`
- `desktop-revisao.png`
- `desktop-pos-envio.png`
- `mobile-inicio.png`
- `mobile-intermediaria.png`
- `mobile-revisao.png`
- `mobile-pos-envio.png`

## Resultado

O wizard `/anunciar` foi aprovado localmente com dados sinteticos:

- fluxo guiado por etapas: OK;
- campos obrigatorios/mensagens: OK;
- voltar/continuar/enviar: OK;
- etapa de midia sem upload real: OK;
- submit local sem publicacao automatica: OK;
- stores ausentes do wizard: OK;
- sem pagamento/Pix/Efi/checkout: OK;
- sem Premium obrigatorio: OK;
- sem e-mail/WhatsApp real: OK;
- mobile sem scroll horizontal, elemento solto, scroll lock ou `document.body.style.overflow`: OK.

## Limites preservados

Nao houve producao, VPS, banco de producao, restore, `POST_DATA`, sanitizacao real, correcao de orfaos, quarentena como staging final, dump, dado real, upload real, pagamento real, Pix/Efi real, e-mail real, WhatsApp real, API externa, remote ou push.

O Bloco 33 nao iniciou fase posterior.
