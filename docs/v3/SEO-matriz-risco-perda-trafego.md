# SEO - matriz de risco de perda de trafego

| Risco | Severidade | Probabilidade | Mitigacao | Gate | Rollback |
| --- | --- | --- | --- | --- | --- |
| Alteracao de slug | alta | media | preservar `/anuncios/[slug]` quando indexavel | mapa URL aprovado | restaurar slug ou 301 seguro |
| Mudanca de canonical | critica | media | validar canonical por ambiente | gate canonical | reverter canonical publicado |
| Sitemap malformado | alta | baixa | validar XML e amostras | gate sitemap | publicar sitemap anterior |
| Robots bloqueando producao | critica | baixa | validar robots antes do cutover | gate robots | restaurar robots anterior |
| Paginas vazias no sitemap | alta | media | excluir paginas fracas/vazias | gate conteudo | remover do sitemap/noindex |
| Noindex acidental | critica | media | bloquear remocao fora do cutover | gate noindex | reverter meta/robots |
| Linkagem interna quebrada | alta | media | validar rotas publicas e breadcrumbs | gate rotas/SEO | restaurar links anteriores |
| Remocao de URL indexada | alta | media | mapear antes de remover | mapa Search Console | 301/410 conforme decisao |
| Alteracao de conteudo local | media | media | revisar title/H1/description | gate conteudo | restaurar conteudo anterior |
| Troca de dominio/www | critica | baixa | manter `https://topsdojob.com` sem `www` | gate dominio | reverter host/canonical |
| Mudanca brusca de title/H1 | alta | media | preservar intencao principal | gate metadata | restaurar padrao aprovado |
| Admin/API no sitemap | critica | baixa | validar sitemap sem rotas proibidas | gate mapa SEO | remover e republicar sitemap |
| Anuncio removido redirecionado para home | alta | media | nao usar redirect generico | gate 301 | desfazer regra |

## Principio de resposta

Qualquer queda relevante apos cutover deve ser analisada com Search Console, logs publicos e mapa de URLs. A resposta deve priorizar restaurar rastreabilidade e reduzir erro de crawler antes de criar novas paginas.
