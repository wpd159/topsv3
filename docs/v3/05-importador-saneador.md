# Importador saneador

## Objetivo

Migrar dados do legado para a V3 sem copiar ambiguidade, corrupção, dívida técnica ou regras concorrentes. O importador deve ser repetível, idempotente, auditável e conservador.

Nada deve ser corrigido silenciosamente.

## Pipeline

```text
EXTRAIR -> NORMALIZAR -> VALIDAR -> CARREGAR STAGING -> IMPORTAR V3 -> RECONCILIAR -> APROVAR
```

## Princípios

- Importação nunca roda no startup normal da aplicação.
- Cada execução gera `importacao_execucao`.
- Cada entidade importada gera mapeamento legado -> V3.
- Reexecução não duplica dados.
- Campos ambíguos geram pendência.
- Financeiro e créditos exigem tolerância zero: qualquer divergência financeira bloqueia promoção e go-live.
- Mídia exige manifesto e verificação.
- SEO exige mapa URL atual x URL V3.
- Relatório deve separar importados, rejeitados, pendentes e inconsistentes.

## Entradas obrigatórias

Antes de importar:

- dump PostgreSQL atual ou fonte oficial equivalente;
- inventário de storage atual, incluindo R2/Supabase quando aplicável;
- lista de URLs públicas atuais;
- export de slugs;
- configurações operacionais relevantes;
- catálogo de benefícios premium;
- saldos e históricos de créditos;
- pagamentos e transações;
- anúncios, usuários, localidades e mídias.

## Identidade de importação

Toda entidade deve carregar:

- `sistema_origem`;
- `tabela_origem`;
- `id_origem`;
- `importacao_execucao_id`;
- hash dos campos relevantes;
- data de extracao;
- status de importação;
- pendências associadas.

## Staging

O staging deve receber dados brutos normalizados, sem misturar com tabelas finais.

Tabelas sugeridas:

- `stg_usuario`
- `stg_anuncio`
- `stg_localidade`
- `stg_midia`
- `stg_story`
- `stg_pagamento`
- `stg_credito`
- `stg_premium`
- `stg_url`

## Códigos obrigatórios do relatório

### `IMPORTADO_OK`

Entidade importada com sucesso, sem pendência bloqueante.

### `USUARIO_SEM_TELEFONE`

Usuário sem telefone normalizavel. Não preencher com valor falso. Classificar impacto conforme a regra comercial do usuário/anúncio.

### `USUARIO_DUPLICADO_SUSPEITO`

Usuários com e-mail, telefone, documento, nome ou outro identificador indicando possível duplicidade. Não mesclar automaticamente.

### `ANUNCIO_SEM_FOTO`

Anúncio sem foto real válida. Placeholder não conta como foto.

### `ANUNCIO_COM_MIDIA_QUEBRADA`

Mídia referenciada não encontrada, inacessível, com tamanho divergente, MIME inválido ou checksum/ETag divergente quando disponível.

### `ANUNCIO_SEM_PRECO`

Anúncio sem preço quando a regra de negócio exigir preço para publicação, busca, premium ou exibição.

### `ANUNCIO_SEM_CIDADE`

Anúncio sem cidade válida ou sem mapeamento para cadastro V3 de localidade.

### `SLUG_DUPLICADO`

Slug conflita com outro anúncio ou URL pública. Exige decisão de preservação e redirect.

### `PREMIUM_INCONSISTENTE`

Ativação premium sem benefício válido, período incoerente, anúncio ausente, usuário ausente, custo divergente ou status ambíguo.

### `CREDITO_INCONSISTENTE`

Saldo, histórico, pagamento ou movimento não reconciliam. Não importar como saldo final sem explicação.

### `PAGAMENTO_SEM_PROVEDOR`

Registro de pagamento sem provedor identificável por campo explícito ou evidência confiável.

### `PAGAMENTO_SEM_TXID`

Pagamento Pix sem `txid` confiável. Não inferir `txid` quando houver risco de colisão.

### `PAGAMENTO_DUPLICADO`

Pagamento com mesmo `txid`, identificador de provedor, idempotency key ou evidência transacional equivalente.

### `PAGAMENTO_APROVADO_SEM_CREDITO`

Pagamento aprovado sem entrada correspondente na razão de créditos.

### `CREDITO_SEM_PAGAMENTO`

Entrada de crédito que declara origem financeira, mas não aponta para pagamento aprovado.

### `PAGAMENTO_COM_CREDITO_DUPLICADO`

