# Politica de URL publica de midia

## Regra atual

Enquanto nao existir politica aprovada de CDN/storage publico, a V3 local nao emite URL publica de midia.

Resultado obrigatorio para midia elegivel:

```text
urlPublica = null
pendenciaMidia = PENDENTE_URL_PUBLICA_MIDIA_CDN
```

## Campos proibidos no contrato publico

Respostas publicas de detalhe, listagem, metricas e stories nao podem expor:

- `storageProvider`;
- `bucket`;
- `chaveObjeto`;
- `sha256`;
- `etag`;
- URL privada;
- documento privado;
- nome original de arquivo;
- hash interno.

## Fonte da decisao

O backend e a unica fonte de decisao para:

- midia publicavel;
- classificacao `LIVRE` ou `BLOQUEADO`;
- confirmacao de idade;
- liberacao de stories;
- liberacao de WhatsApp.

O frontend apenas reflete o retorno autorizado.

## Requisitos para uma futura URL publica

Uma fase futura so podera emitir URL publica quando houver:

- origem publica segura aprovada;
- mapeamento CDN/storage que nao revele chave privada;
- garantia de que documento privado nunca seja publicavel;
- validacao de classificacao e idade no backend;
- smoke que comprove ausencia de bucket, chave, provider e hash;
- revisao juridica/operacional quando aplicavel.

## Stories

Stories sem idade confirmada nao retornam midia.

Stories com idade confirmada podem retornar apenas metadata publica segura e a pendencia de CDN enquanto nao houver URL publica aprovada.

## Dados sinteticos

Dados sinteticos locais podem ter valores tecnicos claramente locais no banco descartavel, mas esses valores nao podem aparecer nas respostas publicas.

## Proibicoes

Nao usar imagem real, URL real de producao, storage real, dump, dado real, telefone real, CPF, documento real, e-mail real, API externa ou producao como bancada de teste.
