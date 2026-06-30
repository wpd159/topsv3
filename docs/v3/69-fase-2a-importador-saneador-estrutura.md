# Fase 2A - estrutura local do importador saneador

## Status

Status da entrega: `OK PARA ESTRUTURA LOCAL, SEM IMPORTACAO REAL`.

A Fase 2A cria somente a estrutura local do módulo de importação saneadora. Nenhum dump foi lido, nenhum dado real foi usado, nenhum banco foi acessado e nenhuma importação foi iniciada.

## Escopo criado

Pacote backend criado:

```text
backend/src/main/java/br/com/topsdojob/v3/importacao/
```

Subpacotes criados:

```text
model/
relatorio/
validacao/
manifesto/
mapeamento/
```

Classes, records e enums criados:

- `CodigoPendenciaImportacao`;
- `SeveridadePendenciaImportacao`;
- `StatusImportacaoItem`;
- `TipoEntidadeImportacao`;
- `DecisaoUrlImportacao`;
- `ResultadoValidacaoImportacao`;
- `PendenciaImportacaoDto`;
- `MapeamentoLegadoV3Dto`;
- `ManifestoMidiaItemDto`;
- `RelatorioImportacaoResumoDto`;
- `LinhaMapaUrlDto`;
- `ImportacaoRelatorioBuilder`;
- `ImportacaoSaneadorEstrutural`.

Todos são Java puro, sem JPA, sem repository, sem controller, sem endpoint, sem acesso a banco, sem leitura de arquivo real e sem rede.

## Códigos de pendência

O enum `CodigoPendenciaImportacao` contém os códigos mínimos da fase:

- `IMPORTADO_OK`;
- `USUARIO_SEM_TELEFONE`;
- `USUARIO_DUPLICADO_SUSPEITO`;
- `ANUNCIO_SEM_FOTO`;
- `ANUNCIO_COM_MIDIA_QUEBRADA`;
- `ANUNCIO_SEM_PRECO`;
- `ANUNCIO_SEM_CIDADE`;
- `SLUG_DUPLICADO`;
- `PREMIUM_INCONSISTENTE`;
- `CREDITO_INCONSISTENTE`;
- `PAGAMENTO_SEM_PROVEDOR`;
- `PAGAMENTO_SEM_TXID`;
- `PAGAMENTO_DUPLICADO`;
- `PAGAMENTO_APROVADO_SEM_CREDITO`;
- `CREDITO_SEM_PAGAMENTO`;
- `PAGAMENTO_COM_CREDITO_DUPLICADO`;
- `STATUS_PAGAMENTO_INCONSISTENTE`;
- `EVENTO_WEBHOOK_DUPLICADO`;
- `PAGAMENTO_EFI_NAO_CONFIRMADO`;
- `PAGAMENTO_MERCADO_PAGO_LEGADO`;
- `SEO_PENDENTE`;
- `MIDIA_SEM_MANIFESTO`;
- `MIDIA_CHECKSUM_DIVERGENTE`;
- `URL_SEM_DECISAO`.

Cada código possui severidade padrão para relatório, incluindo severidades informativas, alertas, erros e bloqueios financeiros.

## Contratos estruturais

### Mapeamento legado -> V3

`MapeamentoLegadoV3Dto` representa vínculo estrutural entre uma entidade de origem e uma entidade V3. O record exige:

- tipo de entidade;
- origem legada;
- identificador legado;
- status de importação;
- lista imutável de pendências.

Entidade sem origem legada é rejeitada em memória.

### Manifesto de mídia

`ManifestoMidiaItemDto` representa uma linha conceitual de manifesto, com origem, identificador legado, caminho sanitizado, checksum opcional, destino conceitual e pendências.

Esta fase não lê storage, não calcula checksum real e não acessa bucket real.

### Mapa URL atual -> V3

`LinhaMapaUrlDto` representa decisão estrutural para uma URL atual. A decisão é obrigatória e `SEM_DECISAO` é rejeitado em memória para impedir URL sem encaminhamento formal.

### Relatório

`ImportacaoRelatorioBuilder` monta resumo em memória:

- total de itens;
- total de pendências;
- pendências por severidade;
- pendências por código;
- itens por tipo;
- indicação de pendência bloqueante.

Nenhum relatório é gravado em disco nesta fase.

## Regras preservadas

- Importador futuro deve classificar registros por evidência, não por nome de tabela.
- Tabela legada com nome Mercado Pago pode conter Efí; provedor deve ser classificado por evidência real.
- Textos SEO do painel/admin serão migrados por importador futuro.
- Métricas de produção serão preservadas, migradas ou reimplementadas com equivalência funcional.
- Premium atual deve ser preservado.
- Usuário gratuito não terá limite artificial de cliques, contatos ou WhatsApp.

## Testes

Foi criado `backend/src/test/java/br/com/topsdojob/v3/importacao/ImportacaoEstruturaTest.java` cobrindo:

- presença dos códigos obrigatórios;
- soma de pendências por severidade no builder;
- rejeição de mapeamento sem origem;
- rejeição de URL sem decisão.

Os testes não foram executados nesta fase porque não há Maven/Maven Wrapper local disponível sem download. A execução fica pendente para quando o executor local estiver disponível sem baixar dependências.

## Complemento da Fase 2B

A Fase 2B complementa a estrutura local com contratos do pacote de entrada da importação em `br.com.topsdojob.v3.importacao.pacote`. Esse complemento permanece contratual e estrutural: não lê dump, não abre arquivo real de entrada, não acessa banco, não executa ETL e não inicia importação real.

O pacote de entrada futuro deve ser validado antes de qualquer importação real. Textos SEO do painel/admin, métricas, Premium, gratuidade e classificação de pagamentos continuam dependentes de evidência real e revisão futura.

## Bloqueios

- Não há importação real.
- Não há leitura de dump.
- Não há dado real.
- Não há conexão com banco.
- Não há migration nova.
- Não há SQL alterado.
- Não há entidade JPA.
- Não há repository.
- Não há controller de domínio.
- Não há endpoint funcional.
- Não há integração com storage real.
- Não há API externa, produção, VPS, Efí real ou OpenAI.