Pagamento aprovado associado a mais de uma entrada de crédito.

### `STATUS_PAGAMENTO_INCONSISTENTE`

Status local, status do provedor, datas e movimentos financeiros entram em conflito.

### `EVENTO_WEBHOOK_DUPLICADO`

Evento de webhook repetido ou com hash/identificador já processado.

### `PAGAMENTO_EFI_NAO_CONFIRMADO`

Registro classificado como Efí Pix que não pode ser confirmado por evidência suficiente ou consulta homologada/produtiva autorizada.

### `PAGAMENTO_MERCADO_PAGO_LEGADO`

Pagamento classificado como Mercado Pago legado. Não converter para Efí.

### `SEO_PENDENTE`

URL sem decisão final de manter, redirecionar, noindex ou remover justificado.

### `MIDIA_SEM_MANIFESTO`

Mídia sem linha correspondente no manifesto conceitual. Não importar objeto sem rastreabilidade de origem, destino e validação.

### `MIDIA_CHECKSUM_DIVERGENTE`

Mídia com checksum, ETag, tamanho ou evidência equivalente divergente. Não promover arquivo sem decisão formal.

### `URL_SEM_DECISAO`

URL atual sem decisão de manter, redirecionar, noindex, remover ou canonicalizar. URL prioritária sem decisão não deve entrar no sitemap.

### Pendências de pacote de entrada

Códigos estruturais adicionados na Fase 2B:

- `PACOTE_DESCRITOR_AUSENTE`: descritor do pacote ausente;
- `PACOTE_SEM_IDENTIFICADOR`: pacote sem identificador lógico;
- `PACOTE_SEM_VERSAO`: pacote sem versão declarada;
- `PACOTE_SEM_EXTRACAO_DECLARADA`: pacote sem data/hora declarada da extração;
- `PACOTE_SEM_ORIGEM`: pacote sem origem declarada;
- `PACOTE_SEM_ARQUIVOS`: pacote sem lista de arquivos;
- `PACOTE_ARQUIVO_DUPLICADO`: tipo de arquivo repetido no descritor;
- `PACOTE_ARQUIVO_OBRIGATORIO_AUSENTE`: arquivo obrigatório ausente ou marcado como ausente;
- `PACOTE_CHECKSUM_AUSENTE`: checksum ausente em tipo que permite alerta;
- `PACOTE_CHECKSUM_OBRIGATORIO_AUSENTE`: checksum ausente em tipo que exige checksum.

Esses códigos são apenas para validação estrutural em memória. Não criam tabela, migration, SQL, entidade JPA, repository ou endpoint.

### Pendências de dicionário de campos

Códigos estruturais adicionados na Fase 2C:

- `DICIONARIO_TIPO_ARQUIVO_AUSENTE`: tipo de arquivo sem dicionário estrutural;
- `DICIONARIO_ARQUIVO_SEM_CAMPOS`: dicionário de arquivo sem campos;
- `DICIONARIO_CAMPO_OBRIGATORIO_SEM_NOME`: campo obrigatório sem nome lógico;
- `DICIONARIO_CAMPO_SEM_TIPO`: campo sem tipo estrutural;
- `DICIONARIO_CAMPO_SEM_OBRIGATORIEDADE`: campo sem obrigatoriedade;
- `DICIONARIO_CAMPO_SENSIVEL_SEM_CLASSIFICACAO`: campo sensível sem classificação;
- `DICIONARIO_CAMPO_FINANCEIRO_TIPO_INCOMPATIVEL`: campo financeiro com tipo incompatível;
- `DICIONARIO_CAMPO_CREDITO_TIPO_INCOMPATIVEL`: campo de crédito com tipo incompatível;
- `DICIONARIO_DOCUMENTO_PUBLICO`: documento privado classificado como público.

Esses códigos são documentais e em memória. Não criam schema, tabela, migration, SQL, entidade JPA, repository, controller ou endpoint.

### Pendências de regras de saneamento

Códigos estruturais adicionados na Fase 2D:

- `SANEAMENTO_REGRA_SEM_CODIGO`: regra sem código lógico;
- `SANEAMENTO_REGRA_SEM_ESCOPO`: regra sem escopo;
- `SANEAMENTO_BLOQUEANTE_SEM_DESCRICAO`: regra bloqueante sem descrição;
- `SANEAMENTO_PAGAMENTO_SEM_EVIDENCIA`: regra de pagamento sem menção à evidência;
- `SANEAMENTO_DOCUMENTO_PRIVADO_PUBLICO`: documento privado marcado como público;
- `SANEAMENTO_CREDITO_TIPO_INCOMPATIVEL`: regra de crédito com tipo incompatível;
- `SANEAMENTO_ESCOPO_PRINCIPAL_SEM_REGRA`: escopo principal sem regra no catálogo.

