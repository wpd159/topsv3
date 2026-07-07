# Relatorio - Wizard /anunciar

## Referencias do clone

- `src/features/anuncio-wizard/anuncio-wizard.tsx`.
- `src/features/anuncio-wizard/components/wizard-ui.tsx`.
- `src/features/anuncio-wizard/components/wizard-final-review.tsx`.
- Componentes de etapa em `src/features/anuncio-wizard/components/`.

## Adaptacoes V3

- Cabecalho visual de card para reforcar a hierarquia do wizard.
- Progresso reestilizado como barras de etapas, preservando os oito passos e o `aria-current`.
- Campos com borda arredondada, foco visivel e densidade mais proxima da producao.
- Botoes primario/secundario mais proximos do CTA de producao.
- Area lateral mantida como bloco de confianca, com acabamento visual menos tecnico.
- Revisao e pos-envio preservam o fluxo existente e a mensagem de ausencia de autopublicacao.

## Nao alterado

- Nenhum contrato, DTO, rota, backend, banco ou regra de validacao foi alterado.
- O wizard continua sem upload real, pagamento, Pix/Efi, checkout, WhatsApp real, e-mail real, Premium obrigatorio, stores ou publicacao automatica.

## Clone

O clone local ja apresentava tres arquivos modificados antes do Bloco 60 e permaneceu assim:

- `docs/deploy.md`;
- `src/components/anuncios/editar/anuncio-edit-media.tsx`;
- `src/features/anuncio-wizard/components/wizard-step-fotos.tsx`.
