# Bloco 3 - backend de dominio base

## Objetivo

Este bloco prepara uma base local de dominio para a futura Fase 3 do backend, espelhando de forma estrutural o schema das migrations `V001` a `V017`.

O bloco nao inicia importador real, nao acessa banco, nao cria migration, nao altera SQL, nao cria controller funcional, nao cria service de negocio e nao adiciona dependencia.

## Decisao tecnica local

O `backend/pom.xml` atual possui `spring-boot-starter-web`, `spring-boot-starter-actuator` e `spring-boot-starter-test`.

Nao ha dependencia local de `spring-boot-starter-data-jpa`, `jakarta.persistence` ou Spring Data JPA configurada para uso de entidades JPA e repositories.

Por isso, o Bloco 3 criou apenas records Java 17 e enums de dominio em Java puro:

- sem anotacoes JPA;
- sem repositories;
- sem services;
- sem controllers de dominio;
- sem datasource;
- sem Flyway em runtime;
- sem download de dependencia;
- sem mudanca no `pom.xml`.

Marcadores de pendencia:

- `PENDENTE_JPA_JAKARTA_PERSISTENCE`: criar entidades JPA anotadas somente quando a dependencia JPA estiver aprovada localmente.
- `PENDENTE_REPOSITORIES_SPRING_DATA_JPA`: criar repositories somente quando Spring Data JPA estiver aprovado localmente.

## Pacotes criados

Base criada em `backend/src/main/java/br/com/topsdojob/v3/domain`.

Pacotes:

- `shared`;
- `usuario`;
- `localizacao`;
- `anuncio`;
- `midia`;
- `documento`;
- `moderacao`;
- `premium`;
- `credito`;
- `financeiro`;
- `metrica`;
- `seo`;
- `banner`;
- `auditoria`;
- `comercial`;
- `suporte`;
- `importacao`.

## Material criado

Foram criados:

- 52 records de dominio com constante `TABELA`;
- 61 enums de dominio, incluindo enums aninhados por pacote;
- 1 referencia estrutural para as 9 tabelas `stg_*`.

Cobertura estrutural:

- 68 tabelas existem nas migrations;
- 52 tabelas possuem record direto;
- 9 tabelas `stg_*` foram agrupadas em `StagingImportacaoReferencia`, sem criar importador;
- 7 tabelas ficaram conscientemente pendentes para fase futura.

Pendencias de mapeamento direto:

- `credencial_usuario`;
- `permissao`;
- `papel_permissao`;
- `backup_politica`;
- `backup_execucao`;
- `backup_artefato`;
- `backup_teste_restauracao`.

Motivo: reduzir superficie sensivel e manter o bloco limitado ao dominio base sem implementar autenticacao funcional, controle granular de permissao ou rotina de backup.

## Regras preservadas

O dominio criado preserva as decisoes centrais das migrations e documentos anteriores:

- documento privado nunca e midia publica;
- `documento_usuario.retencao_ate` permanece nullable;
- `documento_usuario.politica_retencao` inclui `ENQUANTO_HOUVER_ANUNCIO`, `DATA_DEFINIDA`, `RETENCAO_JURIDICA` e `MANUAL`;
- acesso a documento privado e auditavel por `documento_usuario_acesso`;
- auditoria usa snapshots sanitizados ou hashes em `antes_json`, `depois_json`, `antes_hash` e `depois_hash`;
- provedores financeiros preservam `EFI`, `MERCADO_PAGO_LEGADO`, `OUTRO_LEGADO` e `DESCONHECIDO`;
- Pix Efi permanece apenas modelado, sem chamada externa e sem credencial real;
- beneficios Premium, creditos, metricas e cliques WhatsApp permanecem preservaveis;
- nao ha limite artificial de clique, contato ou WhatsApp no modelo de dominio;
- frontend nao e fonte de seguranca.

## Relacionamentos

Como nao ha JPA local, os relacionamentos foram representados por campos `UUID` de referencia, por exemplo:

- `usuarioId`;
- `anuncioId`;
- `arquivoMidiaId`;
- `pagamentoId`;
- `revisaoAnuncioId`;
- `atorUsuarioId`;
- `criadoPor`.

Nao foram criados:

- `cascade`;
- `orphanRemoval`;
- `FetchType.EAGER`;
- anotacoes `@ManyToOne`, `@OneToMany`, `@OneToOne` ou `@ManyToMany`.

## Tipos Java adotados

- `UUID` para chaves;
- `OffsetDateTime` para `timestamptz`;
- `LocalDate` para `date`;
- `BigDecimal` para valores monetarios, ranking e coordenadas;
- `Integer` e `Long` para contadores, creditos e tamanhos;
- `String` para `jsonb` enquanto nao houver decisao de tipo de JSON no backend;
- enums Java para dominios fechados vindos de `CHECK`.

## Validacao estatica local

A primeira validacao estatica do codigo novo foi executada com:

```powershell
javac --release 17 -d <temp> <arquivos-domain-java>
```

Resultado: `OK`.

Maven nao foi executado nesta etapa porque nao havia `mvn` ou wrapper local disponivel sem dependencia/download adicional.

## Limites do bloco

Este bloco nao aprova o schema, nao substitui revisao Pro, nao aplica Flyway e nao inicia fase posterior.

Antes de transformar estes records em entidades JPA, e obrigatorio:

- aprovar dependencia JPA local;
- revisar mapeamentos com Pro;
- definir estrategia de JSON;
- definir conversores de enum se necessario;
- revisar indices/constraints contra uso real;
- criar testes de persistencia em banco local descartavel.