Esses códigos validam apenas o catálogo em memória. Não criam transformação real, schema, tabela, migration, SQL, entidade JPA, repository, controller ou endpoint.

### Pendencias de plano de execucao

Codigos estruturais adicionados na Fase 2E:

- `PLANO_ETAPA_SEM_TIPO`: etapa sem tipo logico;
- `PLANO_ETAPA_SEM_ORDEM`: etapa sem ordem positiva;
- `PLANO_ETAPA_CRITICA_SEM_DESCRICAO`: etapa critica sem descricao;
- `PLANO_DEPENDENCIA_INEXISTENTE`: dependencia declarada ausente no plano;
- `PLANO_ANUNCIO_SEM_DEPENDENCIA_USUARIO_LOCALIDADE`: anuncio sem dependencia de usuario e localidade;
- `PLANO_MIDIA_SEM_DEPENDENCIA_ANUNCIO`: midia sem dependencia de anuncio;
- `PLANO_CREDITO_SEM_DEPENDENCIA_PAGAMENTO`: credito sem dependencia de pagamento ou alternativa auditavel;
- `PLANO_SEO_URL_SEM_DEPENDENCIA_ANUNCIO_LOCALIDADE`: SEO/URL sem dependencia de anuncio e localidade;
- `PLANO_SEM_RELATORIO_DRY_RUN`: plano sem etapa de relatorio dry-run;
- `PLANO_SEM_BLOQUEIO_PENDENCIA_CRITICA`: plano sem gate de bloqueio por pendencia critica.

Esses codigos validam apenas o plano em memoria. Nao criam importacao real, transformacao real, schema, tabela, migration, SQL, entidade JPA, repository, service, controller ou endpoint.

## Fase 2A - estrutura local

A Fase 2A cria apenas estrutura local do importador saneador:

- pacote Java `br.com.topsdojob.v3.importacao`;
- enums de pendência;
- DTOs/records de mapeamento, manifesto, URL e relatório;
- builder de relatório em memória;
- skeleton estrutural sem `@Service`, sem JPA e sem banco;
- testes unitários estruturais pendentes de executor local sem download.

Não há leitura de dump, acesso a storage real, importação, ETL, banco, migration, SQL novo, seed ou dado real nesta fase.

O importador futuro deve classificar registros por evidência real. Tabela legada com nome Mercado Pago pode conter Efí; provedor de pagamento não deve ser inferido apenas pelo nome da tabela. Textos SEO do painel/admin, métricas de produção, Premium atual e regras do gratuito ficam preservados para migração/reimplementação futura com equivalência funcional.

## Fase 2B - pacote de entrada

A Fase 2B cria contratos locais para o pacote de entrada da futura importação:

- pacote Java `br.com.topsdojob.v3.importacao.pacote`;
- enum de tipos de arquivo esperados;
- enum de status de arquivo declarado;
- DTO de arquivo declarado;
- DTO de pacote de entrada;
- resultado de validação do pacote;
- validador estrutural em memória;
- exemplo sanitizado em `docs/v3/exemplos/importacao/pacote-entrada-exemplo-sanitizado.json`.

Não há leitura de dump, abertura de arquivo real de entrada, acesso a storage real, importação, ETL, banco, migration, SQL novo, seed, dado real ou endpoint nesta fase.

O pacote futuro deve ser validado antes de qualquer importação real. Tabela legada com nome Mercado Pago pode conter Efí; provedor de pagamento será classificado por evidência real. Textos SEO do painel/admin serão migrados por pacote futuro. Métricas existentes devem ser preservadas, migradas ou reimplementadas com equivalência funcional. Premium atual deve ser preservado e gratuito não terá limite artificial de cliques, contatos ou WhatsApp.

## Fase 2C - dicionário estrutural de campos

A Fase 2C cria contratos locais para descrever campos esperados nos arquivos do pacote de importação:

- pacote Java `br.com.topsdojob.v3.importacao.dicionario`;
- tipos de campo;
- classificações de sensibilidade;
- classificações de obrigatoriedade;
- DTO de campo;
- DTO de dicionário por arquivo;
- catálogo estrutural por tipo de arquivo;
- validador estrutural em memória.

