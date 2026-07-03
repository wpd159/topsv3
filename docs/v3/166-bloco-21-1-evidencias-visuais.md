# Bloco 21.1 - evidencias visuais e correcao pos-auditoria

## Objetivo

Corrigir bloqueios da auditoria visual do Bloco 21 sem redesign, sem nova regra de negocio e sem uso de producao, dado real ou midia real.

Correcoes aplicadas:

- prints locais obrigatorios gerados;
- age gate sem data pre-preenchida;
- botao de confirmacao de idade bloqueado ate data valida;
- textos publicos visiveis menos tecnicos;
- metadados do pacote preparados para preencher testes no `RESUMO-ENTREGA.md`;
- CSS publico reforcado para viewport mobile estavel.

## Evidencias geradas

Prints desktop:

- `docs/v3/evidencias/bloco-21/desktop-home.png`;
- `docs/v3/evidencias/bloco-21/desktop-anuncio.png`;
- `docs/v3/evidencias/bloco-21/desktop-anuncio-bloqueado-agegate.png`;
- `docs/v3/evidencias/bloco-21/desktop-cidade.png`;
- `docs/v3/evidencias/bloco-21/desktop-bairro.png`;
- `docs/v3/evidencias/bloco-21/desktop-anuncio-placeholder-contato.png`.

Prints mobile:

- `docs/v3/evidencias/bloco-21/mobile-home.png`;
- `docs/v3/evidencias/bloco-21/mobile-anuncio.png`;
- `docs/v3/evidencias/bloco-21/mobile-anuncio-bloqueado-agegate.png`;
- `docs/v3/evidencias/bloco-21/mobile-cidade.png`;
- `docs/v3/evidencias/bloco-21/mobile-bairro.png`;
- `docs/v3/evidencias/bloco-21/mobile-anuncio-placeholder-contato.png`.

Os prints foram gerados com:

- PostgreSQL local descartavel `postgres:16`;
- migrations V001-V017;
- dados sinteticos locais;
- backend local;
- frontend local em modo production local (`next build` + `next start`);
- Edge headless local;
- sem producao;
- sem dado real;
- sem imagem real;
- sem midia real;
- sem WhatsApp real;
- sem storage key, bucket ou hash.

## Age gate

`PublicAgeGateContent` e `PublicAgeGateStories` nao preenchem mais `1990-01-01`.

O campo de data inicia vazio. O botao `Confirmar idade` fica desabilitado enquanto a data estiver vazia, invalida ou posterior ao limite local permitido.

O frontend continua sem decidir idade como fonte final. A autorizacao continua vindo do backend, com cookie HttpOnly emitido pelo backend local.

## Textos visiveis

Foram suavizados textos visiveis que pareciam tecnicos demais:

- marca publica visivel usa `Tops do Job`, nao `Tops do Job V3`;
- `SKELETON LOCAL` deixou de ser texto principal e ficou apenas como identificador tecnico no codigo;
- `API LOCAL` deixou de aparecer como selo principal do detalhe;
- rotulos como `Fluxo local`, `Backend` e `Visualizacao local` foram reduzidos para `Fluxo`, `Autorizacao` e `Visualizacao`;
- home passou a usar texto de navegacao publica com exemplos sinteticos.

Mensagens locais permanecem somente para fallback, evidencia visual e seguranca do ambiente local.

## Mobile

O CSS publico foi reforcado com:

- `width: 100%`;
- `max-width: 100%`;
- `min-width: 0`;
- `overflow-wrap: anywhere`;
- grid com `minmax(min(100%, ...), 1fr)`;
- botoes e cards no fluxo normal.

Nao foi usado:

- `document.body.style.overflow`;
- scroll lock;
- `position: fixed`;
- `position: absolute`;
- `position: sticky`;
- `100vw`;
- animacao automatica;
- `transform`;
- `translate`.

Resultado:

```text
VALIDATION_RESULT=OK_UI_MOBILE_ESTATICA
```

## Validacoes

Validacoes executadas nesta correcao:

- diagnostico de toolchain local;
- build local agregado;
- E2E local descartavel;
- API publica local;
- persistencia JPA estatica;
- validacao mobile estatica;
- scanners de seguranca;
- rotas publicas/SEO local;
- migrations SQL estaticas;
- fonte de importacao local;
- backend compile/test;
- frontend lint/build;
- `git diff --check`;
- `git diff --cached --check`;
- `git status --short`;
- `git remote -v`.

## Confirmacoes

Nao houve:

- redesign;
- nova identidade visual;
- nova paleta;
- nova tipografia;
- animacao automatica;
- elemento solto, dancando ou flutuante;
- scroll lock;
- uso de `document.body.style.overflow`;
- producao alterada;
- VPS alterada;
- banco de producao;
- API externa;
- OpenAI;
- Efi real;
- dado real;
- dump real;
- migration;
- SQL de schema;
- importador real;
- remote;
- push;
- commit;
- fase posterior iniciada.

## Pendencias

- Fonte visual completa do site atual ainda depende de material aprovado.
- CDN/midia publica real permanece pendente.
- Revisao visual humana ainda e necessaria antes de homologacao/producao.
