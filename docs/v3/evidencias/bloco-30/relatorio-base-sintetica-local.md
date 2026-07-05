# Relatorio - base sintetica local

- Bloco: 30
- Resultado: BASE_SINTETICA_LOCAL_CONSOLIDADA
- Fixture: `backend/src/test/resources/fixtures/v3-dados-sinteticos.json`
- Banco acessado: nao
- Docker acessado: nao
- Producao/VPS acessados: nao
- Dados reais usados: nao
- Midia real/documento real usados: nao

## Contagens

- cidades: 5
- bairros: 9
- anuncios: 12
- anuncios_livre: 9
- anuncios_bloqueado: 3
- anuncios_ativos: 8
- anuncios_pausados: 1
- anuncios_pendentes: 1
- anuncios_rejeitados: 1
- anuncios_controle_negativo: 1
- anuncios_premium_ativo: 4
- anuncios_premium_expirado: 2
- anuncios_gratuitos: 6
- beneficios_premium: 6
- metricas_agregadas: 6
- rotas_cobertas: 9
- casos_admin: 6

## Uso previsto

- Desenvolvimento local da V3 sem dependencia de backup/restauracao de producao.
- Testes de rotas publicas, SEO, wizard /anunciar, admin read-only, moderacao local, Premium read-only, age gate e metricas agregadas.
- A fixture nao substitui dados sanitizados de pre-staging/cutover.
