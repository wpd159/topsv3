# Bloco 26.1 - Correcao visual do Anuncie gratis e SDD mestre

## Objetivo

Corrigir a apresentacao publica da rota `/anunciar` criada no Bloco 26 e consolidar o SDD mestre da V3, sem nova regra de negocio, sem producao, sem dados reais e sem fase posterior.

## Correcao visual aplicada

- A rota `/anunciar` deixou de exibir o texto principal "Rota preservada".
- O formulario passou a usar uma largura normal e legivel no desktop.
- A grade de campos foi ajustada para duas colunas confortaveis no desktop e uma coluna no mobile.
- Textos visiveis foram trocados para linguagem de visitante.
- O retorno de sucesso deixou de exibir UUID de anuncio/revisao e status tecnico em destaque.
- Validacoes visiveis mostram rotulos humanos em vez de nomes internos de campo.
- O CTA permanece dentro do fluxo normal do formulario.

Textos tecnicos removidos da UI publica da rota:

- sintetico;
- local;
- API local;
- V3;
- skeleton;
- rota preservada;
- `PENDENTE_REVISAO`;
- `NAO_PUBLICAVEL`;
- UUIDs de anuncio/revisao em destaque visual.

## Mobile

O ajuste nao introduziu:

- elemento solto, dancando ou flutuante;
- scroll lock;
- `document.body.style.overflow`;
- `position: fixed`;
- `position: absolute`;
- `position: sticky`;
- `100vw`;
- animacao automatica;
- CTA fora do fluxo.

O script `scripts/local/validar-ui-mobile-estatica.ps1` permanece como gate obrigatorio de fechamento do bloco.

Resultado neste bloco:

- `VALIDATION_RESULT=OK_UI_MOBILE_ESTATICA`;
- arquivos analisados: 69;
- alertas encontrados: 0;
- alertas sem justificativa: 0.

## SDD mestre

Documentos criados:

- `docs/v3/SDD.md`;
- `docs/v3/SDD-indice-rastreabilidade.md`;
- `docs/v3/SDD-decisoes-consolidadas.md`;
- `docs/v3/SDD-pendencias-gates.md`.

O SDD central consolida arquitetura, rotas publicas, classificacao, idade, WhatsApp, metricas, Premium, creditos, pagamentos, Efi, importador, admin/RBAC, moderacao, outbox, midia/CDN, visual, seguranca, banco/migrations, ambientes, criterios de go-live, pendencias e historico de blocos.

## Continuidade no Bloco 26.2

O Bloco 26.2 completa a correcao visual transformando `/anunciar` em wizard progressivo, adicionando preview Premium administrativo local e criando documentacao central de SEO.

Ele nao altera a decisao do Bloco 26.1: nao ha redesign, nova paleta, nova tipografia, publicacao automatica, upload real, pagamento, Pix/Efi ou Premium obrigatorio.

## Evidencias

Os prints obrigatorios ficam em `docs/v3/evidencias/bloco-26-1/` e estao listados em `docs/v3/evidencias/bloco-26-1/relatorio-prints.md`.

Na verificacao por navegador local, o viewport mobile final retornou `scrollWidth=375` e `clientWidth=375`, sem overflow horizontal observado.

Admin nao foi alterado por este bloco, por isso os prints `desktop-admin-solicitacao.png` e `mobile-admin-solicitacao.png` nao foram gerados.

## Proibicoes preservadas

Nao houve:

- redesign;
- nova paleta;
- nova tipografia;
- nova regra de negocio;
- publicacao automatica;
- pagamento, credito, Pix/Efi ou Premium obrigatorio;
- upload real;
- importador real;
- dado real;
- dump real;
- producao;
- VPS;
- banco de producao;
- API externa;
- OpenAI;
- migration;
- SQL de schema;
- remote;
- push;
- commit.
