# Bloco 35 - admin/moderacao sintetica local

## Objetivo

O Bloco 35 executa duas frentes locais e controladas:

- preliminar obrigatoria de correcao da copy publica do wizard `/anunciar`;
- validacao sintetica local do fluxo administrativo/moderacao ja existente no projeto.

Nao ha uso de dados reais, producao, VPS, restore, quarentena, Pix/Efi real, pagamento real, upload real, e-mail real ou WhatsApp real.

## Preliminar do wizard

O Bloco 34 foi reprovado para checkpoint porque a UI publica do wizard continha textos de bastidor:

- `V3 local`;
- `dados sinteticos locais`;
- `A producao destaca`;
- `producao observavel`;
- `ambiente local`.

A preliminar do Bloco 35 corrige apenas apresentacao publica:

- `Comece seu cadastro com dados sinteticos locais.` foi substituido por `Comece seu cadastro preenchendo as informacoes principais.`;
- `A producao destaca... Na V3 local...` foi substituido por `Crie seu anuncio em etapas simples. O envio e gratuito e passa por revisao antes de qualquer publicacao.`;
- `Fotos, videos e pagamentos ficam fora deste fluxo local.` foi substituido por `Fotos, videos e pagamentos ficam fora deste cadastro inicial.`;
- `A producao valoriza midia revisada, mas esta etapa local nao envia arquivo real.` foi substituido por `As midias passam por revisao antes de aparecerem publicamente.`;
- o validador do wizard passou a reprovar copy de bastidor renderizada.

Nenhuma regra de negocio, rota, DTO, contrato, seguranca, RBAC, backend ou fluxo funcional foi alterado nessa preliminar.

## Checkpoint Bloco 34

O checkpoint local do Bloco 34 corrigido foi criado em `9b677ea` com a mensagem:

`test: consolida paridade wizard producao ate bloco 34`

`git remote -v` permaneceu vazio e nao houve push. Os documentos do Bloco 35 ficaram fora desse commit e permanecem no delta do proprio Bloco 35.

## Admin/moderacao sintetica

A validacao admin/moderacao deve usar somente rotas, endpoints e acoes que ja existem no projeto.

Escopo permitido:

- login/admin local sintetico;
- leitura/listagem/detalhe administrativo local;
- moderacao local sintetica;
- aprovar, reprovar e solicitar ajuste somente quando ja implementados localmente;
- auditoria sanitizada;
- outbox local/read-only/simulado, se existente.

Fora do escopo:

- criar regra grande nova;
- criar migration;
- usar dado real;
- hard delete;
- e-mail real;
- WhatsApp real;
- upload real;
- pagamento/Pix/Efi real;
- publicacao automatica real;
- producao, VPS, restore ou API externa.

## Resultado funcional

O validador `scripts/local/validar-admin-moderacao-sintetica-local.ps1` foi criado para executar o fluxo local descartavel com prefixo Docker `topsv3-admin-sintetico-*`.

Resultado aprovado:

- PostgreSQL descartavel com imagem local `postgres:16`;
- migrations V001 a V017 aplicadas via psql ordenado;
- dados sinteticos publicos/admin e fixture JSON aplicados;
- backend local iniciado em `http://127.0.0.1:18135`;
- frontend local iniciado em `http://127.0.0.1:18335`;
- smoke HTTP com 1089 verificacoes OK;
- UI admin e `/admin/moderacao` renderizadas em desktop/mobile;
- prints sinteticos gerados em `docs/v3/evidencias/bloco-35/prints/`;
- auditoria local de moderacao com 9 eventos sanitizados;
- `SOLICITAR_AJUSTE` sem decisao final indevida;
- outbox local com preview/simulacao sem envio externo.

Durante o desenvolvimento do validador, tentativas reprovadas por UI/cleanup foram limpas manualmente apenas nos recursos proprios `topsv3-admin-sintetico-*`. Recursos `cripto-*`/TopsWI foram apenas detectados e preservados.

## Ajuste visual admin

Foi feito ajuste minimo de apresentacao no admin local para nao renderizar `UPPER_SNAKE_CASE` ou termos de shell/skeleton para o usuario:

- helper `adminDisplay.ts` formata papeis, permissoes, status, tipo de evento e destinos logicos;
- `AdminShell` trocou copy de skeleton por copy de painel admin local;
- `AdminModerationPanel` e `AdminOutboxPanel` continuam usando os mesmos DTOs/contratos, apenas com rotulos humanos na UI.

Nao houve redesign, nova paleta, nova tipografia, nova animacao, botao flutuante, scroll lock, migration, backend novo ou mudanca de regra.

## Gates

Pro nao e necessario para este bloco local/sintetico. Pro continua obrigatorio antes de homologacao/cutover real, dados reais/sanitizados, restore completo, financeiro, Pix/Efi, webhooks, importador real, autenticacao/RBAC de producao ou producao.

Bloco 29 segue materialmente aberto e adiado para pre-staging/cutover. A quarentena sanitizada sem `POST_DATA` continua proibida para staging final.
