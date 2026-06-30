# Inventário de textos públicos da produção

## Resumo da captura

- Captura executada: sim.
- Método usado: GET.
- Domínio permitido: `https://topsdojob.com`.
- Total capturado: 4.
- Total ignorado: 24.
- Diretório sanitizado: `docs/v3/conteudo-publico-capturado/`.

## URLs capturadas

| URL | Status | Tipo | Arquivo | Decisão |
| --- | --- | --- | --- | --- |
| `https://topsdojob.com/` | 200 | HOME | `home.json` | PENDENTE_REVISAO |
| `https://topsdojob.com/sobre` | 200 | INSTITUCIONAL | `sobre.json` | PENDENTE_REVISAO |
| `https://topsdojob.com/sitemap.xml` | 200 | SITEMAP | `sitemap.xml.json` | PENDENTE_REVISAO |
| `https://topsdojob.com/robots.txt` | 200 | ROBOTS | `robots.txt.json` | ADAPTAR |

## URLs ignoradas

| URL | Motivo |
| --- | --- |
| `https://topsdojob.com/como-funciona` | GET público retornou 404. |
| `https://topsdojob.com/seguranca` | GET público retornou 404. |
| `https://topsdojob.com/anunciar` | Redirecionou para URL fora do escopo permitido. |
| `https://topsdojob.com/perguntas-frequentes` | GET público retornou 404. |
| `https://topsdojob.com/acompanhantes/go` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/se` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/mg` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/pa` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/df` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/sp` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/ms` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/mt` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/pr` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/ba` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/sc` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/ce` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/pb` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/am` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/ro` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/pe` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/rj` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/ma` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/pi` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |
| `https://topsdojob.com/acompanhantes/go/aparecida-de-goiania` | Descoberta no sitemap; não capturada por risco de telefone, conteúdo sensível ou volume. |

## Textos aproveitáveis

Nenhum texto foi marcado como `REAPROVEITAR` nesta execução.

## Textos para adaptar

- `robots.txt`: adaptar regras futuras por ambiente, mantendo bloqueios para parâmetros fracos, áreas privadas e sitemap correto.

## Textos que exigem revisão

- Home: texto principal não capturado por política conservadora de conteúdo sensível.
- Sobre: texto principal não capturado por política conservadora de conteúdo sensível.
- Sitemap: usado apenas para descoberta de URLs públicas.

## Textos descartados

Nenhum texto foi marcado como `DESCARTAR` nesta execução.

## Observações

Páginas de anúncio e páginas locais foram deliberadamente ignoradas nesta execução por risco de telefone, WhatsApp, dados pessoais, conteúdo sensível ou volume. A captura dessas rotas exige revisão específica e amostra controlada em fase futura.