Não há leitura de dump, abertura de arquivo real de entrada, acesso a storage real, importação, ETL, banco, migration, SQL novo, seed, dado real ou endpoint nesta fase.

O dicionário definitivo dependerá da fonte real autorizada. A política de obrigatoriedade/checksum do pacote 2B será revisada quando houver fonte real aprovada. Pagamentos Efí/Mercado Pago legado serão classificados por evidência, métricas existentes serão preservadas/migradas/reimplementadas com equivalência funcional, Premium atual será preservado, gratuito não terá limite artificial de cliques/contatos/WhatsApp e documentos privados não serão mídia pública.

## Fase 2D - regras de saneamento e transformação

A Fase 2D cria contratos locais para regras estruturais de saneamento legado -> V3:

- pacote Java `br.com.topsdojob.v3.importacao.saneamento`;
- tipos de regra de saneamento;
- severidades de regra;
- escopos por entidade/processo;
- DTO de regra;
- resultado estrutural de regra;
- catálogo de regras por escopo;
- validador estrutural em memória.

Não há leitura de dump, abertura de arquivo real de entrada, acesso a storage real, transformação, importação, ETL, banco, migration, SQL novo, seed, dado real ou endpoint nesta fase.

Regras definitivas dependerão da fonte real autorizada. Pagamento legado será classificado por evidência, métricas existentes serão preservadas/migradas/reimplementadas com equivalência funcional, Premium atual será preservado, gratuito não terá limite artificial de cliques/contatos/WhatsApp, documentos privados não serão mídia pública e visual/URLs públicas devem ser preservados.

## Fase 2E - plano e dry-run estrutural

A Fase 2E cria contratos locais para plano de execucao e dry-run estrutural da futura importacao:

- pacote Java `br.com.topsdojob.v3.importacao.plano`;
- tipos de etapa;
- status de etapa;
- criticidade de etapa;
- DTO de dependencia;
- DTO de etapa;
- DTO de plano;
- resultado de validacao do plano;
- catalogo de ordem segura;
- validador estrutural em memoria.

Nao ha leitura de dump, abertura de arquivo real de entrada, acesso a storage real, transformacao, importacao, ETL, banco, migration, SQL novo, seed, dado real, service de dominio ou endpoint nesta fase.

O dry-run desta fase e estrutural: valida apenas etapas, dependencias e gates. A importacao real futura continua bloqueada ate aprovacao Pro do schema, fonte real autorizada, relatorio de dry-run real e ausencia de pendencia critica.

## Fase 2F - gate de fonte real autorizada

A Fase 2F cria o gate operacional antes de qualquer uso de fonte real:

- documentacao do gate de fonte real autorizada;
- runbook de recebimento de pacote real futuro;
- checklist de protecao contra vazamento;
- reforco de `.gitignore` para artefatos reais de importacao;
- script local `validar-fonte-importacao-local.ps1`;
- exemplo sanitizado de instrucao de recebimento.

Nao ha leitura de dump, abertura de arquivo real de entrada, acesso a storage real, processamento de pagamento/metrica/midia, importacao, ETL, banco, migration, SQL novo, seed, dado real, service de dominio ou endpoint nesta fase.

O pacote real futuro deve ficar fora do repositorio, com manifesto, checksums, origem declarada, data/hora de extracao e responsavel pela geracao. A importacao real continua bloqueada ate autorizacao expressa, revisao Pro e aprovacao especifica.

## Fase 2G - dossie de transicao

A Fase 2G consolida o estado da Fase 1D e das Fases 2A a 2F em um dossie de transicao:

- estado das migrations e validacoes;
- estado dos contratos do importador saneador;
- gates antes de fonte real e importacao;
- proximas fases classificadas por dependencia;
- riscos residuais;
- proibicoes mantidas.

Nao ha nova camada de importador, codigo Java, script, leitura de dump, abertura de arquivo real de entrada, acesso a banco, ETL, importacao, migration ou SQL nesta fase.

## Relatório de execução

Cada execução deve produzir:

- totais extraídos por entidade;
- totais importados;
- totais pendentes por código;
- totais rejeitados;
- diferenças entre legado e V3;
- lista de slugs duplicados;
- lista de anúncios sem mídia, preço ou cidade;
- lista de mídias quebradas;
- reconciliação de créditos;
- reconciliação de pagamentos;
- reconciliação de premium;
- mapa de URLs;
- resumo de riscos;
- hash/assinatura do relatório.

