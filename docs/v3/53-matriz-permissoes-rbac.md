# Matriz de permissoes RBAC

## Papeis

- `ADMIN`;
- `MODERADOR`;
- `COMERCIAL`;
- `USUARIO`.

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
| `COMERCIAL` | `ANUNCIO_LER`, `COMERCIAL_GERENCIAR`, `SUPORTE_ATENDER`, `FINANCEIRO_LER` |
| `USUARIO` | `ANUNCIO_LER` |

## Limite

A matriz autoriza leitura local dos Blocos 14/15 e, no Bloco 16, apenas decisao local minima de moderacao para `ADMIN` e `MODERADOR`. Ela nao libera hard delete, upload, pagamento, credito, Pix/Efi, e-mail real, importador real ou acao administrativa ampla.

## Endpoints read-only autorizados

| Endpoint | ADMIN | MODERADOR | COMERCIAL | USUARIO |
| --- | --- | --- | --- | --- |
| `GET /api/admin/visao-geral` | sim | sim | sim | nao |
| `GET /api/admin/anuncios/resumo` | sim | sim | sim | nao |
| `GET /api/admin/moderacao/resumo` | sim | sim | nao | nao |
| `GET /api/admin/midias/resumo` | sim | sim | nao | nao |
| `GET /api/admin/metricas/resumo` | sim | nao | sim | nao |
| `GET /api/admin/sistema/status` | sim | nao | nao | nao |

## Endpoints detalhados autorizados

| Endpoint | ADMIN | MODERADOR | COMERCIAL | USUARIO |
| --- | --- | --- | --- | --- |
| `GET /api/admin/anuncios` | sim | sim | limitado | nao |
| `GET /api/admin/anuncios/{id}` | sim | sim | limitado | nao |
| `GET /api/admin/anuncios/{id}/midias` | sim | sim | nao | nao |
| `GET /api/admin/midias` | sim | sim | nao | nao |
| `GET /api/admin/midias/{id}` | sim | sim | nao | nao |
| `GET /api/admin/moderacao/revisoes` | sim | sim | nao | nao |
| `GET /api/admin/moderacao/revisoes/{id}` | sim | sim | nao | nao |

## Endpoints de acao local minima

| Endpoint | ADMIN | MODERADOR | COMERCIAL | USUARIO |
| --- | --- | --- | --- | --- |
| `POST /api/admin/moderacao/revisoes/{id}/decidir` | sim | sim | nao | nao |
| `POST /api/admin/midias/{id}/decidir` | sim | sim | nao | nao |
