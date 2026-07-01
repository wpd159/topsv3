# Politica de stories com idade

## Sem idade confirmada

Stories nao sao retornados como conteudo publico.

A resposta do endpoint de stories deve ser neutra:

- `idadeConfirmada=false`;
- `autorizado=false`;
- `stories=[]`;
- `motivoPublico=IDADE_NAO_CONFIRMADA`;
- `pendencia=null`.

## Com idade confirmada

Com cookie valido, o backend pode retornar stories quando:

- anuncio esta `PUBLICADO`;
- moderacao esta `APROVADO`;
- `removido_em` e nulo;
- vinculo de midia esta `PUBLICAVEL`;
- story esta `PUBLICADO`;
- arquivo esta `VALIDADO`;
- classificacao e `LIVRE` ou `BLOQUEADO`.

## Protecoes

O endpoint nao retorna:

- storage provider;
- bucket;
- chave de objeto;
- sha256;
- etag;
- URL privada;
- documento privado;
- imagem real.

Frontend nao decide idade, classificacao ou liberacao.
