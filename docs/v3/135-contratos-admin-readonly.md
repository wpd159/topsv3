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
| `/api/admin/premium/anuncios/{id}` | GET | `AdminPremiumAnuncioStatusDto` |
| `/api/admin/premium/anuncios/{id}/beneficios` | GET | `AdminBeneficioAnuncioDto[]` |
| `/api/admin/premium/consistencia` | GET | `AdminPremiumConsistenciaResumoDto` |
| `/api/admin/premium/vencendo` | GET | `AdminPremiumVencendoResumoDto` |
| `/api/admin/desempenho/anuncios/{id}` | GET | `AdminDesempenhoAnuncioDto` |
| `/api/admin/desempenho/anuncios/{id}/diario` | GET | `AdminDesempenhoDiarioDto[]` |
| `/api/admin/desempenho/anuncios/{id}/origens` | GET | `AdminDesempenhoOrigemDto[]` |
| `/api/admin/desempenho/anunciantes/{usuarioId}` | GET | `AdminDesempenhoAnuncianteDto` |
| `/api/admin/desempenho/resumo` | GET | `AdminDesempenhoResumoDto` |

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

## Complemento Bloco 22

Contratos Premium read-only adicionais:

- `AdminPremiumAnuncioStatusDto`: flags de status, totais e codigos de consistencia, com `somenteLeitura=true`, `compraOuAtivacaoRealDisponivel=false`, `acoesFinanceirasDisponiveis=false` e `gratuitoLimitadoPorContato=false`;
- `AdminBeneficioAnuncioDto`: beneficio, status original/calculado, janela de vigencia, grupo vinculado e codigos de consistencia;
- `AdminPremiumConsistenciaResumoDto`: inconsistencias calculadas;
- `AdminPremiumVencendoResumoDto`: beneficios vencendo na janela local.

Esses DTOs nao podem retornar valor pago, saldo, credito real, payload de pagamento, telefone/WhatsApp bruto, documento privado, storage key, bucket, hash, URL privada ou auditoria bruta.

## Complemento Bloco 25

Contratos de desempenho read-only adicionais:

- `AdminDesempenhoAnuncioDto`: totais de visualizacao, cliques WhatsApp permitidos, taxa clique/view, serie diaria, origens agregadas e comparativo Premium;
- `AdminDesempenhoDiarioDto`: data, totais agregados, taxa clique/view e flag de Premium ativo;
- `AdminDesempenhoOrigemDto`: origem, UF, cidade, bairro e totais agregados, sempre com `dadosSensiveisOcultos=true`;
- `AdminDesempenhoAnuncianteDto`: agregacao administrativa por anunciante, com `endpointAnuncianteRealDisponivel=false`;
- `AdminDesempenhoResumoDto`: resumo comercial agregado.

Esses DTOs nao podem retornar IP, User-Agent, referer bruto, hash interno, contato bruto, documento, storage, pagamento, credito, Pix/Efi, saldo, txid, valor monetario, payload financeiro, pixel ou tracking externo.

Premium no desempenho e apenas comparativo de exposicao/tendencia. Os DTOs devem manter `promessaResultadoGarantido=false` e `gratuitoLimitado=false`.
