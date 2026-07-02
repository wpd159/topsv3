# Contratos admin read-only detalhado

## Autenticacao

Todos os endpoints usam sessao/cookie admin local com:

```text
credentials: "include"
```

Sem sessao, retornam `401`. Com sessao sem permissao, retornam `403`.

## Endpoints

| Endpoint | Papeis | Retorno |
| --- | --- | --- |
| `GET /api/admin/anuncios` | ADMIN, MODERADOR, COMERCIAL | pagina de `AdminAnuncioListaItemDto` |
| `GET /api/admin/anuncios/{id}` | ADMIN, MODERADOR, COMERCIAL | `AdminAnuncioDetalheDto` |
| `GET /api/admin/anuncios/{id}/midias` | ADMIN, MODERADOR | pagina de `AdminMidiaListaItemDto` |
| `GET /api/admin/midias` | ADMIN, MODERADOR | pagina de `AdminMidiaListaItemDto` |
| `GET /api/admin/midias/{id}` | ADMIN, MODERADOR | `AdminMidiaDetalheDto` |
| `GET /api/admin/moderacao/revisoes` | ADMIN, MODERADOR | pagina de `AdminRevisaoListaItemDto` |
| `GET /api/admin/moderacao/revisoes/{id}` | ADMIN, MODERADOR | `AdminRevisaoDetalheDto` |

## Paginacao

Campos da pagina:

- `itens`;
- `page`;
- `size`;
- `totalElements`;
- `totalPages`;
- `last`.

`size` e limitado a 50.

## Campos permitidos

Anuncio:

- ids;
- slug;
- titulo sanitizado;
- status;
- statusModeracao;
- classificacaoConteudo;
- localizacao por UF/cidade/bairro;
- timestamps;
- contadores e flags agregadas.

Midia:

- ids;
- anuncioId;
- slugAnuncio;
- tipo;
- finalidade;
- ordem;
- status;
- classificacaoConteudo;
- statusArquivo;
- mimeType;
- dimensoes/duracao/tamanho.

Revisao:

- ids;
- anuncioId;
- slugAnuncio;
- tipo;
- status;
- `conteudoSolicitadoPresente`;
- timestamps.

## Campos proibidos

Os contratos nao podem retornar:

- documento privado;
- CPF;
- telefone bruto;
- WhatsApp normalizado;
- e-mail privado completo;
- senha, hash, token ou cookie;
- IP, User-Agent ou hash interno;
- storage provider, bucket, chaveObjeto, sha256, etag, URL privada ou nome original;
- payload financeiro;
- saldo de credito;
- payload completo de revisao;
- auditoria sensivel.

## Limitacao COMERCIAL

`COMERCIAL` acessa apenas anuncios em versao limitada:

- `descricaoResumo` nulo;
- `revisoesTotal` nulo;
- sem endpoints de midia;
- sem endpoints de revisao.

## Nota posterior - Bloco 16

Os contratos read-only permanecem validos. As unicas acoes adicionadas depois deles sao `POST /api/admin/moderacao/revisoes/{id}/decidir` e `POST /api/admin/midias/{id}/decidir`, restritas a `ADMIN` e `MODERADOR`.
