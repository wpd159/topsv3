# Preservação visual atual

## Objetivo

Registrar a diretriz de que a V3 deve preservar o visual atual do Tops do Job. Esta fase não cria nova identidade visual, não redefine layout e não transforma skeletons locais em experiência final.

A V3 deve manter a aparência geral, a navegação pública, a lógica visual das páginas e a linguagem institucional do site atual. Melhorias futuras são permitidas apenas quando forem leves, compatíveis com o visual existente, justificadas e aprovadas.

## Regra principal

O visual atual é a referência de continuidade da V3.

Isso significa:

- home, listagens, páginas locais e página de anúncio devem manter a lógica visual atual;
- cards, badges, botões e hierarquia de conteúdo devem preservar a leitura e o comportamento já reconhecidos pelos usuários;
- espaçamentos, proporções e comportamento mobile devem ser inventariados antes de qualquer consolidação visual;
- páginas skeleton locais não são layout final;
- componentes criados nas fases iniciais são placeholders técnicos, não uma proposta de redesign;
- qualquer mudança estrutural de UX depende de aprovação expressa;
- acessibilidade e responsividade podem ser melhoradas, desde que não descaracterizem a experiência atual.

## Elementos que devem ser preservados

Quando a fonte visual atual estiver disponível, a V3 deve inventariar e preservar:

- estrutura visual da home;
- navegação pública;
- listagens por UF, cidade e bairro;
- página pública de anúncio;
- páginas locais;
- estilo de cards;
- botões e CTAs;
- badges e marcadores visuais;
- cores predominantes;
- tipografia usada atualmente;
- espaçamentos e densidade visual;
- comportamento responsivo e mobile;
- hierarquia visual;
- linguagem institucional;
- padrões de banner.

## Banners

Banners devem manter dimensões fixas documentadas:

| Variante | Dimensão obrigatória |
| --- | --- |
| Desktop | 1452 x 500 px |
| Mobile | 1080 x 900 px |

O conteúdo dos banners deve ser administrável no futuro pelo admin, com histórico, revisão e rollback conforme fases posteriores. Esta fase não cria banner real, mídia real ou fluxo administrativo funcional.

## Melhorias permitidas

Melhorias visuais futuras devem cumprir todos os critérios abaixo:

- serem leves e compatíveis com o visual atual;
- terem justificativa objetiva;
- preservarem URLs, SEO, navegação e reconhecimento do site;
- não criarem nova paleta, nova tipografia ou novo layout por padrão;
- não substituírem a experiência atual por uma identidade visual nova;
- respeitarem acessibilidade, legibilidade e responsividade.

## Aprovação necessária

Dependem de aprovação expressa:

- troca de paleta;
- troca de tipografia;
- redesenho de cards;
- redesenho da home;
- redesign de listagens;
- mudança estrutural da página de anúncio;
- novo layout premium;
- alteração estrutural de navegação;
- qualquer direção visual que possa descaracterizar o site atual.

## Relação com os skeletons locais

Os skeletons criados nas fases 1C.4, 1C.5 e 1C.6B existem apenas para validar rotas, metadata local, navegação estrutural e limites de segurança.

Eles não representam:

- layout final da V3;
- identidade visual nova;
- paleta final;
- tipografia final;
- desenho final de cards;
- fluxo público funcional;
- painel admin funcional.

Antes de consolidar componentes finais do frontend, a V3 deve receber arquivos, prints ou referências confiáveis do visual atual.

## Proteção das rotas durante o skeleton

A preservação visual não altera contratos públicos. Enquanto o visual final aguarda fonte real do legado, a Fase 1C.8 protege localmente as rotas skeleton e SEO local por script estático.

`/anuncios/[slug]` continua sendo o contrato público absoluto da página de anúncio. Rotas alternativas como `/anuncio/[id]`, `/perfil/[slug]`, `/acompanhante/[slug]` e `/ads/[slug]` continuam proibidas.

## Pendência

Status da fonte visual atual nesta fase: `PENDENTE_FONTE_VISUAL_ATUAL`.

Enquanto essa pendência estiver aberta, nenhuma decisão visual final deve ser tomada com base apenas nos skeletons locais.

## Complemento Bloco 6

O Bloco 6 integrou o frontend publico skeleton a API local de leitura sem redesign.

Foram preservados:

- shell publico existente;
- CSS global existente;
- paleta atual do skeleton local;
- tipografia atual do skeleton local;
- ausencia de imagem real;
- ausencia de dados reais.

Os paineis adicionados usam classes ja existentes, como `panel`, `muted` e `health-grid`. Eles indicam estado estrutural da API local e fallback seguro, mas nao representam layout final da V3.
