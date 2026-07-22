# Mapa admin/permissoes

## Rotas de autenticacao

- `POST /api/admin/auth/login`;
- `POST /api/admin/auth/logout`;
- `GET /api/admin/auth/me`;
- `GET /api/admin/auth/permissions`.

## Modulos admin e papeis

| Modulo | Rota | Papeis locais |
| --- | --- | --- |
| Visao geral | `/admin` | ADMIN, MODERADOR |
| Moderacao | `/admin/moderacao` | ADMIN, MODERADOR |
| Anuncios | `/admin/anuncios` | ADMIN, MODERADOR |
| Usuarios | `/admin/usuarios` | ADMIN |
| Midia | `/admin/midia` | ADMIN, MODERADOR |
| Premium | `/admin/premium` | ADMIN, MODERADOR |
| Desempenho | `/admin/desempenho` | ADMIN, MODERADOR |
| Creditos | `/admin/creditos` | ADMIN |
| Financeiro | `/admin/financeiro` | ADMIN |
| SEO | `/admin/seo` | ADMIN |
| Banners | `/admin/banners` | ADMIN |
| Comercial | `/admin/comercial` | ADMIN |
| Suporte | `/admin/suporte` | ADMIN, MODERADOR |
| Backup | `/admin/backup` | ADMIN |
| Auditoria | `/admin/auditoria` | ADMIN |

## Regra

O frontend mostra papeis/permissoes, mas a fonte de autorizacao e sempre o backend.

Nao existe perfil operacional `COMERCIAL` na V3. `ANUNCIANTE` usa o papel `USUARIO`; ambos ficam fora das rotas administrativas.

## Endpoints admin read-only

| Endpoint | Papeis locais |
| --- | --- |
| `GET /api/admin/visao-geral` | ADMIN, MODERADOR |
| `GET /api/admin/anuncios/resumo` | ADMIN, MODERADOR |
| `GET /api/admin/moderacao/resumo` | ADMIN, MODERADOR |
| `GET /api/admin/midias/resumo` | ADMIN, MODERADOR |
| `GET /api/admin/metricas/resumo` | ADMIN |
| `GET /api/admin/sistema/status` | ADMIN |
| `GET /api/admin/desempenho/anuncios/{id}` | ADMIN, MODERADOR |
| `GET /api/admin/desempenho/anuncios/{id}/diario` | ADMIN, MODERADOR |
| `GET /api/admin/desempenho/anuncios/{id}/origens` | ADMIN, MODERADOR |
| `GET /api/admin/desempenho/anunciantes/{usuarioId}` | ADMIN |
| `GET /api/admin/desempenho/resumo` | ADMIN |

Todos retornam apenas dados agregados e somente leitura. Nenhum endpoint deste bloco executa escrita, aprovacao, reprovacao, exclusao, pagamento, credito, upload, Pix ou importador real.

A visao geral e limitada por papel: `MODERADOR` nao recebe metricas/sistema.

## Complemento Bloco 15

Listagem, detalhe, midia e revisao de anuncios ficam restritos a `ADMIN` e `MODERADOR` com as permissoes granulares correspondentes.

## Complemento Bloco 16

`ADMIN` e `MODERADOR` podem executar apenas:

- `POST /api/admin/moderacao/revisoes/{id}/decidir`;
- `POST /api/admin/midias/{id}/decidir`.

`USUARIO`, `ANUNCIANTE` e qualquer token historico sem permissao recebem `403`. Sem sessao recebe `401`. As acoes sao locais, auditadas e nao criam e-mail real, hard delete, upload, pagamento, credito, Pix/Efi ou importador real.
