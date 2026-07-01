# Bloco 4 - persistencia JPA base

## Objetivo

Criar a base de persistencia backend com JPA/Spring Data alinhada ao schema `V001` a `V017`, sem controller funcional, sem endpoint de dominio, sem service de negocio, sem importador real, sem banco e sem dados reais.

O checkpoint inicial estava limpo no commit:

```text
2fc3ccb feat: consolida base v3 local ate bloco 3
```

## Dependencias

O `backend/pom.xml` nao possuia Spring Data JPA nem driver PostgreSQL.

Dependencias minimas adicionadas:

- `spring-boot-starter-data-jpa`;
- `org.postgresql:postgresql` com `scope` `runtime`.

Nao foram adicionados:

- Lombok;
- MapStruct;
- bibliotecas de JSON extra;
- drivers ou clientes externos;
- dependencia de storage;
- qualquer ferramenta nova.

Nao houve download de dependencias nem execucao de Maven.

Marcador operacional:

- `PENDENTE_BUILD_MAVEN_LOCAL`: Maven/wrapper nao estava disponivel localmente sem download.

## Arquitetura criada

Camada nova:

- `backend/src/main/java/br/com/topsdojob/v3/persistence/entity`;
- `backend/src/main/java/br/com/topsdojob/v3/persistence/repository`;
- `backend/src/main/java/br/com/topsdojob/v3/persistence/shared`.

Os records Java puros do Bloco 3 permanecem em `domain/*` e nao foram convertidos em entidades.

## Padroes usados

- Java 17;
- `jakarta.persistence`;
- `UUID` para chaves e referencias;
- `OffsetDateTime` para `timestamptz`;
- `LocalDate` para `date`;
- `BigDecimal` para valores monetarios, ranking e coordenadas;
- `EnumType.STRING`;
- `@Table(name = "...")`;
- `@Column(name = "...")` explicito;
- `@Id`;
- `@Version` onde existe coluna `versao`;
- `String` para `jsonb` com `columnDefinition = "jsonb"` e endurecimento posterior com `@JdbcTypeCode(SqlTypes.JSON)`.

Relacionamentos foram representados por UUIDs. Nao foram criados `cascade`, `orphanRemoval`, relacionamento `EAGER` ou associacoes JPA complexas.

## Endurecimento Bloco 4.1

O Bloco 4.1 endureceu a camada JPA sem criar services, controllers, endpoints, banco ou migrations.

Ajustes aplicados:

- 41 entidades com construtor `protected NomeEntity() {}` explicito;
- 459 getters publicos adicionados para campos persistidos;
- nenhum setter publico indiscriminado;
- nenhum relacionamento JPA complexo;
- nenhum `CascadeType`, `orphanRemoval` ou `FetchType.EAGER`;
- campos `jsonb` com `@JdbcTypeCode(SqlTypes.JSON)`, mantendo `columnDefinition = "jsonb"`;
- campos `BigDecimal` com `precision` e `scale` alinhados as migrations: dinheiro `numeric(12,2)`, coordenadas `numeric(9,6)` e ranking `numeric(10,4)`;
- script `scripts/local/validar-persistencia-jpa-estatica.ps1` criado para validar a camada sem Maven, banco ou rede.

Decisao sobre `jsonb`:

- `@JdbcTypeCode(SqlTypes.JSON)` foi aplicado porque o backend usa Spring Boot 3.3.5 e Spring Data JPA, com Hibernate 6 transitivo.
- `PENDENTE_JSONB_HIBERNATE_RUNTIME`: a validacao runtime segue pendente ate existir Maven/wrapper local ou outro build local autorizado.

## Entidades JPA criadas

Foram criadas 41 entidades JPA:

- localizacao: `EstadoEntity`, `CidadeEntity`, `BairroEntity`;
- usuario: `UsuarioEntity`, `CredencialUsuarioEntity`, `PapelUsuarioEntity`;
- anuncio: `AnuncioEntity`, `AnuncioLocalizacaoEntity`, `DocumentoBuscaAnuncioEntity`;
- midia: `ArquivoMidiaEntity`, `AnuncioMidiaEntity`, `StoryAnuncioEntity`;
- documento: `DocumentoUsuarioEntity`, `DocumentoUsuarioAcessoEntity`;
- moderacao: `RevisaoAnuncioEntity`, `AnuncioMidiaRevisaoEntity`, `DecisaoModeracaoEntity`;
- premium: `BeneficioPremiumEntity`, `GrupoAtivacaoBeneficioEntity`, `AtivacaoBeneficioEntity`;
- credito: `MovimentoCreditoEntity`;
- financeiro: `PagamentoEntity`, `PagamentoEventoEntity`, `PagamentoConciliacaoEntity`;
- metrica: `EventoVisualizacaoEntity`, `CliqueWhatsappEntity`, `AgregadoVisualizacaoDiariaEntity`, `AgregadoCliqueWhatsappDiarioEntity`;
- seo: `SeoUrlEntity`, `SeoConteudoPaginaEntity`, `SeoRedirectEntity`;
- banner: `BannerEntity`;
- auditoria: `AuditoriaEventoEntity`, `OutboxEventoEntity`;
- comercial: `ComercialContatoEntity`, `ComercialInteracaoEntity`;
- suporte: `TicketSuporteEntity`;
- backup: `BackupPoliticaEntity`, `BackupExecucaoEntity`, `BackupArtefatoEntity`, `BackupTesteRestauracaoEntity`.

