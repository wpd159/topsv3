# Inventario visual aplicado na V3 - Bloco 21

## Fonte de referencia

Referencia usada nesta execucao:

- documentos locais de preservacao visual;
- componentes publicos skeleton ja existentes;
- consulta SSH somente leitura para confirmar que a producao atual roda como aplicacao Next compilada e possui assets publicos;
- validadores locais de rotas, SEO, build e mobile.

Nao foram copiados:

- imagens;
- videos;
- CSS de producao;
- HTML completo;
- dados reais;
- arquivos de configuracao;
- segredos;
- dumps;
- midia.

## Principios aplicados

- Preservar a leitura publica de marketplace.
- Manter rotas publicas ja protegidas.
- Melhorar densidade, alinhamento e hierarquia sem redesenhar.
- Usar cards simples e neutros, sem luxo visual novo.
- Manter Arial/Helvetica ja definida no skeleton.
- Manter paleta clara e neutra ja existente no skeleton local.
- Manter CTAs dentro do fluxo.
- Manter placeholders estaveis e sem URL real.
- Manter SEO local `noindex`.

## Mapa de componentes

| Componente | Uso | Observacao |
| --- | --- | --- |
| `PublicHomeHero` | Home local | Links apenas para rotas preservadas locais |
| `PublicAnuncioCard` | Listagens | Card responsivo sem imagem real |
| `PublicAnuncioGrid` | Cidade/bairro | Estado vazio quando API local nao retorna itens |
| `PublicLocalidadeHeader` | Cidade/bairro | Resume rota, total local e status |
| `PublicAnuncioDetalhe` | Detalhe de anuncio | Nao renderiza WhatsApp bruto |
| `PublicMidiaPlaceholder` | Cards/detalhe | Placeholder neutro com dimensao estavel |
| `PublicContatoAction` | CTA WhatsApp | Acao mediada pelo backend local |
| `PublicStoriesGate` | Stories | Stories continuam protegidos por idade |
| `PublicEmptyState` | Fallback | Estados vazios sem poluicao visual |
| `PublicSeoTextBlock` | SEO local | Texto local sem SEO final |

## Rotas preservadas

- `/`;
- `/anuncios/[slug]`;
- `/acompanhantes/[uf]/[cidade]`;
- `/acompanhantes/[uf]/[cidade]/[bairro]`.

## Elementos visuais aplicados

- shell publico com largura maior para acomodar grid;
- hero local simples;
- grid responsivo de anuncios;
- card com midia placeholder, titulo, resumo, localidade, badges e CTA;
- cabecalho de localidade com rota, total e status;
- detalhe com midia placeholder e metadados seguros;
- bloco de stories protegido;
- estado vazio padronizado;
- bloco SEO local neutro.

## Regras preservadas

- Frontend nao decide classificacao.
- Frontend nao libera WhatsApp por conta propria.
- Frontend nao renderiza URL bruta de WhatsApp.
- Frontend nao monta URL de midia.
- Conteudo `BLOQUEADO` permanece dependente do backend e da confirmacao de idade.
- Stories continuam bloqueados sem idade confirmada.
- SEO local continua sem canonical de producao.

## Mobile

Nao houve uso de:

- `position: fixed`;
- `position: absolute`;
- `position: sticky`;
- `100vw`;
- `document.body.style.overflow`;
- `@keyframes`;
- `animation`;
- `transform`;
- `translate`.

Os elementos permanecem no fluxo normal da pagina.

## Pendencias visuais

- Inventario visual completo com fonte de frontend/prints aprovados ainda e pendencia futura.
- Midia publica real depende de desenho de CDN/storage aprovado.
- Ajustes finos de marca, banners e conteudo final dependem de revisao humana.

## Evidencias do Bloco 21.1

Foram adicionadas evidencias versionaveis em `docs/v3/evidencias/bloco-21/`.

Desktop:

- `desktop-home.png`;
- `desktop-anuncio.png`;
- `desktop-anuncio-bloqueado-agegate.png`;
- `desktop-cidade.png`;
- `desktop-bairro.png`;
- `desktop-anuncio-placeholder-contato.png`.

Mobile:

- `mobile-home.png`;
- `mobile-anuncio.png`;
- `mobile-anuncio-bloqueado-agegate.png`;
- `mobile-cidade.png`;
- `mobile-bairro.png`;
- `mobile-anuncio-placeholder-contato.png`.

As evidencias usam dados sinteticos locais, placeholders neutros e nao incluem midia real, WhatsApp real, storage key, bucket, hash ou dado real.
