# Relatorio - validacao de dados da quarentena sanitizada

- Bloco: 29.5
- Resultado: OK_DADOS_QUARENTENA_SANITIZADOS
- Banco de quarentena: `topsv3_quarentena`
- Container de quarentena: `topsv3-bloco29-pg17-quarentena`
- Banco aprovado para staging final: nao
- Valores brutos listados: nao
- Slugs reais listados: nao
- Backup/dump/midia/documento real versionado: nao
- Erros agregados de sanitizacao por coluna: 4
- Classificacao dos erros agregados: `ALERTA_SANITIZACAO_AGREGADA_NAO_CLASSIFICADA`
- Aprovacao final com dados sanitizados: pendente de revisao Pro/humana ou classificacao posterior em novo bloco.

## Achados sensiveis agregados
- cpf: 0
- email: 0
- telefone_whatsapp: 0
- ip_bruto: 0
- url_midia_storage: 0
- token_secret_pix_efi: 0
- total_sensivel: 0

## Agregados estruturais e SEO
- colunas_cidade_uf_bairro=23
- colunas_classificacao=7
- colunas_premium=0
- colunas_slug=9
- colunas_status=27
- linhas_tabelas_anuncio=21461
- slugs_duplicados_agregado=269
- tabelas_total=77

## Observacoes
- Contagem total de anuncios, status, classificacao LIVRE/BLOQUEADO, cidade/UF/bairro e Premium dependem de mapeamento Pro se o schema legado nao usar nomes detectaveis.
- Registros que exigem revisao manual permanecem agregados e sem valores reais.
- O resultado `total_sensivel=0` nao elimina o alerta residual dos 4 erros agregados; ele apenas confirma que os padroes sensiveis verificados nao foram encontrados apos a sanitizacao.
- O banco de quarentena continua proibido para staging final e para validacao transacional definitiva.