## Usuários

Regras:

- Normalizar e-mail e telefone.
- Não importar senha em claro.
- Hash legado deve ser tratado como dado sensível.
- Usuário sem telefone gera pendência.
- Duplicidade suspeita não deve ser mesclada automaticamente.
- Contas administrativas legadas exigem revisão manual.

## Anúncios

Regras:

- Preservar slug sempre que possível.
- Validar status.
- Validar cidade/bairro.
- Validar preço conforme regra.
- Validar ao menos uma mídia real quando anúncio for ativo/publicável.
- Separar dados públicos de dados privados.

## Mídia

Regras:

- Gerar manifesto por objeto.
- Validar existencia no storage.
- Validar tamanho.
- Validar MIME real.
- Validar checksum/ETag quando disponível.
- Não transformar placeholder em mídia.
- Não misturar documento privado com galeria pública.

## Stories

Regras:

- Story importado precisa de `anuncio_midia` canônico.
- O `anuncio_midia` vinculado ao story deve apontar para anúncio e arquivo válidos.
- `anuncio_midia.tipo` deve ser `STORY`.
- `anuncio_midia.finalidade` deve ser `STORY`.
- `story_anuncio.anuncio_midia_id` deve ser único.
- Story expirado pode ser importado como histórico, mas não deve aparecer publicamente.
- Story sem vínculo canônico não deve entrar como entidade final sem pendência.

## Premium

Regras:

- Mapear benefício legado para catálogo V3.
- Registrar origem da ativação como `IMPORTACAO` quando a origem real não estiver disponível.
- Preservar início/fim quando confiáveis.
- Marcar inconsistência quando houver período impossível, anúncio ausente, usuário ausente ou benefício desconhecido.

## Créditos e pagamentos

Regras:

- Construir razão de créditos a partir do histórico disponível.
- Quando não for possível reconstruir todos os movimentos, criar movimento de abertura auditado e documentado.
- Pagamento aprovado deve reconciliar com entrada de crédito.
- Divergência financeira bloqueia cutover, promoção e go-live.
- Ajuste manual exige justificativa e aprovação.
- Importar e reconciliar pagamentos Efí com `txid`, identificador do provedor, valor, plano, créditos, status, data de criação, data de aprovação, confirmação por webhook, indicação de crédito concedido e vínculo com movimento de crédito.
- A tabela legada `pagamentos_mp` pode conter pagamentos de mais de um provedor; o nome da tabela não define Mercado Pago automaticamente.
- Registros Efí vindos de tabela legada com nome de Mercado Pago devem ser importados como `EFI` quando houver evidência suficiente.
- Registros sem evidência suficiente devem gerar `PAGAMENTO_SEM_PROVEDOR` ou `PAGAMENTO_EFI_NAO_CONFIRMADO`, conforme o caso.
- Classificar cada registro por `provider` e por evidências disponíveis antes de mapear para `pagamento`.
- Não converter pagamento Mercado Pago em pagamento Efí.
- Não converter pagamento Efí em Mercado Pago pelo nome da tabela.
- Não usar saldo final como prova de pagamento.
- Webhook duplicado e consulta duplicada devem ser refletidos como evento/processamento idempotente, nunca como crédito duplicado.
- Dados necessários a auditoria devem ser normalizados; payload bruto financeiro só deve ser retido quando indispensável, protegido e com retenção curta.

## SEO

Regras:

- Cada URL atual deve ter decisão.
- URL prioritária sem decisão gera `SEO_PENDENTE`.
- Slug alterado exige redirect 301.
- Página sem inventário suficiente não deve entrar no sitemap.

## Ensaios obrigatórios

Antes da virada:

1. importação completa em staging;
2. ajuste do ETL;
3. segunda importação completa;
4. comparação entre execuções;
5. importação sombra com dados reais;
6. delta final;
7. reconciliação final;
8. aprovação formal dos gates.

Gate mínimo: duas execuções consecutivas reproduzíveis, com divergência financeira final zero. Pendências não financeiras só podem seguir com decisão formal quando não comprometerem dados, SEO, segurança ou operação.

## Saídas

O importador deve gerar:

- relatório humano em português;
- CSV/JSON de pendências;
- mapa legado -> V3;
- mapa URL atual -> URL V3;
- manifesto de mídia;
- reconciliação financeira;
- resumo executivo de go/no-go.
