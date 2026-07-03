# Bloco 25 - prova de resultado read-only

## Objetivo

O Bloco 25 cria uma camada local de desempenho para apoiar prova de resultado para admin e, futuramente, anunciante.

O escopo e estritamente read-only:

- visualizacoes agregadas;
- cliques WhatsApp permitidos;
- taxa clique/view;
- origem agregada;
- cidade, bairro e UF sanitizados;
- comparativo organico/Premium;
- estado vazio para anuncio sem metricas.

Nao ha dado real, producao, VPS, banco de producao, API externa, Pix/Efi, OpenAI, tracking externo, pixel, cobranca, credito real, compra, impulsionamento, worker, scheduler, migration nova ou SQL de schema.

## Endpoints locais

| Endpoint | Papel local | Escopo |
| --- | --- | --- |
| `GET /api/admin/desempenho/anuncios/{id}` | ADMIN, COMERCIAL, MODERADOR | detalhe sanitizado do anuncio |
| `GET /api/admin/desempenho/anuncios/{id}/diario` | ADMIN, COMERCIAL, MODERADOR | serie diaria agregada |
| `GET /api/admin/desempenho/anuncios/{id}/origens` | ADMIN, COMERCIAL, MODERADOR | origens agregadas |
| `GET /api/admin/desempenho/anunciantes/{usuarioId}` | ADMIN, COMERCIAL | visao administrativa por anunciante |
| `GET /api/admin/desempenho/resumo` | ADMIN, COMERCIAL | resumo comercial agregado |

Todos os endpoints exigem sessao administrativa local, `ANUNCIO_LER` e metodo `GET`.

`USUARIO` nao acessa admin. `MODERADOR` pode consultar desempenho basico de anuncio, mas nao acessa resumo comercial agregado nem visao por anunciante.

## Fontes locais

As consultas usam primeiro agregados locais:

- `agregado_visualizacao_diaria`;
- `agregado_clique_whatsapp_diario`.

Quando nao houver agregado, o backend pode fazer fallback local para eventos sinteticos:

- `evento_visualizacao`;
- `clique_whatsapp`.

Esse fallback nao expoe evento bruto. Ele apenas reagrupa totais para permitir tela estavel em ambiente local.

## Sanitizacao

As respostas nao podem expor:

- IP bruto ou hash de IP;
- User-Agent bruto ou hash;
- referer bruto;
- visitante/hash interno;
- CPF, documento privado, e-mail real;
- telefone ou WhatsApp bruto;
- storage provider, bucket, chave de objeto, sha256 ou etag;
- payload de pagamento, Pix/Efi, webhook, saldo, credito, txid ou valor monetario;
- auditoria bruta ou payload sensivel.

Campos de controle retornam:

- `somenteLeitura=true`;
- `dadosSensiveisOcultos=true`;
- `trackingExternoExecutado=false`.

## Premium e organico

O comparativo Premium e apenas informativo.

Regras:

- Premium pode ampliar exposicao;
- Premium nao promete contratacao;
- Premium nao garante resultado;
- Premium nao transforma gratuito em limitado;
- gratuito continua sem limite comercial artificial de clique, contato ou WhatsApp;
- backend e a fonte do calculo;
- frontend apenas exibe os dados retornados.

O DTO retorna explicitamente:

- `promessaResultadoGarantido=false`;
- `gratuitoLimitado=false`.

## Painel local

A rota `/admin/desempenho` mostra:

- totais do anuncio sintetico;
- serie diaria;
- origens;
- comparativo organico/Premium;
- resumo agregado;
- visao administrativa por anunciante;
- estado vazio para anuncio sem metricas.

A tela nao tem botao de comprar, pagar, impulsionar, exportar, ativar Premium, ajustar credito, processar pagamento, gerar Pix, chamar pixel ou enviar tracking externo.

## Evidencias visuais

As evidencias do Bloco 25 ficam em:

- `docs/v3/evidencias/bloco-25/desktop-admin-desempenho.png`;
- `docs/v3/evidencias/bloco-25/mobile-admin-desempenho.png`;
- `docs/v3/evidencias/bloco-25/desktop-admin-desempenho-fallback.png`.

As capturas devem usar somente ambiente local, PostgreSQL descartavel, dados sinteticos, backend/frontend locais e navegador headless local.

## Overlay circular N

A busca estatica no codigo do frontend nao encontrou implementacao local de overlay circular preto com letra `N`, elemento fixed/absolute/sticky relacionado ou marcador equivalente. Se o overlay aparecer em screenshot, ele deve ser tratado como sobreposicao externa do navegador/ferramenta de captura, nao como componente da V3.

## Fora do escopo

Este bloco nao cria:

- migration;
- SQL de schema;
- tabela nova;
- entidade nova de dominio;
- importador real;
- endpoint publico de anunciante;
- painel funcional de anunciante;
- dado real;
- consulta ampla em producao;
- tracking/pixel externo;
- integracao com analytics externo;
- pagamento, credito, Pix/Efi ou checkout.

## Pendencias Pro

- Validar regra final de exposicao de relatorios para anunciante real.
- Definir periodo e granularidade para relatorios comerciais reais.
- Revisar equivalencia das metricas reais de producao antes de migracao/importacao.
- Revisar politicas de privacidade para relatorios externos ao admin.
- Aprovar qualquer exportacao futura antes de implementacao.
