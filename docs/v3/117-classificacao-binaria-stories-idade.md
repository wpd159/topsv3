# Classificacao binaria e stories com idade

## Modelo final

A V3 usa somente:

- `LIVRE`;
- `BLOQUEADO`.

Nao existem estados publicos intermediarios.

## Regras de exibicao

`LIVRE`:

- pode aparecer publicamente quando o anuncio tambem estiver publicado, aprovado e nao removido;
- pode exibir midia publica aprovada;
- pode liberar WhatsApp pelo endpoint autorizado quando houver contato valido.

`BLOQUEADO`:

- nao libera sem confirmacao de idade;
- pode ser liberado pelo backend apos confirmacao valida;
- pode exibir detalhe, stories e WhatsApp somente quando as demais regras publicas forem atendidas.

## Stories

Todo story fica bloqueado ate confirmacao de idade.

A partir do Bloco 9 existe confirmacao de idade local por declaracao e cookie HttpOnly.

Com idade confirmada:

- story pode ser `LIVRE` ou `BLOQUEADO`;
- story deve estar aprovado/publicavel;
- story deve estar vinculado a anuncio publicado;
- autorizacao deve vir do backend.

Sem idade confirmada, nenhum story e publico.

## Proibicoes preservadas

Nao criar:

- classificacao intermediaria;
- regra de semi explicito;
- blur por categoria;
- desbloqueio parcial por visitante;
- confirmacao de idade em localStorage ou sessionStorage;
- decisao de classificacao no frontend.
