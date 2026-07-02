# Contratos admin read-only

## Autenticacao

Todos os endpoints read-only usam sessao/cookie administrativo local.

O frontend deve enviar:

```text
credentials: "include"
```

Nao existe JWT, OAuth, token no corpo da resposta, localStorage ou sessionStorage.

## Endpoints

| Endpoint | Metodo | Retorno |
| --- | --- | --- |
| `/api/admin/visao-geral` | GET | `AdminVisaoGeralDto` |
| `/api/admin/anuncios/resumo` | GET | `AdminResumoAnunciosDto` |
| `/api/admin/moderacao/resumo` | GET | `AdminResumoModeracaoDto` |
| `/api/admin/midias/resumo` | GET | `AdminResumoMidiasDto` |
| `/api/admin/metricas/resumo` | GET | `AdminResumoMetricasDto` |
| `/api/admin/sistema/status` | GET | `AdminStatusSistemaDto` |

## Respostas de erro

- `401`: usuario sem sessao autenticada;
- `403`: usuario autenticado sem papel/permissao suficiente;
- `500`: falha inesperada sem detalhe interno sensivel.

## Contrato dos DTOs

`AdminContadorDto`:

- `codigo`;
- `rotulo`;
- `total`.

`AdminResumoAnunciosDto`:

- `totalAtivos`;
- `publicados`;
- `pendentesRevisao`;
- `pausados`;
- `bloqueados`;
- `comContatoConfigurado`;
- `porStatus`.

`AdminResumoModeracaoDto`:

- `revisoesAbertas`;
- `revisoesEmAnalise`;
- `anunciosPendentesModeracao`;
- `anunciosBloqueados`;
- `documentosPendentes`.

`AdminResumoMidiasDto`:

- `arquivosTotal`;
- `arquivosPendentes`;
- `arquivosValidados`;
- `midiasPublicaveis`;
- `midiasPendentes`;
- `midiasBloqueadas`;
- `storiesPublicados`;
- `storiesPendentes`.

`AdminResumoMetricasDto`:

- `visualizacoesTotal`;
- `cliquesWhatsappTotal`;
- `cliquesWhatsappPermitidos`;
- `cliquesWhatsappBloqueados`.

`AdminStatusSistemaDto`:

- `app`;
- `ambiente`;
- `local`;
- `efiPixMockMode`;
- `politicaApi`;
- `pendenciaCsrf`.

`AdminVisaoGeralDto` agrega os resumos acima.

Campos agregados de `AdminVisaoGeralDto` podem ser `null` quando o papel autenticado nao tiver permissao para aquele bloco. Essa limitacao vale especialmente para `COMERCIAL` e `MODERADOR`.

## Proibicoes de contrato

Os contratos OpenAPI e DTOs Java nao podem incluir:

- senha, hash, token ou cookie;
- documento privado;
- telefone ou WhatsApp real;
- storage key, bucket, provider, hash, etag ou URL privada;
- valor financeiro sensivel;
- payload de auditoria sensivel.

## Permissoes

| Papel | Permitido neste bloco |
| --- | --- |
| `ADMIN` | todos os resumos read-only e status do sistema |
| `MODERADOR` | visao geral, anuncios, moderacao e midia |
| `COMERCIAL` | visao geral, anuncios e metricas |
| `USUARIO` | nenhum endpoint admin |

Nenhuma permissao read-only autoriza escrita ou acao critica.

## Complemento Bloco 15

Contratos detalhados adicionais:

- `GET /api/admin/anuncios`;
- `GET /api/admin/anuncios/{id}`;
- `GET /api/admin/anuncios/{id}/midias`;
- `GET /api/admin/midias`;
- `GET /api/admin/midias/{id}`;
- `GET /api/admin/moderacao/revisoes`;
- `GET /api/admin/moderacao/revisoes/{id}`.

Todos usam pagina sanitizada quando retornam lista e mantem os campos proibidos fora da resposta.
