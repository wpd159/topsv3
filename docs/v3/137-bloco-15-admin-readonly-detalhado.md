# Bloco 15 - admin read-only detalhado

## Objetivo

Evoluir o admin local somente leitura para listagens e detalhes sanitizados de anuncios, midia e moderacao.

## Consulta a producao

Nao houve consulta SSH somente leitura. Os documentos locais, schema V3, codigo do workspace e dados sinteticos locais foram suficientes.

## Health publico minimo

`/api/health`, `/api/health/readiness` e `/api/health/liveness` passam a retornar apenas:

- `status`;
- `app`;
- `requestId`.

`app.env` e `efiPixMockMode` deixam de ser expostos no health publico. Esses dados permanecem disponiveis somente em `/api/admin/sistema/status`, autenticado e restrito a `ADMIN`.

## Endpoints criados

- `GET /api/admin/anuncios`;
- `GET /api/admin/anuncios/{id}`;
- `GET /api/admin/anuncios/{id}/midias`;
- `GET /api/admin/moderacao/revisoes`;
- `GET /api/admin/moderacao/revisoes/{id}`;
- `GET /api/admin/midias`;
- `GET /api/admin/midias/{id}`.

Todos sao `GET`, autenticados e read-only.

## RBAC

| Papel | Acesso |
| --- | --- |
| `ADMIN` | todos os endpoints detalhados |
| `MODERADOR` | anuncios, midia e revisoes |
| `COMERCIAL` | anuncios em versao limitada |
| `USUARIO` | nenhum endpoint admin |

`COMERCIAL` nao acessa midia detalhada, midias do anuncio ou revisoes.

## Filtros e paginacao

Anuncios:

- `page`;
- `size`, limitado a 50;
- `status`;
- `statusModeracao`;
- `classificacaoConteudo`;
- `uf`;
- `cidade`;
- `bairro`;
- `termo`, simples, limitado e sem query nativa.

Midias:

- `page`;
- `size`, limitado a 50;
- `status`;
- `classificacaoConteudo`;
- `tipo`;
- `anuncioId`.

Revisoes:

- `page`;
- `size`, limitado a 50;
- `status`;
- `tipo`;
- `anuncioId`.

## Sanitizacao

Os DTOs nao retornam:

- documento privado;
- CPF;
- telefone bruto;
- WhatsApp normalizado;
- e-mail privado completo;
- senha, hash, token ou cookie;
- IP, User-Agent ou hash interno;
- storage provider, bucket, chave, hash, etag, URL privada ou nome original;
- payload financeiro;
- saldo de credito;
- auditoria sensivel;
- conteudo completo de revisao.

Anuncios retornam indicadores agregados como `contatoConfigurado`, `documentoPendente`, `midiasTotal` e `revisoesTotal`.

Midias retornam apenas metadata segura. Revisoes retornam apenas `conteudoSolicitadoPresente`.

## Frontend

O shell admin passa a exibir uma area de detalhamento local com:

- lista de anuncios;
- lista de midia;
- lista de revisoes;
- estado de detalhe read-only quando permitido pelo papel.

Nao foram criados botoes funcionais de aprovacao, rejeicao, exclusao, pausa, ativacao, pagamento, credito, Pix ou upload.

## Dados sinteticos

`scripts/local/dados-sinteticos/dados-publicos-minimos.sql` passa a conter:

- anuncio LIVRE publicado;
- anuncio BLOQUEADO publicado;
- anuncio PENDENTE_REVISAO;
- midia pendente;
- revisao ABERTA.

Os usuarios admin/moderador/comercial/usuario continuam em `dados-admin-minimos.sql`.

## Fora do escopo

Nao houve migration, SQL de schema, seed real, acao critica, moderacao real, financeiro/Pix, importador real, producao, VPS, banco de producao, API externa, remote, push ou commit.

## Nota posterior - Bloco 16

O Bloco 16 passou a permitir somente acoes locais minimas de moderacao para revisao e midia, com RBAC e auditoria sanitizada. O restante das proibicoes do Bloco 15 continua valido.
