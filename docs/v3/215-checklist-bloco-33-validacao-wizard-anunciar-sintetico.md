# Checklist - Bloco 33 validacao wizard Anuncie gratis

## Checkpoint

- [x] Diagnostico inicial executado.
- [x] Remote confirmado vazio.
- [x] `git diff --check` aprovado.
- [x] `git diff --cached --check` aprovado.
- [x] README ajustado antes do checkpoint.
- [x] Commit local criado: `f6189f0`.
- [x] Push nao executado.

## Wizard `/anunciar`

- [x] Fluxo guiado por etapas validado.
- [x] Inicio desktop/mobile validado.
- [x] Etapa intermediaria desktop/mobile validada.
- [x] Revisao desktop/mobile validada.
- [x] Pos-envio desktop/mobile validado.
- [x] Campos obrigatorios e mensagens amigaveis validados.
- [x] Botoes voltar/continuar/enviar validados.
- [x] Categoria exibida como texto publico, sem enum tecnico.
- [x] Etapa de midia sem upload, camera, arquivo ou documento real.
- [x] Submit local sem publicacao automatica.
- [x] Stores ausentes do wizard.
- [x] Premium nao obrigatorio.
- [x] Pagamento/Pix/Efi/checkout ausentes.
- [x] E-mail e WhatsApp real nao enviados.

## Mobile e visual

- [x] Sem redesign.
- [x] Sem nova paleta.
- [x] Sem nova tipografia.
- [x] Sem animacao automatica.
- [x] Sem botao flutuante.
- [x] Sem scroll horizontal.
- [x] Sem scroll lock.
- [x] Sem `document.body.style.overflow`.
- [x] Sem `position: fixed`, `absolute` ou `sticky` publico indevido.
- [x] CTA dentro do fluxo normal.
- [x] Placeholder de midia sem salto visual detectado.

## Dados e ambiente

- [x] Dados reais nao usados.
- [x] Backup/dump nao usado.
- [x] Restore nao executado.
- [x] Quarentena nao usada como staging final.
- [x] Recursos Docker efemeros com prefixo `topsv3-wizard-sintetico`.
- [x] Recursos TopsWI/cripto nao alterados.
- [x] Producao/VPS/API externa nao acessadas.

## Validacoes

- [x] `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`
- [x] `scripts/local/validar-e2e-sintetico-local.ps1`
- [x] `scripts/local/validar-publico-renderizado-sintetico-local.ps1`
- [x] Validadores negativos API/SEO com backend indisponivel retornaram pendente/exit 2.
- [x] Scanners de codificacao, arquivos proibidos e secrets executados.
- [x] Build/test backend e lint/build frontend executados quando a toolchain local permitiu.
- [x] ZIP final gerado fora do repositorio.

## Pendencias

- [ ] `gitleaks` real continua pendente se nao estiver instalado no PATH; fallback local segue obrigatorio.
- [ ] Revisao Pro continua gate antes de homologacao/cutover real, financeiro, Pix/Efi, webhooks, restore completo, dados reais/sanitizados e producao.
