# Mapa admin/permissoes

## Rotas de autenticacao

- `POST /api/admin/auth/login`;
- `POST /api/admin/auth/logout`;
- `GET /api/admin/auth/me`;
- `GET /api/admin/auth/permissions`.

## Modulos admin e papeis

| Modulo | Rota | Papeis locais |
| --- | --- | --- |
| Visao geral | `/admin` | ADMIN, MODERADOR, COMERCIAL |
| Moderacao | `/admin/moderacao` | ADMIN, MODERADOR |
| Anuncios | `/admin/anuncios` | ADMIN, MODERADOR, COMERCIAL |
| Usuarios | `/admin/usuarios` | ADMIN |
| Midia | `/admin/midia` | ADMIN, MODERADOR |
| Premium | `/admin/premium` | ADMIN, COMERCIAL |
| Creditos | `/admin/creditos` | ADMIN, COMERCIAL |
| Financeiro | `/admin/financeiro` | ADMIN |
| SEO | `/admin/seo` | ADMIN, COMERCIAL |
| Banners | `/admin/banners` | ADMIN, COMERCIAL |
| Comercial | `/admin/comercial` | ADMIN, COMERCIAL |
| Suporte | `/admin/suporte` | ADMIN, MODERADOR, COMERCIAL |
| Backup | `/admin/backup` | ADMIN |
| Auditoria | `/admin/auditoria` | ADMIN |

## Regra

O frontend mostra papeis/permissoes, mas a fonte de autorizacao e sempre o backend.

## Endpoints admin read-only

| Endpoint | Papeis locais |
| --- | --- |
| `GET /api/admin/visao-geral` | ADMIN, MODERADOR, COMERCIAL |
| `GET /api/admin/anuncios/resumo` | ADMIN, MODERADOR, COMERCIAL |
| `GET /api/admin/moderacao/resumo` | ADMIN, MODERADOR |
| `GET /api/admin/midias/resumo` | ADMIN, MODERADOR |
| `GET /api/admin/metricas/resumo` | ADMIN, COMERCIAL |
| `GET /api/admin/sistema/status` | ADMIN |

Todos retornam apenas dados agregados e somente leitura. Nenhum endpoint deste bloco executa escrita, aprovacao, reprovacao, exclusao, pagamento, credito, upload, Pix ou importador real.

A visao geral e limitada por papel: `COMERCIAL` nao recebe blocos de moderacao/midia/sistema, e `MODERADOR` nao recebe metricas/sistema.

## Complemento Bloco 15

`COMERCIAL` pode consultar listagem/detalhe de anuncios em versao limitada. Endpoints de midia e revisao detalhada ficam restritos a `ADMIN` e `MODERADOR`.

## Complemento Bloco 16

`ADMIN` e `MODERADOR` podem executar apenas:

- `POST /api/admin/moderacao/revisoes/{id}/decidir`;
- `POST /api/admin/midias/{id}/decidir`.

`COMERCIAL` e `USUARIO` recebem `403`. Sem sessao recebe `401`. As acoes sao locais, auditadas e nao criam e-mail real, hard delete, upload, pagamento, credito, Pix/Efi ou importador real.
