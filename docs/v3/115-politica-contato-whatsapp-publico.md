# Politica de contato WhatsApp publico

## Decisao backend

O backend decide se o contato WhatsApp pode ser exposto.

O frontend nunca deve liberar contato por conta propria.

## Condicoes obrigatorias

WhatsApp publico pode ser retornado somente quando o anuncio:

- esta `PUBLICADO`;
- possui moderacao `APROVADO`;
- possui `removido_em` nulo;
- possui classificacao `LIVRE`, ou `BLOQUEADO` com idade confirmada pelo backend;
- possui `whatsapp_normalizado` valido.

Se qualquer condicao falhar, o contato nao e exposto.

Conteudo `BLOQUEADO` nao libera WhatsApp sem idade confirmada. Apos confirmacao valida, pode liberar pelo mesmo endpoint autorizado.

## Endpoint autorizado

O unico endpoint que pode retornar URL de WhatsApp e:

```text
POST /api/public/anuncios/{slug}/clique-whatsapp
```

Ele pode retornar:

- `disponivel=true`;
- `whatsappUrl=https://wa.me/5500000000000` em smoke local autorizado;
- `politica.disponivel=true`.

O backend nao retorna `whatsapp_normalizado` bruto como campo separado.

## Eventos

Cada clique permitido ou indisponivel para anuncio publico existente registra evento em `clique_whatsapp`.

Nao ha limite diario comercial de clique, contato ou WhatsApp.

## Privacidade

Nao armazenar:

- IP bruto;
- User-Agent bruto;
- referer bruto;
- telefone em endpoint nao autorizado;
- documento privado;
- storage key;
- bucket;
- hash interno;
- pagamento;
- saldo.

Hashes tecnicos usam salt configuravel por ambiente. Producao futura deve definir o salt fora do codigo.

A partir do Bloco 9, fora de `local` a aplicacao falha se o salt ou segredo de idade estiver ausente, vazio ou ficticio.
