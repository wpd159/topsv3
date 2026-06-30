# Contratos e relatórios de importação

## Status

Status da entrega: `CONTRATO ESTRUTURAL LOCAL, SEM IMPORTACAO REAL`.

Este documento define formatos conceituais para a futura importação saneadora. A Fase 2A não lê dump, não lê storage, não acessa banco e não grava relatório real.

## Relatório de importação

Formato lógico do resumo:

| Campo | Descrição |
| --- | --- |
| `totalItens` | Total de itens avaliados em memória. |
| `totalPendencias` | Total de pendências associadas aos itens. |
| `pendenciasPorSeveridade` | Mapa por `SeveridadePendenciaImportacao`. |
| `pendenciasPorCodigo` | Mapa por `CodigoPendenciaImportacao`. |
| `itensPorTipo` | Mapa por `TipoEntidadeImportacao`. |
| `contemPendenciaBloqueante` | Indica pendência que impede promoção. |

O relatório final futuro deve conter também hash/assinatura, reconciliação financeira, resumo executivo e arquivos exportáveis de pendências. Esses itens não são gerados na Fase 2A.

## Manifesto de mídia

Formato lógico de item:

| Campo | Descrição |
| --- | --- |
| `origemLegado` | Sistema/tabela/fonte de origem, sanitizada. |
| `idLegado` | Identificador legado do objeto. |
| `caminhoLegadoSanitizado` | Caminho lógico sanitizado, sem expor arquivo privado real. |
| `checksumSha256` | Checksum quando disponível em fase futura. |
| `bucketDestino` | Bucket conceitual destino, sem credencial real. |
| `chaveDestino` | Chave conceitual destino, sem upload real. |
| `status` | Status estrutural do item. |
| `pendencias` | Pendências como mídia ausente ou checksum divergente. |

Regras:

- placeholder não conta como mídia real;
- documento privado não entra em galeria pública;
- objeto sem manifesto gera `MIDIA_SEM_MANIFESTO`;
- checksum divergente gera `MIDIA_CHECKSUM_DIVERGENTE`;
- storage real fica para fase futura.

## Mapa legado -> V3

Formato lógico:

| Campo | Descrição |
| --- | --- |
| `tipoEntidade` | Tipo da entidade importável. |
| `origemLegado` | Origem obrigatória. |
| `idLegado` | Identificador legado obrigatório. |
| `idV3` | Identificador V3 opcional nesta fase. |
| `status` | Estado estrutural de importação. |
| `pendencias` | Pendências vinculadas à entidade. |

Regras:

- origem legada é obrigatória;
- não há promoção automática para tabelas finais;
- duplicidade suspeita não deve ser mesclada automaticamente;
- divergência financeira deve bloquear promoção futura.

## Mapa URL atual -> V3

Formato lógico:

| Campo | Descrição |
| --- | --- |
| `urlAtualPath` | Path atual obrigatório, iniciado por `/`. |
| `urlDestinoPath` | Path destino quando houver redirect/canonical. |
| `tipoEntidade` | Tipo vinculado à URL. |
| `idLegado` | Identificador legado opcional. |
| `decisao` | Decisão obrigatória. |
| `pendencias` | Pendências SEO ou de redirect. |

Decisões permitidas:

- `MANTER`;
- `REDIRECIONAR_301`;
- `NOINDEX`;
- `REMOVER`;
- `CANONICALIZAR`.

`SEM_DECISAO` existe como marcador de entrada bruta futura, mas é rejeitado pelo DTO estrutural da Fase 2A. URL sem decisão deve gerar `URL_SEM_DECISAO` ou `SEO_PENDENTE` em fase futura.

## Pagamentos e provedores

O provedor de pagamento deve ser classificado por evidência real:

- campo explícito confiável;
- `txid`;
- identificador de provedor;
- webhook;
- conciliação;
- datas e status coerentes;
- relação com crédito concedido.

O nome da tabela legada não define provedor. Uma tabela chamada Mercado Pago pode conter registros Efí, e um registro sem evidência suficiente deve gerar pendência. Mercado Pago legado não deve ser convertido para Efí por inferência fraca.

## SEO, métricas e Premium

- Textos SEO do painel/admin serão migrados em importador futuro.
- Métricas de produção serão preservadas, migradas ou reimplementadas com equivalência funcional.
- Premium atual deve ser preservado como benefício aditivo.
- Gratuito não terá limite artificial de cliques, contatos ou WhatsApp.

## Pacote de entrada

A Fase 2B adiciona contratos para o pacote de entrada futuro:

- descritor `PacoteEntradaImportacaoDto`;
- item `ArquivoPacoteImportacaoDto`;
- tipos `TipoArquivoPacoteImportacao`;
- status `StatusArquivoPacoteImportacao`;
- resultado `ResultadoValidacaoPacoteImportacao`;
- validador `ValidadorPacoteEntradaImportacao`.

Esses contratos são apenas estruturais. O pacote futuro deverá ser declarado e validado antes de qualquer leitura real, saneamento, reconciliação ou promoção de dados.

## Dicionário de campos

A Fase 2C adiciona contratos para descrever campos esperados no pacote futuro:

- `TipoCampoImportacao`;
- `ObrigatoriedadeCampoImportacao`;
- `SensibilidadeCampoImportacao`;
- `CampoPacoteImportacaoDto`;
- `DicionarioArquivoImportacaoDto`;
- `CatalogoDicionarioImportacao`;
- `ValidadorDicionarioImportacao`.

O dicionário classifica campos sensíveis, obrigatórios, saneáveis, bloqueantes e dependentes de evidência. Ele permanece estrutural e em memória.

## Regras de saneamento

A Fase 2D adiciona contratos para regras estruturais de saneamento e transformação:

- `TipoRegraSaneamentoImportacao`;
- `SeveridadeRegraSaneamentoImportacao`;
- `EscopoRegraSaneamentoImportacao`;
- `RegraSaneamentoImportacaoDto`;
- `ResultadoRegraSaneamentoImportacao`;
- `CatalogoRegrasSaneamentoImportacao`;
- `ValidadorRegrasSaneamentoImportacao`.

Esses contratos descrevem transformações futuras, mas não executam transformação real. O catálogo preserva pagamento por evidência, métricas existentes, Premium atual, gratuito sem limite artificial, documentos privados fora de mídia pública e URLs/visual preservados.

## Plano de execucao e dry-run estrutural

A Fase 2E adiciona contratos para plano e dry-run estrutural:

- `TipoEtapaImportacao`;
- `StatusEtapaImportacao`;
- `CriticidadeEtapaImportacao`;
- `DependenciaEtapaImportacaoDto`;
- `EtapaImportacaoDto`;
- `PlanoExecucaoImportacaoDto`;
- `ResultadoPlanoImportacaoDto`;
- `CatalogoPlanoExecucaoImportacao`;
- `ValidadorPlanoExecucaoImportacao`.

Esses contratos organizam ordem, dependencias e gates futuros. O dry-run desta fase e somente estrutural e em memoria: nao le arquivo real, nao abre dump, nao conecta banco, nao transforma dados e nao importa registros.

## Fora do escopo 2A/2B/2C/2D/2E

- leitura de dump;
- leitura de storage real;
- abertura de arquivo real de entrada;
- transformação real;
- conexão com banco;
- escrita de relatório real;
- ETL;
- importação;
- seed;
- migration;
- SQL;
- endpoint funcional;
- service de dominio;
- integração externa.
