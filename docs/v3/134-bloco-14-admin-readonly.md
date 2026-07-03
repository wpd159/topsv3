# Bloco 14 - admin read-only local

## Objetivo

Criar uma primeira camada administrativa local somente leitura, protegida por sessao/RBAC, para visao geral, filas e metricas basicas do shell admin.

## Consulta a producao

Nao houve consulta SSH somente leitura. Os contratos locais, migrations ja auditadas, codigo do workspace e documentos dos Blocos 12 e 13 foram suficientes.

## Correcao preliminar Efi Pix mock

O arquivo base `application.yml` nao defaulta mais `EFI_PIX_MOCK_MODE` para `true`.

Regra consolidada:

- `application.yml`: `efi.pix.mock-mode=${EFI_PIX_MOCK_MODE:false}`;
- `application-local.yml`: `efi.pix.mock-mode=${EFI_PIX_MOCK_MODE:true}`;
- health/status podem reportar o estado, mas nao criam integracao Efi real;
- fora de local, mock ausente permanece `false`.

## Endpoints backend

Foram criados endpoints autenticados e somente leitura:

- `GET /api/admin/visao-geral`;
- `GET /api/admin/anuncios/resumo`;
- `GET /api/admin/moderacao/resumo`;
- `GET /api/admin/midias/resumo`;
- `GET /api/admin/metricas/resumo`;
- `GET /api/admin/sistema/status`.

Os controllers ficam em `backend/src/main/java/br/com/topsdojob/v3/web/admin/readonly/`.

## Services e DTOs

Services criados:

- `AdminVisaoGeralConsultaService`;
- `AdminAnuncioResumoConsultaService`;
- `AdminModeracaoResumoConsultaService`;
- `AdminMidiaResumoConsultaService`;
- `AdminMetricaResumoConsultaService`;
- `AdminSistemaStatusService`.

DTOs criados:

- `AdminVisaoGeralDto`;
- `AdminResumoAnunciosDto`;
- `AdminResumoModeracaoDto`;
- `AdminResumoMidiasDto`;
- `AdminResumoMetricasDto`;
- `AdminStatusSistemaDto`;
- `AdminContadorDto`.

## RBAC aplicado

| Endpoint | ADMIN | MODERADOR | COMERCIAL | USUARIO |
| --- | --- | --- | --- | --- |
| `GET /api/admin/visao-geral` | sim | sim | sim | nao |
| `GET /api/admin/anuncios/resumo` | sim | sim | sim | nao |
| `GET /api/admin/moderacao/resumo` | sim | sim | nao | nao |
| `GET /api/admin/midias/resumo` | sim | sim | nao | nao |
| `GET /api/admin/metricas/resumo` | sim | nao | sim | nao |
| `GET /api/admin/sistema/status` | sim | nao | nao | nao |

Usuario nao autenticado recebe `401`. Usuario autenticado sem permissao recebe `403`.

`/api/admin/visao-geral` tambem limita o corpo por papel: `COMERCIAL` recebe anuncios e metricas agregadas, sem blocos de moderacao/midia/sistema; `MODERADOR` recebe anuncios, moderacao e midia, sem metricas/sistema; `ADMIN` recebe todos os blocos.

## Campos proibidos

Os DTOs read-only nao retornam:

- documento privado;
- telefone ou WhatsApp real;
- dado financeiro sensivel;
- storage key, bucket, provider, hash, etag ou URL privada;
- payload de auditoria sensivel;
- senha, hash, token ou cookie.

Metricas e contatos sao expostos somente como contagens agregadas.

## Frontend admin

O shell admin local passou a consultar os endpoints read-only com `credentials: "include"` em `frontend/src/lib/api/adminReadonlyApi.ts`.

O componente `AdminReadonlyPanel` exibe somente contadores e estados locais. Ele nao cria botao funcional de aprovacao, rejeicao, exclusao, pagamento, credito, upload, Pix ou moderacao real.

## Dados sinteticos

`scripts/local/dados-sinteticos/dados-admin-minimos.sql` inclui usuarios locais sinteticos para validar RBAC:

- `admin.local@example.invalid`;
- `moderador.local@example.invalid`;
- `comercial.local@example.invalid`;
- `usuario.local@example.invalid`.

Nao ha seed real, CPF, documento, telefone real, e-mail real ou dado financeiro real.

## Fora do escopo

Nao houve migration, alteracao de SQL de schema, acao critica, moderacao real, financeiro/Pix, importador real, producao, VPS, banco de producao, API externa, remote, push ou commit.

## Complemento Bloco 15

O Bloco 15 adicionou listagens e detalhes read-only de anuncios, midia e revisoes, preservando o mesmo contrato de sessao/RBAC do Bloco 14.

O health publico deixou de expor ambiente e `efiPixMockMode`; esses dados continuam disponiveis apenas em `/api/admin/sistema/status`.

## Complemento Bloco 22

O Bloco 22 adiciona endpoints read-only de Premium:

- `GET /api/admin/premium/anuncios/{id}`;
- `GET /api/admin/premium/anuncios/{id}/beneficios`;
- `GET /api/admin/premium/consistencia`;
- `GET /api/admin/premium/vencendo`.

Eles seguem sessao/RBAC, usam DTOs sanitizados e nao retornam valor pago, saldo, credito real, payload de pagamento, telefone bruto, documento privado ou storage. Nao criam acao de compra, ativacao, checkout, cobranca, Pix/Efi ou expiracao real.
