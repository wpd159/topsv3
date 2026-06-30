# Dicionario de campos da importacao

Status da entrega: `DICIONARIO ESTRUTURAL LOCAL, SEM DUMP E SEM BANCO`.

## Objetivo

A Fase 2C define um dicionario estrutural dos campos esperados nos arquivos do pacote de importacao. O dicionario e declarativo: nao le dump, nao abre arquivo real de entrada, nao acessa banco, nao importa dados e nao executa ETL.

O dicionario definitivo dependera de fonte real autorizada. Ate la, campos que dependem da origem ficam marcados como `PENDENTE_EVIDENCIA`.

## Contratos Java

Pacote criado:

```text
backend/src/main/java/br/com/topsdojob/v3/importacao/dicionario/
```

Contratos criados:

- `TipoCampoImportacao`;
- `ObrigatoriedadeCampoImportacao`;
- `SensibilidadeCampoImportacao`;
- `CampoPacoteImportacaoDto`;
- `DicionarioArquivoImportacaoDto`;
- `CatalogoDicionarioImportacao`;
- `ValidadorDicionarioImportacao`.

Todos sao Java puro, sem JPA, sem Spring, sem repository, sem controller, sem endpoint, sem acesso a banco, sem leitura de arquivo real e sem rede.

## Tipos de campo

Tipos definidos:

- `TEXTO`;
- `NUMERO_INTEIRO`;
- `NUMERO_DECIMAL`;
- `BOOLEANO`;
- `DATA`;
- `DATA_HORA`;
- `UUID`;
- `JSON`;
- `ENUM`;
- `CHECKSUM`;
- `IDENTIFICADOR_LEGADO`;
- `REFERENCIA_MIDIA`;
- `URL`;
- `EMAIL`;
- `TELEFONE`;
- `DOCUMENTO`;
- `DINHEIRO`;
- `CREDITO`.

`DINHEIRO` representa valor decimal/numeric. `CREDITO` representa quantidade inteira/bigint conceitual, nunca float.

## Catalogo por arquivo

`CatalogoDicionarioImportacao` declara um nucleo minimo para:

- `DUMP_BANCO_LEGADO`;
- `EXPORT_USUARIOS`;
- `EXPORT_ANUNCIOS`;
- `EXPORT_LOCALIDADES`;
- `MANIFESTO_MIDIA`;
- `EXPORT_METRICAS`;
- `EXPORT_PAGAMENTOS`;
- `EXPORT_CREDITOS`;
- `EXPORT_PREMIUM`;
- `EXPORT_BENEFICIOS`;
- `EXPORT_BANNERS`;
- `EXPORT_SEO_CONTEUDOS`;
- `EXPORT_URLS_PUBLICAS`;
- `EXPORT_SLUGS`;
- `CHECKSUMS`;
- `RELATORIO_ORIGEM`.

O catalogo nao declara todos os campos definitivos. Ele cria a base estrutural para revisao futura e marca pontos dependentes de fonte real como `PENDENTE_EVIDENCIA`.

## Regras por dominio

Pagamentos:

- provedor deve ser classificado por evidencia real;
- tabela legada com nome Mercado Pago pode conter Efi;
- Efi nao deve ser convertido em Mercado Pago por nome da tabela;
- Mercado Pago legado nao deve ser convertido em Efi sem evidencia;
- `txid`, status, datas, conciliação e relacao com credito sao evidencias futuras.

Financeiro e creditos:

- valores financeiros devem usar `DINHEIRO` ou tipo decimal/numeric compativel;
- credito deve usar `CREDITO` ou inteiro/bigint conceitual;
- credito nunca deve usar decimal/float;
- divergencia financeira futura deve bloquear promocao.

Midia e documentos:

- documento privado nao e midia publica;
- documento privado nao pode ser `PUBLICO`;
- referencias de midia sao texto logico, nao path real validado por filesystem;
- checksum e evidencia tecnica, nao leitura real de arquivo.

Metricas, Premium e gratuito:

- metricas de producao sao tratadas como existentes e preservaveis/migraveis;
- metricas podem ser reimplementadas desde que haja equivalencia funcional;
- Premium atual e regra existente e deve ser preservado;
- gratuito nao deve ter limite artificial de cliques, contatos ou WhatsApp.

SEO:

- textos SEO do painel/admin serao migrados por pacote de importacao futuro;
- URLs publicas exigem decisao documentada antes de sitemap/canonical final.

## Complemento da Fase 2D

A Fase 2D usa o dicionario estrutural como base para catalogar regras de saneamento e transformacao. Isso nao torna o dicionario definitivo e nao autoriza leitura de dump, abertura de arquivo real de entrada, acesso a banco ou transformacao real.

As regras definitivas dependerao da fonte real autorizada, com preservacao de pagamentos por evidencia, metricas existentes, Premium atual, gratuito sem limite artificial, documentos privados fora de midia publica e URLs/visual preservados.

## Complemento da Fase 2E

A Fase 2E usa o dicionario como dependencia conceitual do plano. O plano valida apenas ordem, dependencias e gates estruturais em memoria.

O dicionario definitivo ainda dependera da fonte real autorizada. Nenhuma etapa do plano autoriza leitura de dump, abertura de arquivo real, acesso a banco, transformacao ou importacao real.

## Fora do escopo

- leitura de dump;
- leitura de arquivo real de entrada;
- validacao por filesystem;
- acesso a banco;
- Flyway;
- Docker;
- SQL;
- migration;
- seed;
- ETL real;
- transformacao real;
- importacao real;
- API externa;
- storage real.
