# Relatorio - dados sinteticos sem dados reais

## Resultado

`OK_DADOS_SINTETICOS_SEM_DADOS_REAIS`

## Fixture

- Caminho: `backend/src/test/resources/fixtures/v3-dados-sinteticos.json`
- `localOnly`: true.
- `noRealData`: true.
- Cidades: 5.
- Bairros: 9.
- Anuncios: 12.
- Anuncios `LIVRE`: 9.
- Anuncios `BLOQUEADO`: 3.
- Premium ativo: 4.
- Premium expirado: 2.
- Gratuitos: 6.
- Metricas agregadas: 6.

## Validacoes

- `scripts/local/validar-dados-sinteticos-v3-local.ps1`: OK.
- E2E sintetico carregou a fixture como overlay descartavel: OK.
- API publica nao retornou CPF, e-mail real, telefone real, documento, storage, token, segredo ou payload financeiro: OK.
- `BLOQUEADO` nao expos WhatsApp publico: OK.

## Confirmacoes

- Dados reais usados: nao.
- Backup/dump usado: nao.
- Quarentena usada como staging final: nao.
- Producao/VPS/banco de producao acessados: nao.
- SQL bruto/log bruto real versionado: nao.
- Midia/documento real versionados: nao.
