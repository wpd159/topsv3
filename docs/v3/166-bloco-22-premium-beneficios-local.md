# Bloco 22 - Premium e beneficios locais

## Objetivo

Implementar leitura e calculo local de Premium/beneficios, preservando o modelo atual como regra existente e mantendo novos recursos apenas como aditivos.

Este bloco nao cria compra, cobranca, checkout, Pix/Efi funcional, credito real, ativacao real por dinheiro, job de expiracao, scheduler, migration, SQL de schema ou importador real.

## Consulta a producao

Nao houve consulta SSH somente leitura. As migrations, documentos locais e codigo do workspace foram suficientes para implementar o bloco localmente.

## Regras preservadas

- Premium vende exposicao, midia adicional, destaque, relatorios e organizacao.
- Premium nao promete contratacao.
- Plano gratuito continua util.
- Nao ha limite comercial diario de clique, contato ou WhatsApp para o gratuito.
- Beneficios novos so podem ser aditivos.
- Beneficios ativados em conjunto devem expirar de forma coerente.
- Expiracao real automatica fica fora deste bloco.

## Backend

Foram adicionados services locais de leitura/calculo:

- `PremiumStatusConsultaService`;
- `BeneficioAnuncioConsultaService`;
- `PremiumExpiracaoPolicyService`;
- `PremiumConsistenciaService`;
- `PremiumPublicoMapper`.

Foram adicionados endpoints admin autenticados e somente leitura:

- `GET /api/admin/premium/anuncios/{id}`;
- `GET /api/admin/premium/anuncios/{id}/beneficios`;
- `GET /api/admin/premium/consistencia`;
- `GET /api/admin/premium/vencendo`.

Os endpoints sao GET, exigem sessao/RBAC e nao executam mutacao comercial.

## DTOs sanitizados

Os DTOs admin nao retornam:

- valor pago;
- preco/custo snapshot;
- saldo de credito;
- payload de pagamento;
- idempotency key;
- telefone/WhatsApp bruto;
- documento privado;
- storage key, bucket, hash ou URL privada;
- auditoria bruta.

O DTO publico continua restrito a flags/rotulos sanitizados:

- destaque;
- topo;
- midia extra;
- beneficios publicos como `Destaque`, `Topo`, `Stories` ou `Midia extra`.

Publico nao recebe valor, credito, grupo, campanha, origem financeira, historico ou dado interno.

## Expiracao conjunta

A regra local calcula:

- beneficio ativo;
- beneficio expirado;
- beneficio vencendo em ate 7 dias;
- beneficio pendente;
- grupo expirado com beneficio ativo;
- beneficio expirado antes do grupo;
- grupo sem beneficios;
- data invalida;
- origem desconhecida.

Se um grupo/pacote/campanha expira, todos os beneficios vinculados sao tratados como expirados no calculo local. Se uma ativacao ainda aparece ativa apos a expiracao do grupo, o relatorio gera `GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO`.

Se um beneficio expira antes do grupo sem justificativa local, o relatorio gera `BENEFICIO_EXPIRADO_ANTES_DO_GRUPO`.

## Dados sinteticos

O arquivo `scripts/local/dados-sinteticos/dados-publicos-minimos.sql` recebeu somente dados locais descartaveis:

- anuncio gratuito sem beneficio Premium;
- anuncio com Premium ativo;
- grupo de beneficios ativo;
- grupo expirado com ativacao ativa inconsistente;
- beneficio vencendo;
- beneficio expirado antes do grupo;
- grupo sem beneficios.

Nao ha dado real, pagamento real, Pix/Efi, checkout, cobranca ou credito real.

O Bloco 23 passa a complementar esta base com leitura local de creditos/ledger. Essa leitura nao altera Premium, nao ativa beneficio e nao cria credito real.

O Bloco 25 passa a complementar a base Premium com comparativo organico/Premium em prova de resultado local. Essa leitura nao altera beneficio, nao vende impulsionamento, nao promete contratacao, nao limita gratuito e nao cria acao financeira.

## Frontend/admin

`/admin/premium` deixou de ser placeholder e passou a mostrar painel read-only minimo:

- status Premium do anuncio sintetico;
- beneficios do anuncio;
- beneficios vencendo;
- inconsistencias;
- confirmacao visual de compra real bloqueada;
- confirmacao visual de limite gratuito inexistente.

Nao foram criados botoes de ativar, comprar, pagar, ajustar credito, checkout, webhook ou acao real.

## OpenAPI

`contracts/openapi/topsdojob-v3-local.yaml` documenta os endpoints e schemas read-only de Premium, incluindo campos proibidos.

## Evidencias visuais

Como houve alteracao de painel admin, prints foram gerados em ambiente local com dados sinteticos e incluidos no pacote:

- `docs/v3/evidencias/bloco-22/desktop-admin-premium.png`;
- `docs/v3/evidencias/bloco-22/mobile-admin-premium.png`.

## Fora do escopo

Nao houve:

- producao;
- VPS;
- banco de producao;
- dado real;
- dump real;
- migration nova;
- alteracao de SQL de schema;
- compra real;
- pagamento real;
- Pix/Efi funcional;
- credito real;
- checkout;
- webhook real;
- scheduler/job de expiracao;
- importador real;
- remote;
- push;
- commit.

## Riscos residuais

- Regras finais de beneficios atuais ainda dependem de evidencia de producao/revisao Pro antes de homologacao/producao.
- Ativacao real, cobranca, conciliacao, creditos e expurgo/expiracao automatica continuam fora desta entrega.
- Ranking final com Premium ativo permanece dependente de fase futura.
