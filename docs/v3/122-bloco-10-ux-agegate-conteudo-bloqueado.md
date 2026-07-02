# Bloco 10 - UX local de age gate para conteudo BLOQUEADO

## Objetivo

Corrigir a pagina local `/anuncios/[slug]` para continuar oferecendo confirmacao de idade quando o detalhe inicial nao e liberado pelo backend.

## Regra preservada

A V3 preserva como base o comportamento publico atual quando ele ja estiver correto e compativel com as decisoes do projeto. Nesta fase, a regra estava clara pelos documentos locais e pelo Bloco 9; nao houve consulta SSH a producao.

## Fluxo implementado

- o primeiro carregamento tenta `GET /api/public/anuncios/{slug}`;
- se o backend liberar, o detalhe local e renderizado;
- se o backend nao liberar, a pagina ainda renderiza o componente local de idade;
- `POST /api/public/idade/confirmar` emite cookie HttpOnly;
- apos confirmacao, o frontend reconsulta `GET /api/public/anuncios/{slug}` com `credentials: include`;
- o conteudo so aparece se o backend retornar detalhe autorizado.

## Garantias

- frontend nao decide se o anuncio e `LIVRE` ou `BLOQUEADO`;
- frontend nao usa localStorage/sessionStorage;
- CPF, documento, telefone real, imagem real e URL real de producao continuam proibidos;
- storage key, bucket e hash interno nao sao expostos;
- nenhum redesign foi feito.

## Pendencias restantes

- CDN/midia publica real permanece `PENDENTE_URL_PUBLICA_MIDIA_CDN`;
- Bloco 11 consolidou `urlPublica=null` como estrategia local segura ate aprovacao de CDN/storage publico;
- revisao juridica/moderacao final permanece futura;
- smoke standalone depende de backend local ja iniciado.
