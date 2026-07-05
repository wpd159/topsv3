# Relatorio - SEO com quarentena sanitizada

- Bloco: 29.5
- Resultado: OK_SEO_QUARENTENA_SANITIZADA
- Banco aprovado para staging final: nao
- Slugs reais versionados: nao
- Lista bruta de anuncios reais versionada: nao
- Midia real versionada: nao
- Canonical/sitemap/robots de producao alterados: nao

## Agregados
- bairros_com_conteudo_suficiente_estimados=0
- cidades_com_conteudo_suficiente_estimadas=0
- rotas_impactadas=6
- urls_anuncio_preservaveis_estimadas=520
- urls_desconhecidas_bloco28_classificadas=0
- urls_desconhecidas_bloco28_pendentes=45
- urls_exigem_decisao_estimadas=0
- urls_noindex_removidas_estimadas=3
- paginas_fracas_vazias: dependem de regra Pro por rota antes de cutover.

## Impacto por rota
- /: avaliar agregados de cidade/UF antes de cutover.
- /anuncios/[slug]: usar apenas contagens, sem lista bruta de slugs versionada.
- /acompanhantes/[uf]/[cidade]: validar conteudo suficiente por agregado.
- /acompanhantes/[uf]/[cidade]/[bairro]: validar conteudo suficiente por agregado.
- /sitemap.xml: nao alterar producao neste bloco.
- /robots.txt: nao alterar producao neste bloco.
