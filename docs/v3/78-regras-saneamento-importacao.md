# Regras de saneamento da importacao

Status da entrega: `REGRAS ESTRUTURAIS EM MEMORIA, SEM TRANSFORMACAO REAL`.

## Objetivo

A Fase 2D define regras estruturais de saneamento e transformacao legado -> V3. Esta fase nao le dump, nao abre arquivo real de entrada, nao acessa banco, nao importa dados e nao executa ETL.

As regras definitivas dependem da fonte real autorizada. O catalogo atual e contratual e documental.

## Contratos Java

Pacote criado:

```text
backend/src/main/java/br/com/topsdojob/v3/importacao/saneamento/
```

Contratos criados:

- `TipoRegraSaneamentoImportacao`;
- `SeveridadeRegraSaneamentoImportacao`;
- `EscopoRegraSaneamentoImportacao`;
- `RegraSaneamentoImportacaoDto`;
- `ResultadoRegraSaneamentoImportacao`;
- `CatalogoRegrasSaneamentoImportacao`;
- `ValidadorRegrasSaneamentoImportacao`.

Todos sao Java puro, sem Spring, sem JPA, sem repository, sem controller, sem endpoint, sem acesso a banco, sem leitura de arquivo real e sem rede.

## Tipos de regra

Tipos definidos:

- `NORMALIZAR_TEXTO`;
- `NORMALIZAR_EMAIL`;
- `NORMALIZAR_TELEFONE_E164`;
- `NORMALIZAR_DATA`;
- `NORMALIZAR_DINHEIRO`;
- `NORMALIZAR_CREDITO_INTEIRO`;
- `MAPEAR_STATUS`;
- `MAPEAR_PROVEDOR_PAGAMENTO_POR_EVIDENCIA`;
- `VALIDAR_SLUG`;
- `PRESERVAR_SLUG_PUBLICO`;
- `DEDUPLICAR_REGISTRO`;
- `CLASSIFICAR_MIDIA`;
- `SEPARAR_DOCUMENTO_PRIVADO`;
- `VALIDAR_CHECKSUM_MIDIA`;
- `MAPEAR_LOCALIDADE`;
- `VALIDAR_HIERARQUIA_LOCALIDADE`;
- `PRESERVAR_METRICAS_EXISTENTES`;
- `PRESERVAR_PREMIUM_EXISTENTE`;
- `PRESERVAR_BENEFICIO_EXISTENTE`;
- `SINALIZAR_PENDENCIA_EVIDENCIA`;
- `BLOQUEAR_DADO_REAL_EM_EXEMPLO`;
- `SANITIZAR_TEXTO_PUBLICO`;
- `SANITIZAR_AUDITORIA`;
- `DECIDIR_URL_PUBLICA`;
- `GERAR_REDIRECT_SEGURO`;
- `MARCAR_NAO_IMPORTAVEL`.

## Escopos

Escopos definidos:

- `USUARIO`;
- `ANUNCIO`;
- `LOCALIDADE`;
- `MIDIA`;
- `DOCUMENTO`;
- `PREMIUM`;
- `CREDITO`;
- `PAGAMENTO`;
- `WEBHOOK`;
- `METRICA`;
- `SEO`;
- `URL`;
- `BANNER`;
- `COMERCIAL`;
- `PACOTE_IMPORTACAO`.

## Regras preservadas

- Pagamento legado sera classificado por evidencia.
- Tabela legada com nome Mercado Pago pode conter Efi.
- Efi nao deve ser convertido em Mercado Pago pelo nome da tabela.
- Mercado Pago legado nao deve ser convertido em Efi sem evidencia.
- Creditos usam inteiro/bigint conceitual, nunca float.
- Valores financeiros usam decimal/numeric conceitual, nunca float.
- Metricas existentes serao preservadas, migradas ou reimplementadas com equivalencia funcional.
- Premium atual sera preservado.
- Beneficios atuais serao preservados.
- Gratuito nao tera limite artificial de cliques, contatos ou WhatsApp.
- Documentos privados nao sao midia publica.
- Visual e URLs publicas devem ser preservados.

## Sem transformacao real

O catalogo nao executa transformacao, nao calcula dado derivado, nao grava arquivo e nao consulta origem. Ele apenas descreve regras futuras para revisao.

## Complemento da Fase 2E

A Fase 2E usa este catalogo como dependencia conceitual antes de preparar localidades, usuarios, anuncios, midias, documentos, pagamentos, creditos, metricas, SEO, banners e regras comerciais.

O plano de execucao nao transforma dados e nao aplica regras sobre origem real. Ele apenas garante que a ordem futura preserve pagamento por evidencia, metricas existentes, Premium atual, gratuito sem limite artificial, documentos privados fora de midia publica e visual/URLs preservados.