## Repositories criados

Foram criados 10 repositories minimos:

- `UsuarioRepository`;
- `AnuncioRepository`;
- `ArquivoMidiaRepository`;
- `DocumentoUsuarioRepository`;
- `AtivacaoBeneficioRepository`;
- `MovimentoCreditoRepository`;
- `PagamentoRepository`;
- `SeoUrlRepository`;
- `BannerRepository`;
- `AuditoriaEventoRepository`.

Nao foram criadas queries customizadas, query nativa, service, controller ou endpoint.

## Seguranca e privacidade

Decisoes preservadas:

- documento privado nao vira midia publica;
- `documento_usuario.retencao_ate` permanece nullable;
- `politica_retencao` inclui `ENQUANTO_HOUVER_ANUNCIO`;
- acesso a documento privado segue auditavel em `documento_usuario_acesso`;
- snapshots JSON de auditoria sao conceitualmente sanitizados;
- pagamentos distinguem `EFI`, `MERCADO_PAGO_LEGADO`, `OUTRO_LEGADO` e `DESCONHECIDO`;
- nao ha chamada real a Efi ou Mercado Pago;
- premium e creditos foram mapeados sem regra de negocio;
- cliques WhatsApp e metricas foram mapeados sem limite comercial artificial;
- frontend nao e fonte de seguranca.

## Pendencias

- `PENDENTE_BUILD_MAVEN_LOCAL`: Maven/wrapper local indisponivel sem download.
- `PENDENTE_JAVA_17_LOCAL`: Java detectado localmente como 21 LTS, enquanto o backend exige Java 17 LTS.
- `PENDENTE_MAVEN_WRAPPER_LOCAL`: `backend/mvnw.cmd` e `mvnw.cmd` ausentes.
- `PENDENTE_JSONB_HIBERNATE_RUNTIME`: mapeamento `jsonb` com Hibernate 6 ainda precisa de build/runtime local.
- Validacao de runtime JPA em PostgreSQL local descartavel ainda pendente.
- Sanitizers definitivos para payloads `jsonb` seguem pendentes para camada futura.
- Repositories adicionais dependem de revisao de caso de uso.
- Tabelas de importacao/staging seguem fora dos repositories funcionais.
- Autenticacao, sessao, permissao e webhooks reais seguem bloqueados para fases futuras.

## Limites

Nao houve:

- migration nova;
- alteracao de SQL;
- banco acessado;
- Flyway executado;
- dump ou dado real;
- arquivo real de entrada;
- importacao ou ETL real;
- controller funcional;
- endpoint de dominio;
- service de negocio;
- storage real;
- API externa;
- remote, push ou commit.

## Complemento Bloco 4.2

Foram criados scripts de build local para backend/frontend:

- `scripts/local/validar-build-local.ps1`;
- `scripts/local/validar-backend-build.ps1`;
- `scripts/local/validar-frontend-build.ps1`.

Eles nao instalam ferramentas, nao baixam dependencias, nao criam wrapper, nao executam `npm install` ou `npm ci`, nao acessam banco e nao acessam rede externa. Enquanto o backend build nao passar em Java 17 LTS com Maven/wrapper local, a persistencia JPA nao deve ser consumida por services/controllers.

## Complemento Bloco 4.3

O gate de toolchain local foi fechado documentalmente com diagnostico e runbook. Java 21 LTS foi detectado, mas Java 17 LTS segue pendente. Maven segue pendente. Node/npm foram detectados, mas `frontend/node_modules` segue ausente.

Nenhuma ferramenta foi instalada, nenhum download foi feito, o PATH nao foi alterado e nenhum Maven Wrapper binario foi criado.

## Complemento Bloco 4.4

O Bloco 4.4 regularizou a toolchain local com autorizacao expressa.

Pendencias resolvidas para build local:

- `PENDENTE_JAVA_17_LOCAL`: resolvida por Java 17 LTS local usado por processo;
- `PENDENTE_MAVEN_LOCAL`: resolvida por Maven 3.9.9 local;
- `PENDENTE_NODE_MODULES_LOCAL`: resolvida por `npm install` autorizado;
- `PENDENTE_BUILD_MAVEN_LOCAL`: resolvida para compile/test local;
- `PENDENTE_JSONB_HIBERNATE_RUNTIME`: reduzida no escopo de compilacao/testes, mas validacao runtime com banco continua fora desta fase.

Resultado:

- backend `compile` OK;
- backend `test` OK;
- frontend `lint` OK;
- frontend `build` OK;
- validacao agregada `OK_BUILD_LOCAL`.

Nao foram criados service, controller, endpoint, migration, SQL, seed, banco, importador real ou integracao externa.

## Complemento Bloco 5

Com o build local validado no Bloco 4.4, o Bloco 5 autorizou apenas a camada publica de leitura:

- DTOs publicos;
- mappers publicos;
- services `readOnly`;
- controllers `GET`;
- repositories derivados simples.

Continuam proibidos neste complemento:

- admin funcional;
- autenticacao real;
- escrita;
- acao critica;
- financeiro/Pix/Efi funcional;
- moderacao real;
- importador real;
- banco persistente;
- migration nova;
- SQL novo.
