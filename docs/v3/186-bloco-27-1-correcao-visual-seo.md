# Bloco 27.1 - Correcao visual SEO publico

## Objetivo

Corrigir a reprovacao visual do Bloco 27 nas paginas publicas de SEO e criar um gate renderizado para impedir que layout espremido, H1 verticalizado ou breadcrumbs quebrados passem novamente.

Este bloco nao inicia fase posterior e nao altera producao.

## Diagnostico dos prints do Bloco 27

Foram analisados os prints de:

- `docs/v3/evidencias/bloco-27/home-linkagem-interna.png`
- `docs/v3/evidencias/bloco-27/cidade-seo.png`
- `docs/v3/evidencias/bloco-27/bairro-seo.png`
- `docs/v3/evidencias/bloco-27/anuncio-seo.png`
- `docs/v3/evidencias/bloco-27/anunciar-link-seo.png`

Os prints mostravam conteudo publico confinado em mini-coluna a esquerda, com H1 e breadcrumbs quebrando letra por letra. A pagina `/anunciar` tambem aparecia estreita, com o wizard comprimido.

## Causa raiz

A causa operacional foi a ausencia de gate renderizado que medisse dimensoes reais. Os validadores do Bloco 27 confirmavam metadados, textos, rotas e ausencia de scroll horizontal, mas nao mediam largura do shell, largura/altura de H1, largura dos breadcrumbs ou largura do wizard.

A causa de layout protegida nesta correcao foi:

- shell publico sem contrato explicito suficiente de `inline-size`/`max-inline-size`;
- uso amplo de `overflow-wrap:anywhere`, perigoso para H1, breadcrumbs, botoes e textos humanos;
- breadcrumbs com links para rotas inexistentes (`/acompanhantes` e `/acompanhantes/[uf]`);
- ausencia de validador renderizado capaz de reprovar mini-coluna.

Durante a verificacao local, a pagina renderizada em viewport correto ja apresentava largura normal, indicando que a evidencia anterior nao provava as dimensoes reais. Mesmo assim, o CSS foi endurecido para reduzir risco de regressao e o gate renderizado passou a ser obrigatorio.

## Correcoes aplicadas

- `html`, `body`, `main`, `.shell` e `.public-shell` receberam largura explicita e limites responsivos.
- `.public-shell` passou a usar `width: min(100%, 1120px)` e `max-inline-size: 1120px`.
- H1, breadcrumbs, botoes, links publicos e wizard passaram a usar quebra por palavra, nao por letra.
- `overflow-wrap:anywhere` ficou restrito ao token tecnico `.route-pattern`.
- `PublicBreadcrumbs` passou a renderizar texto quando nao existe rota publica segura.
- Breadcrumb de cidade:
  - `Inicio` aponta para `/`;
  - `Acompanhantes` e UF sao texto;
  - cidade e item atual.
- Breadcrumb de bairro:
  - `Inicio` aponta para `/`;
  - `Acompanhantes` e UF sao texto;
  - cidade aponta para `/acompanhantes/[uf]/[cidade]`;
  - bairro e item atual.
- Breadcrumb de anuncio:
  - `Inicio` aponta para `/`;
  - cidade/bairro apontam apenas quando a localidade existe;
  - anuncio e item atual.
- Texto publico tecnico foi removido:
  - `SEO por cidade e bairro` virou `Acompanhantes por cidade e bairro`;
  - `Texto SEO local` virou `Conteudo por cidade e bairro`;
  - paginas institucionais deixaram de exibir linguagem de skeleton/V3/dados reais.
- O indicador visual do Next em modo dev foi desabilitado com `devIndicators: false` para nao aparecer em evidencias.

## Gate renderizado

Criado:

- `scripts/local/validar-layout-publico-renderizado.ps1`

O script usa somente ferramenta local ja instalada:

- Node.js local;
- Edge/Chrome/Chromium local;
- Chrome DevTools Protocol em modo headless;
- frontend local em `127.0.0.1`.

O script nao instala dependencias, nao acessa producao e nao usa API externa.

Rotas medidas:

- `/`
- `/acompanhantes/go/goiania`
- `/acompanhantes/go/goiania/setor-bueno`
- `/anuncios/anuncio-exemplo`
- `/anunciar`

Viewports:

- desktop `1280x900`;
- mobile `390x844`.

Validacoes renderizadas:

- shell publico com largura minima;
- H1 com largura minima;
- H1 sem altura/proporcao de texto verticalizado;
- breadcrumbs com largura/altura legiveis;
- wizard `/anunciar` com largura legivel;
- ausencia de scroll horizontal;
- ausencia de `document.body.style.overflow`;
- ausencia de `overflow-x:hidden` global;
- ausencia de texto tecnico publico;
- ausencia de links para `/acompanhantes` e `/acompanhantes/[uf]`;
- ausencia de `fixed`, `absolute` ou `sticky` em elementos publicos do app.

Artefatos internos do Next em modo dev sao ignorados pelo gate de posicao, pois nao pertencem a UI publica do Tops do Job.

## Evidencias novas

Geradas em:

- `docs/v3/evidencias/bloco-27-1/`

Arquivos:

- `home-linkagem-interna.png`
- `cidade-seo.png`
- `bairro-seo.png`
- `anuncio-seo.png`
- `anunciar-link-seo.png`
- `home-linkagem-interna-mobile.png`
- `cidade-seo-mobile.png`
- `bairro-seo-mobile.png`
- `anuncio-seo-mobile.png`
- `anunciar-link-seo-mobile.png`
- `relatorio-prints.md`
- `relatorio-layout-renderizado.md`

Resultado renderizado:

- 10 verificacoes;
- 0 falhas;
- home, cidade, bairro, anuncio e `/anunciar` sem mini-coluna;
- H1 horizontal legivel;
- breadcrumbs legiveis;
- wizard legivel;
- sem scroll horizontal.

## Sem redesign

Nao houve:

- nova identidade visual;
- nova paleta;
- nova tipografia;
- redesign de home;
- redesign completo de cards;
- animacao automatica;
- elemento solto/flutuante/dancando;
- scroll lock.

## Proibicoes preservadas

Nao houve:

- producao alterada;
- banco de producao;
- dado real;
- midia real;
- migration;
- SQL de schema;
- upload real;
- e-mail real;
- WhatsApp real;
- pagamento;
- credito;
- Pix/Efi;
- checkout;
- webhook;
- importador real;
- API externa;
- remote;
- push;
- commit.

## Regra futura

O Bloco 27 nao deve ser considerado aprovado visualmente sem executar `scripts/local/validar-layout-publico-renderizado.ps1` e revisar os prints de `docs/v3/evidencias/bloco-27-1/`.
