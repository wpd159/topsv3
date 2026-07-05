# Relatorio - auditoria E2E sintetico Bloco 31

## Escopo auditado

- `scripts/local/validar-e2e-local-descartavel.ps1`
- `scripts/local/validar-api-publica-local.ps1`
- `scripts/local/validar-build-local.ps1`
- `scripts/local/dados-sinteticos/dados-publicos-minimos.sql`
- `scripts/local/dados-sinteticos/dados-admin-minimos.sql`
- `backend/src/test/resources/fixtures/v3-dados-sinteticos.json`

## Achados

- O E2E local existente sobe PostgreSQL descartavel via Docker, aplica migrations V001-V017, carrega seeds sinteticos SQL, inicia backend local e executa smoke HTTP.
- O script antigo usa nomes Docker com prefixo `topsv3-bloco29-e2e-*`; esse prefixo nao deve ser usado no Bloco 31.
- O E2E antigo nao cria volume persistente, usa porta efemera do PostgreSQL e remove apenas o container/rede que cria.
- O smoke HTTP existente usa slugs sinteticos antigos (`anuncio-sintetico-local`, `cidade-sintetica`) e cobre muitos fluxos publicos/admin.
- A fixture do Bloco 30 usa slugs `demo-*`, cidades reais como nomes publicos sinteticos e dominio local; ela ainda nao era carregada pelo E2E.
- O backend usa PostgreSQL no perfil local; sem banco descartavel a API HTTP nao sobe de forma util para smoke completo.
- Frontend publico usa API local via rotas reais e nao decide classificacao.

## Decisao de implementacao

Estender o mecanismo existente de E2E com parametros seguros, sem duplicar arquitetura:

- permitir prefixo Docker parametrico;
- usar obrigatoriamente `topsv3-e2e-sintetico-*` no Bloco 31;
- carregar a fixture JSON `v3-dados-sinteticos.json` como overlay sintetico no banco descartavel;
- criar scripts finos para API/SEO sinteticos;
- manter os seeds SQL antigos para nao regredir os smokes ja existentes;
- nunca usar recursos `topsv3-bloco29-*` como staging final;
- nunca tocar TopsWI/cripto, volumes ou networks de terceiros;
- nao executar prune, compose down, pull automatico ou remocao fora do prefixo do Bloco 31.

## Recursos Docker permitidos para este bloco

- Containers temporarios com prefixo `topsv3-e2e-sintetico-pg-`.
- Networks temporarias com prefixo `topsv3-e2e-sintetico-net-`.
- Volumes persistentes: nenhum.

## Limites

Esta auditoria nao acessou producao, VPS, banco de producao, backup, dump, SQL bruto real, log bruto, Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real ou API externa real.
