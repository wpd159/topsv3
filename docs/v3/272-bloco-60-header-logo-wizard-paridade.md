# Bloco 60 - Header, logo e wizard com paridade visual

## Escopo

O Bloco 60 faz checkpoint local do Bloco 59 e aproxima visualmente o header publico e o wizard `/anunciar` da producao observavel pelo clone local `C:\clone\topsdojob-frontend`.

## Checkpoint

- Commit do Bloco 59: `3111afc` (`style: transplanta cards detalhe visual ate bloco 59`).
- `git remote -v` em `C:\topsv3`: vazio.
- Push: nao executado.

## Fonte visual

- Clone consultado somente leitura: `C:\clone\topsdojob-frontend`.
- Header de referencia: `src/components/layout/header.tsx`.
- Wizard de referencia: `src/features/anuncio-wizard/anuncio-wizard.tsx` e componentes em `src/features/anuncio-wizard/components/`.
- Logo fonte local: `C:\clone\topsdojob-frontend\public\logo-finallllll.webp`.
- Logo V3 criada para referencia publica: `frontend/public/logo.webp`, servida como `/logo.webp`.

## Adaptacoes V3

- Header publico passa a usar a logomarca real por `next/image`, referenciando `/logo.webp`.
- Logo tem dimensoes responsivas e fica dentro do fluxo normal do header.
- CTA `PUBLICAR SEU ANUNCIO` permanece no header sem botao flutuante.
- Wizard recebe cabecalho visual de card, progresso por barras, campos arredondados, botoes mais proximos da producao e lateral de confianca com acabamento visual.
- Fluxo funcional do wizard nao foi alterado: sem upload real, pagamento, Pix/Efi, WhatsApp real, e-mail real ou autopublicacao.

## Fora do escopo

- Backend, banco, migrations, auth/RBAC, restore, VPS, importacao, Pix/Efi, pagamento, upload real e API externa.
- Redesign novo, nova paleta, nova tipografia, animacao chamativa, botao flutuante, barra fixa, scroll lock, `document.body.style.overflow` ou carrossel automatico.

## Status

O Bloco 60 reduz a pendencia de paridade visual em header e wizard, mas nao encerra `BLOQUEADO_PARIDADE_VISUAL_PRODUCAO`. Permanecem pendentes revisao humana/Pro final, footer/admin quando aplicavel e comparacao visual completa antes de homologacao real.
