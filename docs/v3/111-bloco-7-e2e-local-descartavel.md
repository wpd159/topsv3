# Bloco 7 - E2E local descartavel

## Objetivo

Executar validacao local end-to-end com PostgreSQL descartavel, migrations V001-V017, backend local, API publica e smoke tests HTTP, usando somente dados sinteticos minimos.

## Resultado

Status da execucao:

```text
OK_E2E_LOCAL_DESCARTAVEL
```

Resumo operacional:

- Docker daemon local disponivel;
- imagem local usada: `postgres:16`;
- PostgreSQL descartavel iniciado sem volume persistente;
- migrations V001-V017 aplicadas via `psql` ordenado;
- dados sinteticos minimos aplicados;
- backend iniciado em perfil `local` na porta `18080`;
- health/readiness responderam;
- smoke HTTP da API publica executado com sucesso;
- backend encerrado;
- container e rede descartaveis removidos;
- nenhum volume persistente criado.

## Relatorio temporario da execucao

Relatorio gerado fora do repositorio:

```text
C:\Users\WpD\AppData\Local\Temp\topsv3-e2e-local-descartavel-20260701-002231.md
```

O relatorio registrou 68 tabelas no schema `public` apos as migrations.

## Migrations

Foram aplicadas localmente em banco descartavel:

- V001 a V017;
- sem alterar migrations;
- sem criar migration nova;
- sem alterar SQL de schema;
- sem Flyway real nesta execucao de e2e;
- sem banco persistente.

O metodo usado foi `SQL_ORDENADO_PSQL`, usando o `psql` do proprio container PostgreSQL local.

## Dados sinteticos

Arquivo criado:

```text
scripts/local/dados-sinteticos/dados-publicos-minimos.sql
```

Dados criados:

- usuario tecnico sintetico sem e-mail e sem telefone;
- UF ficticia `ZZ`;
- cidade ficticia `cidade-sintetica`;
- bairro ficticio `bairro-sintetico`;
- anuncio ficticio `anuncio-sintetico-local`;
- localizacao publica sintetica;
- registros SEO locais sinteticos para anuncio, cidade, bairro, sitemap e robots.

Nao foram criados:

- nome real;
- telefone real;
- e-mail real;
- CPF;
- documento;
- foto real;
- URL real;
- conteudo adulto real;
- WhatsApp publico;
- pagamento real;
- credito real;
- midia real;
- storage key real;
- bucket real.

## Backend local

`backend/src/main/resources/application-local.yml` foi ajustado para profile local seguro:

- datasource por variaveis de ambiente locais;
- `ddl-auto: validate`;
- `open-in-view: false`;
- `sql.init.mode: never`;
- sem segredo real;
- sem URL de producao;
- sem banco de producao.

O e2e tambem revelou divergencias reais entre JPA e schema para campos `char(n)`. Foram corrigidos apenas mapeamentos JPA, sem alterar SQL:

- campos `char(2)` de UF/origem;
- campo `char(3)` de moeda em pagamento.

## Processo de pacote

A inconsistencia do pacote do Bloco 6 foi tratada no processo do Bloco 7.

O empacotador ja suporta `-MetadadosExecucao`. Neste bloco, o pacote final deve receber JSON de metadados com as validacoes realmente executadas, para que `RESUMO-ENTREGA.md` nao registre "Testes executados: Nenhum" quando houver build, testes e scanners.

Status:

```text
RESUMO_ENTREGA_TESTES_EXECUTADOS_CORRIGIDO_VIA_METADADOS
```

Nao houve invencao de testes.

## Garantias

Nao houve:

- producao;
- VPS;
- banco de producao;
- Efi real;
- OpenAI;
- API externa de negocio;
- dump real;
- dados reais;
- arquivo real de entrada;
- ETL real;
- migration nova;
- alteracao de SQL de schema;
- seed real;
- admin funcional;
- autenticacao real;
- sessao/token real;
- Pix/Efi funcional;
- financeiro funcional;
- endpoint de acao critica;
- endpoint de moderacao real;
- importador real;
- WhatsApp publico;
- documento privado exposto;
- storage key/hash/bucket expostos;
- remote;
- push;
- commit.

## Riscos residuais

- E2E usou `psql` ordenado, nao Flyway CLI real;
- o schema segue aguardando revisao Pro antes de fases dependentes;
- dados sinteticos sao minimos e nao validam jornada publica final;
- URL publica/CDN de midia segue pendente;
- politica publica de WhatsApp segue pendente.

## Complemento Bloco 8

O E2E local passou a validar tambem os endpoints:

- `POST /api/public/anuncios/anuncio-sintetico-local/visualizacao`;
- `POST /api/public/anuncios/anuncio-sintetico-local/clique-whatsapp`.

O dado sintetico usa somente classificacao `LIVRE` para o anuncio publico, story `BLOQUEADO` nao exposto e WhatsApp ficticio autorizado apenas no endpoint de clique.
