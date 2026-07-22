# Matriz de permissoes RBAC

## Papeis

- `ADMIN`;
- `MODERADOR`;
- `USUARIO`.

`ANUNCIANTE` e tipo de conta e usa o papel `USUARIO`. O token historico `COMERCIAL` nao e perfil operacional e permanece apenas por compatibilidade de schema, sem permissoes administrativas.

## Permissoes minimas

- `ADMIN_CONFIGURAR`;
- `SEGURANCA_GERENCIAR`;
- `ANUNCIO_LER`;
- `ANUNCIO_MODERAR`;
- `MIDIA_REVISAR`;
- `DOCUMENTO_REVISAR`;
- `COMERCIAL_GERENCIAR`;
- `SUPORTE_ATENDER`;
- `AUDITORIA_LER`;
- `FINANCEIRO_LER`.

## Matriz local inicial

| Papel | Permissoes |
| --- | --- |
| `ADMIN` | todas as permissoes minimas |
| `MODERADOR` | `ANUNCIO_LER`, `ANUNCIO_MODERAR`, `MIDIA_REVISAR`, `DOCUMENTO_REVISAR`, `SUPORTE_ATENDER`, `AUDITORIA_LER` |
| `USUARIO` | nenhuma permissao administrativa |

## Limite

A matriz autoriza leitura local dos Blocos 14/15 e, no Bloco 16, apenas decisao local minima de moderacao para `ADMIN` e `MODERADOR`. Ela nao libera hard delete, upload, pagamento, credito, Pix/Efi, e-mail real, importador real ou acao administrativa ampla.

## Endpoints read-only autorizados

| Endpoint | ADMIN | MODERADOR | USUARIO |
| --- | --- | --- | --- |
| `GET /api/admin/visao-geral` | sim | sim | nao |
| `GET /api/admin/anuncios/resumo` | sim | sim | nao |
| `GET /api/admin/moderacao/resumo` | sim | sim | nao |
| `GET /api/admin/midias/resumo` | sim | sim | nao |
| `GET /api/admin/metricas/resumo` | sim | nao | nao |
| `GET /api/admin/sistema/status` | sim | nao | nao |

## Endpoints de desempenho read-only

| Endpoint | ADMIN | MODERADOR | USUARIO |
| --- | --- | --- | --- |
| `GET /api/admin/desempenho/anuncios/{id}` | sim | sim | nao |
| `GET /api/admin/desempenho/anuncios/{id}/diario` | sim | sim | nao |
| `GET /api/admin/desempenho/anuncios/{id}/origens` | sim | sim | nao |
| `GET /api/admin/desempenho/anunciantes/{usuarioId}` | sim | nao | nao |
| `GET /api/admin/desempenho/resumo` | sim | nao | nao |

## Endpoints detalhados autorizados

| Endpoint | ADMIN | MODERADOR | USUARIO |
| --- | --- | --- | --- |
| `GET /api/admin/anuncios` | sim | sim | nao |
| `GET /api/admin/anuncios/{id}` | sim | sim | nao |
| `GET /api/admin/anuncios/{id}/midias` | sim | sim | nao |
| `GET /api/admin/midias` | sim | sim | nao |
| `GET /api/admin/midias/{id}` | sim | sim | nao |
| `GET /api/admin/moderacao/revisoes` | sim | sim | nao |
| `GET /api/admin/moderacao/revisoes/{id}` | sim | sim | nao |

## Endpoints de acao local minima

| Endpoint | ADMIN | MODERADOR | USUARIO |
| --- | --- | --- | --- |
| `POST /api/admin/moderacao/revisoes/{id}/decidir` | sim | sim | nao |
| `POST /api/admin/midias/{id}/decidir` | sim | sim | nao |
