# Politica de conteudo BLOQUEADO com confirmacao de idade

## Regra

`BLOQUEADO` nao significa proibido para sempre. Na V3, significa bloqueado ate confirmacao de idade valida pelo backend.

Sem idade confirmada:

- nao aparece como detalhe publico normal;
- nao libera WhatsApp;
- nao retorna stories;
- nao libera midia publica.

Com idade confirmada:

- pode retornar detalhe quando `PUBLICADO`, `APROVADO` e sem `removido_em`;
- pode liberar WhatsApp pelo endpoint autorizado quando houver contato valido;
- pode retornar stories autorizados com metadata segura;
- continua sem expor storage key, bucket, hash, documento privado ou URL privada.

## Decisao backend

O frontend apenas chama APIs locais e reflete o retorno autorizado.

Nao ha classificacao intermediaria, blur por categoria, desbloqueio parcial por visitante ou confirmacao em localStorage/sessionStorage.

## Pendencias

CDN/midia publica real permanece pendente:

```text
PENDENTE_URL_PUBLICA_MIDIA_CDN
```

Revisao juridica final permanece futura.
