# Checklist Bloco 14 - admin read-only

- [x] Inventario inicial executado antes das alteracoes.
- [x] Nao houve consulta SSH a producao.
- [x] `EFI_PIX_MOCK_MODE` base corrigido para fail-closed.
- [x] `application-local.yml` manteve mock true apenas para local.
- [x] Endpoints `GET /api/admin/*` read-only criados.
- [x] Nenhum `POST`, `PUT`, `PATCH` ou `DELETE` read-only criado.
- [x] Controllers protegidos por sessao/RBAC.
- [x] `ADMIN` acessa todos os resumos.
- [x] `MODERADOR` acessa anuncios, moderacao e midia.
- [x] `COMERCIAL` acessa visao geral, anuncios e metricas.
- [x] `USUARIO` nao acessa admin.
- [x] Usuario sem sessao recebe `401`.
- [x] Usuario sem permissao recebe `403`.
- [x] DTOs nao expoem documento privado, storage, senha, hash, token, cookie, WhatsApp real ou dado financeiro sensivel.
- [x] Frontend admin usa `credentials: "include"`.
- [x] Frontend admin nao usa localStorage/sessionStorage para auth ou read-only.
- [x] Frontend admin nao criou botao funcional de aprovacao, rejeicao, exclusao, pagamento, credito, upload, Pix ou moderacao real.
- [x] Dados usados no E2E sao sinteticos e ficam fora de migrations.
- [x] OpenAPI atualizado para os contratos read-only.
- [x] Validacoes locais executadas.
- [x] Nao houve migration, SQL de schema, seed real, producao, VPS, banco de producao, API externa, remote, push ou commit.
- [x] Nenhuma fase posterior foi iniciada.

## Complemento Bloco 15

- [x] Health publico minimo sem ambiente e sem Efi mock.
- [x] Listagens/detalhes read-only mantem RBAC.
- [x] Contratos detalhados continuam sem acao critica.
