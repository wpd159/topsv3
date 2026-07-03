# Relatorio de prints - Bloco 26

## Ambiente

- Frontend local: `http://127.0.0.1:3000/anunciar`.
- Backend local: `http://127.0.0.1:18080`.
- Banco: PostgreSQL 16 descartavel, sem volume persistente.
- Dados: sinteticos locais.
- Producao, VPS, API externa, Efi real, OpenAI e banco de producao: nao acessados.

## Capturas

- `anunciar-desktop-inicial.png`: desktop inicial da rota `/anunciar`.
- `anunciar-mobile-inicial.png`: mobile inicial da rota `/anunciar`, capturado por viewport.
- `anunciar-validacao-erro.png`: validacao local com campos obrigatorios ausentes, capturada por viewport mobile.
- `anunciar-sucesso.png`: envio sintetico concluido com anuncio `PENDENTE_REVISAO` e revisao `ABERTA`, capturado por viewport mobile.
- `anunciar-upload-futuro.png`: estado documentado de upload futuro, sem seletor de arquivo e sem storage key.
- `admin-solicitacao-bloco-26.png`: admin local autenticado mostrando a solicitacao do Bloco 26 na fila de moderacao.

## Observacoes

- Nenhum botao flutuante, scroll lock, animacao automatica ou overlay da V3 foi identificado nos prints.
- O botao/overlay do Next.js nao aparece nas capturas finais porque os prints foram feitos com `next start`, nao com `next dev`.
- O `fullPage` mobile do navegador integrado gerou costura visual inconsistente; por isso as evidencias mobile finais usam screenshot de viewport e medicao DOM com `documentElement.scrollWidth <= innerWidth`.
- A V3 recebeu `viewport` explicito e contenção responsiva para manter a rota `/anunciar` dentro da largura mobile.
- O admin exigiu reload apos login para recarregar paines que haviam montado antes da sessao local existir.
