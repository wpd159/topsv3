# Backend

Skeleton Spring Boot local do Tops do Job V3.

Escopo da Fase 1B:

- aplicação Spring Boot mínima;
- Java 17 LTS como padrão conservador;
- endpoints locais `GET /api/health`, `GET /api/health/readiness` e `GET /api/health/liveness`;
- resposta de health padronizada com `status` igual a `UP`;
- Actuator local para health;
- perfis `local` e `test` documentais em `application-local.yml` e `application-test.yml`;
- nenhuma migration;
- nenhum SQL;
- nenhuma entidade JPA de negócio;
- nenhum repository de domínio;
- nenhum service de negócio;
- nenhuma integração Efí real.

Comando futuro, somente quando as dependências já estiverem autorizadas/disponíveis:

```powershell
mvn spring-boot:run
```
