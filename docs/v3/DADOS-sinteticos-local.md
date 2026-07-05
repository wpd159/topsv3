# Dados sinteticos locais

## Objetivo

Definir a base sintetica local do Bloco 30 para permitir continuidade do desenvolvimento da V3 sem backup, dump, restore, sanitizacao, Docker, producao ou banco de quarentena como staging.

## Arquivos

- Fixture: `backend/src/test/resources/fixtures/v3-dados-sinteticos.json`
- Gerador/relatorio: `scripts/local/gerar-dados-sinteticos-v3-local.ps1`
- Validador: `scripts/local/validar-dados-sinteticos-v3-local.ps1`
- Evidencia: `docs/v3/evidencias/bloco-30/relatorio-base-sintetica-local.md`

## Escopo sintetico

- 5 cidades: Goiania/GO, Aparecida de Goiania/GO, Brasilia/DF, Anapolis/GO e cidade local de controle.
- 9 bairros sinteticos, incluindo Setor Bueno, Jardim Goias, Marista, Campinas, Centro, Asa Sul e Asa Norte.
- 12 anuncios sinteticos cobrindo ativo, pausado, pendente, rejeitado e controle negativo.
- Classificacao binaria `LIVRE` e `BLOQUEADO`.
- Planos gratuito, Premium ativo e Premium expirado.
- Casos com bairro, sem bairro, SEO forte, SEO fraco e midia placeholder insuficiente.
- Beneficios Premium sinteticos: destaque, anuncio topo, fotos extra, stories, pacote expirado e gratuito util.
- Metricas agregadas de views e cliques WhatsApp, sem IP bruto e sem user-agent bruto.

## Regras de seguranca

- Nao usar CPF, RG, documento, selfie, e-mail real, telefone real, WhatsApp real ou endereco especifico real.
- Usar apenas dominio reservado `example.test` quando e-mail sintetico for necessario.
- Usar placeholders nao discaveis para WhatsApp e telefone.
- Nao usar imagem real, midia adulta real, storage key real, bucket real ou URL privada.
- Nao usar token, senha, certificado, payload Pix/Efi, pagamento real ou API externa real.
- Frontend nao decide classificacao; backend permanece fonte da decisao.
- `BLOQUEADO` nao libera midia publica nem WhatsApp publico.

## Limites

A base sintetica nao substitui a revisao Pro/humana nem o gate de pre-staging/cutover com backup consistente. Ela serve apenas para desenvolvimento local, validacoes estaticas, rotas publicas locais, SEO local, admin local, moderacao local, Premium read-only, wizard `/anunciar`, age gate e metricas agregadas.

## Bloco 31

A fixture foi integrada ao E2E local por overlay sintetico gerado em tempo de execucao, sem versionar SQL temporario. O overlay e aplicado apenas no PostgreSQL descartavel do script `scripts/local/validar-e2e-sintetico-local.ps1`.

Regras do overlay:

- usa prefixo Docker `topsv3-e2e-sintetico-*`;
- aplica migrations V001-V017 antes dos dados;
- reaproveita seeds sinteticos antigos para os smokes existentes;
- adiciona cidades, bairros, anuncios e SEO da fixture `v3-dados-sinteticos.json`;
- grava slugs publicos de cidade sem sufixo de UF quando a rota preservada exige, por exemplo `goiania-go` vira `/acompanhantes/go/goiania`;
- pula a cidade de controle `ZZ` no overlay para evitar conflito com seed legado `cidade-sintetica`;
- nao cria volume persistente;
- nao usa dados reais, backup, dump, quarentena ou producao.

Validacoes aprovadas:

- `validar-e2e-sintetico-local.ps1`;
- `validar-api-publica-sintetica-local.ps1`;
- `validar-seo-sintetico-local.ps1`;
- `validar-dados-sinteticos-v3-local.ps1`.

## Bloco 31.1

Os validadores sinteticos de API e SEO exigem backend local disponivel por padrao. Se o backend estiver indisponivel, devem retornar pendente com exit code 2 e nao podem aprovar por relatorio antigo.

Compatibilidade por evidencia existente so e permitida com o parametro explicito `-PermitirEvidenciaExistente`, registrando `ALERTA_EVIDENCIA_EXISTENTE_REUTILIZADA` no output e no relatorio.

O E2E descartavel base usa prefixo default `topsv3-e2e-local`. O wrapper sintetico continua usando explicitamente `topsv3-e2e-sintetico` e nao usa recursos de quarentena `topsv3-bloco29-*` como staging.

## Bloco 32

A base sintetica foi usada na auditoria renderizada publica com backend descartavel, frontend local e prints desktop/mobile. O script `scripts/local/validar-publico-renderizado-sintetico-local.ps1` usa prefixo Docker `topsv3-render-sintetico`, renderiza rotas publicas principais e valida que `BLOQUEADO` nao exponha WhatsApp publico indevidamente.

Os prints do Bloco 32 ficam em `docs/v3/evidencias/bloco-32/prints/` e contêm somente dados sinteticos locais.

## Bloco 32.1

A mesma base sintetica foi usada para corrigir a apresentacao publica renderizada. O Bloco 32.1 nao usa dados reais, backup, dump, restore, sanitizacao real ou quarentena como staging.

As novas evidencias ficam em `docs/v3/evidencias/bloco-32-1/` e os prints corrigidos continuam contendo somente dados sinteticos locais.

## Bloco 33

A base sintetica foi usada para validar o wizard publico `/anunciar` em ambiente descartavel, com PostgreSQL local temporario, backend local, frontend local e navegador headless local.

Regras adicionais:

- dados do wizard devem usar somente valores sinteticos e dominio reservado `example.invalid`;
- WhatsApp permitido no fluxo local: `+5500000000000`;
- a localidade sintetica validada usa `GO`, `Goiania` e `Setor Bueno`;
- nenhum upload, foto, video, documento, pagamento, credito, Pix/Efi, Premium obrigatorio, e-mail real ou WhatsApp real e permitido;
- a solicitacao nasce nao publicada e nao pode ser autopublicada pelo wizard.

Evidencias ficam em `docs/v3/evidencias/bloco-33/` e os prints contem somente dados sinteticos locais.
