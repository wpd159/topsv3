# Plano de execucao da importacao

Status da entrega: `PLANO ESTRUTURAL LOCAL, SEM IMPORTACAO REAL`.

## Objetivo

A Fase 2E define a ordem conceitual da futura importacao saneadora e os gates minimos de dry-run estrutural. Esta fase nao le dump, nao abre arquivo real de entrada, nao acessa banco, nao transforma dados, nao executa ETL e nao importa dados.

O plano atual e um contrato local em memoria. A execucao real dependera de fonte autorizada, revisao Pro do schema, aprovacao dos gates de seguranca e fase futura especifica.

## Contratos Java

Pacote criado:

```text
backend/src/main/java/br/com/topsdojob/v3/importacao/plano/
```

Contratos criados:

- `TipoEtapaImportacao`;
- `StatusEtapaImportacao`;
- `CriticidadeEtapaImportacao`;
- `DependenciaEtapaImportacaoDto`;
- `EtapaImportacaoDto`;
- `PlanoExecucaoImportacaoDto`;
- `ResultadoPlanoImportacaoDto`;
- `CatalogoPlanoExecucaoImportacao`;
- `ValidadorPlanoExecucaoImportacao`.

Todos sao Java puro, sem Spring, sem JPA, sem repository, sem controller, sem endpoint, sem acesso a banco, sem leitura de arquivo real, sem storage client, sem HTTP client e sem rede.

## Tipos de etapa

Tipos definidos no catalogo:

- `VALIDAR_PACOTE_ENTRADA`;
- `VALIDAR_DICIONARIO_CAMPOS`;
- `VALIDAR_REGRAS_SANEAMENTO`;
- `PREPARAR_LOCALIDADES`;
- `PREPARAR_USUARIOS`;
- `PREPARAR_ANUNCIOS`;
- `PREPARAR_MIDIAS`;
- `PREPARAR_DOCUMENTOS_PRIVADOS`;
- `PREPARAR_PREMIUM_BENEFICIOS`;
- `PREPARAR_PAGAMENTOS`;
- `PREPARAR_CREDITOS`;
- `PREPARAR_METRICAS`;
- `PREPARAR_SEO_URLS`;
- `PREPARAR_BANNERS`;
- `PREPARAR_COMERCIAL_SUPORTE`;
- `GERAR_RELATORIO_DRY_RUN`;
- `BLOQUEAR_IMPORTACAO_COM_PENDENCIA_CRITICA`.

## Ordem segura

Ordem padrao declarada:

1. validar pacote de entrada;
2. validar dicionario de campos;
3. validar regras de saneamento;
4. preparar localidades;
5. preparar usuarios;
6. preparar anuncios;
7. preparar midias;
8. preparar documentos privados;
9. preparar Premium e beneficios;
10. preparar pagamentos;
11. preparar creditos;
12. preparar metricas;
13. preparar SEO e URLs;
14. preparar banners;
15. preparar comercial e suporte;
16. gerar relatorio de dry-run;
17. bloquear importacao real com pendencia critica.

Essa ordem protege dependencias naturais: anuncio depende de usuario e localidade; midia depende de anuncio; creditos dependem de pagamento ou evidencia equivalente auditavel; SEO/URLs dependem de anuncio e localidade.

## Dependencias criticas

O catalogo registra dependencias obrigatorias:

| Etapa | Dependencia |
| --- | --- |
| `PREPARAR_ANUNCIOS` | `PREPARAR_USUARIOS` e `PREPARAR_LOCALIDADES` |
| `PREPARAR_MIDIAS` | `PREPARAR_ANUNCIOS` |
| `PREPARAR_DOCUMENTOS_PRIVADOS` | `PREPARAR_USUARIOS` e `PREPARAR_ANUNCIOS` |
| `PREPARAR_PREMIUM_BENEFICIOS` | `PREPARAR_ANUNCIOS` |
| `PREPARAR_PAGAMENTOS` | `PREPARAR_USUARIOS` e `VALIDAR_REGRAS_SANEAMENTO` |
| `PREPARAR_CREDITOS` | `PREPARAR_PAGAMENTOS` ou alternativa auditavel |
| `PREPARAR_METRICAS` | `PREPARAR_ANUNCIOS` |
| `PREPARAR_SEO_URLS` | `PREPARAR_ANUNCIOS` e `PREPARAR_LOCALIDADES` |
| `PREPARAR_BANNERS` | `PREPARAR_MIDIAS` |
| `GERAR_RELATORIO_DRY_RUN` | decisoes de SEO/URLs e comercial/suporte |
| `BLOQUEAR_IMPORTACAO_COM_PENDENCIA_CRITICA` | relatorio de dry-run |

## Regras preservadas

- Pagamentos devem ser classificados por evidencia; o nome da tabela legada nao define provedor.
- Tabela legada com nome Mercado Pago pode conter Efi.
- Mercado Pago legado nao deve ser convertido para Efi sem evidencia.
- Efi nao deve ser convertido para Mercado Pago pelo nome da tabela.
- Creditos usam inteiro/bigint conceitual e dependem de pagamento ou evidencia equivalente auditavel.
- Metricas existentes devem ser preservadas, migradas ou reimplementadas com equivalencia funcional.
- Premium atual deve ser preservado.
- Gratuito nao deve receber limite artificial de cliques, contatos ou WhatsApp.
- Documento privado nunca e midia publica e nunca e publicavel.
- Visual atual, banners e URLs publicas devem ser preservados.

## Gates

Antes de qualquer importacao real futura:

- schema deve sair de `AGUARDANDO_REVISAO_PRO`;
- fonte real deve ser autorizada;
- pacote de entrada deve ser declarado;
- dicionario deve ser validado contra fonte autorizada;
- regras de saneamento devem ser revisadas;
- dry-run estrutural deve gerar relatorio;
- pendencias bloqueantes devem impedir promocao;
- divergencia financeira deve bloquear go-live;
- revisao humana deve aprovar decisoes de SEO, documentos privados e pagamentos.

## Fora do escopo

- dump real;
- dado real;
- abertura de arquivo real de entrada;
- banco local persistente;
- banco de producao;
- leitura/escrita em storage;
- migration;
- SQL;
- Flyway;
- Docker;
- ETL real;
- transformacao real;
- importacao real;
- endpoint funcional;
- API externa;
- commit, remote ou push.
