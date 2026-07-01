# Correcao persistencia JPA - Bloco 4.1

## Objetivo

Endurecer a camada JPA criada no Bloco 4 antes de qualquer uso por services, controllers ou endpoints.

Esta fase nao criou banco, migration, SQL, controller, endpoint, service de negocio, importador real, dado real, dump, integracao externa, remote, push ou commit.

## Entidades endurecidas

Foram endurecidas 41 entidades em `backend/src/main/java/br/com/topsdojob/v3/persistence/entity`.

Grupos cobertos:

- localizacao;
- usuario;
- anuncio;
- midia;
- documento;
- moderacao;
- premium;
- credito;
- financeiro;
- metrica;
- seo;
- banner;
- auditoria;
- comercial;
- suporte;
- backup.

## Construtores protegidos

Todas as 41 entidades receberam construtor JPA protegido explicito no formato:

```java
protected NomeEntity() {
}
```

Isso evita dependencia de construtor publico implicito e deixa claro que a instanciacao direta nao faz parte do contrato funcional desta fase.

## Getters

Foram adicionados 459 getters publicos para campos persistidos top-level.

Nao foram adicionados setters publicos em massa. A fase continua sem regra de negocio, sem factories, sem comandos e sem mutacao funcional.

## Jsonb

Campos `jsonb` permanecem como `String` e mantem `columnDefinition = "jsonb"`.

Foi adicionado:

```java
@JdbcTypeCode(SqlTypes.JSON)
```

Decisao:

- usar Hibernate 6 transitivo de Spring Boot 3.3.5 / Spring Data JPA;
- nao adicionar dependencia nova;
- nao executar Maven sem ferramenta local;
- registrar `PENDENTE_JSONB_HIBERNATE_RUNTIME` ate validacao de build/runtime local autorizada.

Campos revisados:

- `documento_busca_anuncio.beneficios_ranking_json`;
- `revisao_anuncio.payload_solicitado`;
- `auditoria_evento.antes_json`;
- `auditoria_evento.depois_json`;
- `outbox_evento.payload_json`.

## BigDecimal

Campos `BigDecimal` receberam `precision` e `scale` conforme o schema ja existente:

- dinheiro: `numeric(12,2)`;
- coordenadas: `numeric(9,6)`;
- ranking: `numeric(10,4)`.

Campos ajustados:

- `anuncio.preco`;
- `documento_busca_anuncio.preco`;
- `pagamento.valor`;
- `pagamento_conciliacao.valor_confirmado`;
- `ativacao_beneficio.preco_snapshot`;
- `anuncio_localizacao.latitude`;
- `anuncio_localizacao.longitude`;
- `documento_busca_anuncio.ranking_base`.

## Validador estatico

Criado:

```text
scripts/local/validar-persistencia-jpa-estatica.ps1
```

O script valida, sem banco e sem rede:

- entidades com `@Entity`;
- entidades com construtor protegido explicito;
- entidades com `@Table`;
- campos persistidos com `@Column`;
- getters publicos para campos persistidos;
- `jsonb` com `@JdbcTypeCode(SqlTypes.JSON)`;
- `BigDecimal` com `precision` e `scale`;
- ausencia de `CascadeType`, `orphanRemoval` e `FetchType.EAGER`;
- repositories ainda minimos;
- ausencia de SQL/migration alterada;
- ausencia de controller, service ou importador criado nesta fase;
- ausencia de uso de banco/rede/dados reais em Java alterado.

Resultado inicial:

```text
VALIDATION_RESULT=OK_PERSISTENCIA_JPA_ESTATICA
Total de verificacoes: 18
Verificacoes OK: 18
Verificacoes com falha: 0
```

## Pendencias

- `PENDENTE_BUILD_MAVEN_LOCAL`: Maven/wrapper local indisponivel sem download.
- `PENDENTE_JAVA_17_LOCAL`: Java local detectado como 21 LTS, mas o backend exige Java 17 LTS.
- `PENDENTE_MAVEN_WRAPPER_LOCAL`: wrapper ausente na raiz e em `backend/`.
- `PENDENTE_NODE_MODULES_LOCAL`: frontend sem `node_modules`, portanto build/lint nao deve rodar sem autorizacao de instalacao futura.
- `PENDENTE_JSONB_HIBERNATE_RUNTIME`: anotacao Hibernate JSON precisa de validacao de build/runtime local.
- Validacao Spring Boot/JPA completa continua bloqueada ate existir ferramenta local autorizada.

## Complemento Bloco 4.2

O Bloco 4.2 adiciona validadores locais de build backend/frontend com retorno `0`, `1` e `2`.

Retorno `2` representa pendencia operacional, nao aprovacao. Services, controllers, endpoints e importador real continuam bloqueados ate build backend validado ou decisao expressa documentada.

## Confirmacoes

- Nenhuma fase posterior foi iniciada.
- Nenhum banco foi acessado.
- Nenhuma migration foi criada.
- Nenhum SQL foi alterado.
- Nenhum service de negocio foi criado.
- Nenhum controller ou endpoint foi criado.
- Nenhum importador real foi criado.
- Nenhum dado real, dump ou arquivo real de entrada foi lido.
- Nenhuma API externa foi acessada.
- Nenhum remote foi configurado.
- Nenhum push ou commit foi executado.

## Complemento Bloco 4.4

A anotacao JSON e a camada JPA foram validadas em build local de compilacao/testes com Java 17 e Maven 3.9.9 localizados por processo.

Resultado:

- `mvn -q -DskipTests compile`: OK;
- `mvn -q test`: OK;
- `scripts/local/validar-persistencia-jpa-estatica.ps1`: mantido como validador estatico complementar.

A validacao runtime com banco permanece fora do Bloco 4.4. Nao houve Flyway, banco, migration nova, SQL novo, service, controller, endpoint ou importador real.
