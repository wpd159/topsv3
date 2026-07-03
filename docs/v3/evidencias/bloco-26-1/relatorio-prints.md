# Relatorio de prints - Bloco 26.1

Prints gerados localmente para validar a correcao visual da rota `/anunciar`.

## Ambiente

- Frontend: `http://127.0.0.1:3000/anunciar`, iniciado por `next start` local.
- Backend: `http://127.0.0.1:8080`, perfil local.
- Banco: PostgreSQL 16 descartavel em Docker local, sem volume persistente.
- Dados: sinteticos locais, sem dado real, foto real, video real, upload real, producao ou API externa.

## Arquivos

- `docs/v3/evidencias/bloco-26-1/desktop-anunciar-inicial.png`
- `docs/v3/evidencias/bloco-26-1/mobile-anunciar-inicial.png`
- `docs/v3/evidencias/bloco-26-1/desktop-anunciar-validacao-erro.png`
- `docs/v3/evidencias/bloco-26-1/mobile-anunciar-validacao-erro.png`
- `docs/v3/evidencias/bloco-26-1/desktop-anunciar-sucesso.png`
- `docs/v3/evidencias/bloco-26-1/mobile-anunciar-sucesso.png`

## Admin

Admin nao foi afetado pelo Bloco 26.1. Por isso, os prints condicionais abaixo nao foram gerados:

- `desktop-admin-solicitacao.png`;
- `mobile-admin-solicitacao.png`.

## Resultado visual observado

- Desktop nao esta espremido na lateral esquerda.
- "Rota preservada" nao aparece como elemento principal do visitante.
- Nao houve texto verticalizado em `/anunciar`.
- Mobile permanece em coluna unica, sem scroll horizontal observado.
- CTA permanece dentro do fluxo.
- Nao ha elemento flutuante, animacao automatica ou scroll lock.
- A tela nao usa `document.body.style.overflow`.
- Metrica final do navegador local: `scrollWidth=375`, `clientWidth=375`.
- O texto visivel final nao continha "Rota preservada", "sintetico", "local", "V3" ou "skeleton".

## Observacao

Os dados usados para acionar o estado de sucesso sao reservados/sinteticos e servem apenas para atravessar a validacao local. O print de sucesso nao exibe UUIDs nem status tecnico em destaque visual.
