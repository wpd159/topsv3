# Diretriz UI mobile - Bloco 21

## Escopo permitido

O Bloco 21 pode fazer melhorias visuais pequenas para deixar a V3 mais limpa, estavel e profissional, preservando o visual atual do Tops do Job.

Melhorias permitidas:

- espacamento;
- alinhamento;
- hierarquia visual;
- contraste;
- legibilidade;
- tamanho de toque no mobile;
- estados vazios;
- loading ou skeleton estavel;
- organizacao de CTA;
- responsividade;
- consistencia de cards;
- reducao de poluicao visual.

## Fora de escopo

Continuam proibidos:

- nova identidade visual;
- nova paleta;
- nova tipografia;
- redesign de home;
- redesign completo de cards;
- layout luxuoso novo;
- animacoes chamativas;
- efeitos visuais desnecessarios;
- mudanca estrutural nao aprovada.

## Regra critica mobile

No mobile, nenhum elemento pode ficar solto, dancando, pulando, flutuando sem controle, sobrepondo conteudo ou causando instabilidade visual.

Regras obrigatorias:

- nao criar elemento que fique se movendo sozinho;
- nao criar animacao automatica em card, CTA, midia, stories, botoes ou banners;
- nao criar carrossel automatico;
- nao criar floating button solto sem aprovacao;
- nao criar barra fixa que cubra conteudo;
- nao criar `sticky` ou `fixed` que sobreponha card, CTA ou rodape;
- nao criar elemento absoluto fora do fluxo sem justificativa;
- nao criar layout com scroll horizontal;
- nao criar card com largura maior que viewport;
- nao criar botao que fique sobre texto ou midia;
- nao criar mudancas bruscas de altura apos carregar dados;
- nao criar layout shift perceptivel no carregamento;
- nao usar `document.body.style.overflow`;
- nao reintroduzir scroll lock;
- nao bloquear o scroll do body;
- nao depender de `100vw` quando isso gerar overflow horizontal;
- usar `max-width: 100%`, `overflow-wrap` e contencao adequada em textos longos;
- respeitar safe area e viewport mobile;
- manter CTA, idade/stories, WhatsApp e placeholders dentro do fluxo normal da pagina.

## Position fixed, absolute ou sticky

Se algum elemento precisar de `position: fixed`, `position: absolute` ou `position: sticky`, a justificativa deve ser registrada neste documento ou no checklist do bloco, com:

- arquivo e seletor/componente;
- motivo do uso;
- confirmacao de que nao sobrepoe conteudo no mobile;
- confirmacao de que nao bloqueia scroll;
- evidencia de validacao local.

No estado atual desta diretriz, nao ha uso novo justificado para o Bloco 21.

## Complemento da implementacao do Bloco 21

Na implementacao local do Bloco 21, os novos componentes publicos foram mantidos no fluxo normal da pagina.

Nao foi introduzido uso de:

- `document.body.style.overflow`;
- scroll lock;
- `position: fixed`;
- `position: absolute`;
- `position: sticky`;
- `100vw`;
- `@keyframes`;
- `animation`;
- `transform`;
- `translate`.

O script `scripts/local/validar-ui-mobile-estatica.ps1` foi executado durante a implementacao e retornou `VALIDATION_RESULT=OK_UI_MOBILE_ESTATICA`, sem alertas.

Continuam sem justificativa ativa porque nao ha excecao visual registrada.

## Complemento Bloco 21.1

A correcao pos-auditoria reforcou a contencao mobile dos componentes publicos.

Foram mantidos proibidos e ausentes:

- `document.body.style.overflow`;
- scroll lock;
- `position: fixed`;
- `position: absolute`;
- `position: sticky`;
- `100vw`;
- animacao automatica;
- `transform`;
- `translate`.

Os prints mobile finais foram gerados em viewport mobile local de 430 px para conferencia visual legivel pelo Edge headless. O CSS permanece responsivo e sem alerta no validador estatico.

## Saida obrigatoria do Bloco 21

A entrega do Bloco 21 deve informar:

- quais melhorias pequenas foram feitas;
- confirmacao de que nao houve redesign;
- confirmacao de que o mobile nao possui elemento solto, dancando ou flutuante indevido;
- confirmacao de que nao ha scroll lock;
- confirmacao de que `document.body.style.overflow` nao foi usado;
- resultado da validacao mobile estatica;
- pendencias mobile restantes, se houver.
