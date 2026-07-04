# SEO - inventario de producao sanitizado

## Escopo

Inventario publico somente leitura executado no Bloco 28.

Foram consultados `robots.txt`, `sitemap.xml`, URLs publicas do sitemap e metadados limitados. Nao houve login, banco, SQL, dump, midia, formulario, admin, deploy ou alteracao de producao.

## Saida bruta externa

Arquivos brutos completos ficam fora do repositorio:

```text
C:\topsv3-auditoria-local\seo\bloco-28
```

O repositorio registra apenas contagens, padroes e exemplos sanitizados.

## Contagem por tipo

| Tipo | Total |
| --- | ---: |
| home | 1 |
| cidade | 29 |
| bairro | 21 |
| anuncio | 62 |
| institucional | 1 |
| outros | 0 |
| proibido/admin/api | 0 |
| desconhecido | 45 |

## Padroes de URL

| Tipo | Padrao |
| --- | --- |
| Home | `/` |
| Cidade | `/acompanhantes/[uf]/[cidade]` |
| Bairro | `/acompanhantes/[uf]/[cidade]/[bairro]` |
| Anuncio | `/anuncios/[slug]` |
| Institucional | paginas publicas sem login |
| Proibido | `/admin`, `/api` e equivalentes nao podem entrar no sitemap |

## Amostras sanitizadas

Cidade:

- `/acompanhantes/go/aparecida-de-goiania`;
- `/acompanhantes/se/aracaju`;
- `/acompanhantes/mg/belo-horizonte`;
- `/acompanhantes/pa/belem`;
- `/acompanhantes/df/brasilia`.

Bairro:

- `/acompanhantes/go/goiania/[bairro-publico-amostra-1]`;
- `/acompanhantes/go/goiania/[bairro-publico-amostra-2]`;
- `/acompanhantes/go/goiania/[bairro-publico-amostra-3]`;
- `/acompanhantes/go/goiania/[bairro-publico-amostra-4]`;
- `/acompanhantes/go/goiania/[bairro-publico-amostra-5]`.

Anuncio:

- `/anuncios/[slug-publico-amostra-1]`;
- `/anuncios/[slug-publico-amostra-2]`;
- `/anuncios/[slug-publico-amostra-3]`;
- `/anuncios/[slug-publico-amostra-4]`;
- `/anuncios/[slug-publico-amostra-5]`.

## Metadados observados

- Home: title com proposta de acompanhantes proximos e H1 publico de busca.
- Cidade: title/H1 no padrao `Acompanhantes em [Cidade], [UF]`.
- Bairro: title/H1 no padrao `Acompanhantes em [Bairro], [Cidade]`.
- Anuncio: metadados existem, mas title/description/H1 foram sanitizados porque podem conter nome publico sensivel.
- Robots de cidade/bairro/anuncio em producao publica usam `index, follow` com previews amplos.

## Risco

Slugs de anuncios reais podem carregar nome publico sensivel. Por isso, a lista bruta fica apenas fora do repositorio e nao entra no ZIP de revisao.
