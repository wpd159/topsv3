# Pacote de entrada da importacao

Status da entrega: `CONTRATO ESTRUTURAL LOCAL, SEM DUMP E SEM BANCO`.

## Objetivo

A Fase 2B define o descritor conceitual do pacote de entrada da futura importacao saneadora. Esta fase nao le dump, nao abre storage legado, nao acessa banco, nao importa dados e nao executa ETL real.

O pacote de entrada futuro sera validado antes de qualquer importacao real. A validacao criada agora e apenas estrutural e opera sobre DTOs em memoria.

## Contratos Java

Pacote criado:

```text
backend/src/main/java/br/com/topsdojob/v3/importacao/pacote/
```

Contratos criados:

- `TipoArquivoPacoteImportacao`;
- `StatusArquivoPacoteImportacao`;
- `ArquivoPacoteImportacaoDto`;
- `PacoteEntradaImportacaoDto`;
- `ResultadoValidacaoPacoteImportacao`;
- `ValidadorPacoteEntradaImportacao`.

Todos sao Java puro, sem JPA, sem Spring, sem repository, sem controller, sem endpoint, sem acesso a banco, sem leitura de arquivo real e sem rede.

## Tipos de arquivo esperados

Tipos minimos definidos para o pacote futuro:

- `DUMP_BANCO_LEGADO`;
- `MANIFESTO_MIDIA`;
- `EXPORT_URLS_PUBLICAS`;
- `EXPORT_SLUGS`;
- `EXPORT_METRICAS`;
- `EXPORT_PAGAMENTOS`;
- `EXPORT_CREDITOS`;
- `EXPORT_PREMIUM`;
- `EXPORT_BENEFICIOS`;
- `EXPORT_BANNERS`;
- `EXPORT_SEO_CONTEUDOS`;
- `EXPORT_USUARIOS`;
- `EXPORT_ANUNCIOS`;
- `EXPORT_LOCALIDADES`;
- `RELATORIO_ORIGEM`;
- `CHECKSUMS`.

Na Fase 2B, esses tipos sao somente declaracoes contratuais. Nenhum arquivo fisico e aberto, lido, baixado, validado por filesystem ou interpretado.

## Descritor do pacote

`PacoteEntradaImportacaoDto` representa:

| Campo | Regra |
| --- | --- |
| `identificadorLogico` | Obrigatorio para rastreabilidade. |
| `versao` | Obrigatoria para versionar o formato do pacote. |
| `extraidoEm` | Data/hora declarada da extracao futura. |
| `origem` | Origem logica e sanitizada do pacote. |
| `arquivos` | Lista declarativa de arquivos esperados. |
| `observacaoSanitizada` | Campo textual opcional sem dado real. |

O DTO nao valida existencia fisica de caminhos e nao abre arquivos.

## Arquivo declarado

`ArquivoPacoteImportacaoDto` representa uma linha declarativa do pacote:

| Campo | Regra |
| --- | --- |
| `tipo` | Tipo esperado pelo contrato. |
| `nomeLogico` | Nome logico sanitizado; se ausente, usa o padrao do tipo. |
| `status` | Estado declarativo do arquivo. |
| `caminhoDeclaradoSanitizado` | Texto logico; nao e path real validado por filesystem. |
| `formatoDeclarado` | Formato conceitual futuro. |
| `tamanhoBytesDeclarado` | Tamanho declarado, opcional e nao negativo. |
| `checksumSha256` | Checksum declarado quando disponivel. |
| `versaoContrato` | Versao do contrato aplicado ao arquivo. |

Checksums presentes devem ter 64 caracteres hexadecimais. Checksums ausentes geram alerta, exceto nos tipos que exigem checksum.

## Exemplo sanitizado

Exemplo criado:

```text
docs/v3/exemplos/importacao/pacote-entrada-exemplo-sanitizado.json
```

O arquivo e ficticio e nao contem CPF, telefone, e-mail, URL real de producao, path real de maquina, bucket real, chave real ou dado de anuncio.

## Complemento da Fase 2C

A Fase 2C complementa o pacote de entrada com dicionário estrutural de campos por tipo de arquivo. O dicionário não altera a regra da Fase 2B: nenhum arquivo físico é aberto, lido, baixado, validado por filesystem ou interpretado.

O dicionário definitivo dependerá da fonte real autorizada. A política de obrigatoriedade/checksum do pacote 2B será revisada quando houver fonte real aprovada.

## Complemento da Fase 2D

A Fase 2D adiciona regras estruturais de saneamento e transformação legado -> V3. O pacote de entrada continua sem leitura real: nenhum arquivo físico é aberto, lido, baixado, validado por filesystem ou interpretado.

As regras de saneamento definitivas dependerão da fonte real autorizada e da validação do pacote/dicionário antes de qualquer transformação real.

## Complemento da Fase 2E

A Fase 2E usa o pacote de entrada apenas como dependencia conceitual do plano de execucao. O plano nao abre arquivo fisico, nao valida filesystem, nao le dump e nao executa importacao.

O pacote futuro devera ser validado antes do dry-run real de dados e antes de qualquer transformacao/importacao.

## Complemento da Fase 2F

A Fase 2F define o gate para recebimento futuro do pacote real. Ela nao recebe pacote real, nao abre arquivo real e nao valida conteudo.

Antes de qualquer dry-run real, o pacote devera ficar fora do repositorio, conter manifesto, checksums, origem declarada, data/hora de extracao, responsavel pela geracao e autorizacao expressa.

## Regras preservadas

- Tabela legada com nome Mercado Pago pode conter Efi.
- Provedor de pagamento sera classificado por evidencia real, nao por nome de tabela.
- Metricas existentes devem ser preservadas, migradas ou reimplementadas com equivalencia funcional.
- Premium atual deve ser preservado.
- Gratuito nao tera limite artificial de cliques, contatos ou WhatsApp.
- Textos SEO do painel/admin serao migrados por pacote de importacao futuro.

## Fora do escopo

- dump real;
- dado real;
- leitura de arquivo real de entrada;
- storage real;
- banco local persistente;
- banco de producao;
- migration;
- SQL;
- seed;
- Flyway;
- Docker;
- ETL real;
- importacao real;
- endpoint funcional;
- API externa.
