# Checklist final da Fase 1D

Status da entrega: `OK PARA AUDITORIA TÉCNICA LOCAL, AGUARDANDO_REVISAO_PRO`.

## Consolidação

- [x] Migrations `V001` a `V017` criadas.
- [x] Nenhuma migration nova criada na Fase 1D.5.
- [x] Nenhum SQL de schema alterado na Fase 1D.5.
- [x] Ordem das migrations documentada.
- [x] Tabelas principais documentadas.
- [x] Pendências Pro documentadas.
- [x] Riscos residuais documentados.
- [x] Proibições antes da aprovação final documentadas.
- [x] Regra de não iniciar importador/backend de domínio antes da aprovação documentada.

## Validações

- [x] Validação SQL estática executada.
- [x] Validação SQL estática com `27/27` verificações OK.
- [x] Validação PostgreSQL local descartável executada.
- [x] `VALIDATION_RESULT=OK_POSTGRES_DESCARTAVEL`.
- [x] PostgreSQL local descartável executado com imagem local `postgres:16`.
- [x] Nenhum pull/download/instalação executado.
- [x] Método `SQL_ORDENADO_PSQL` usado porque Flyway real não estava disponível localmente.
- [x] Migrations `V001` a `V017` aplicadas em ordem sem erro.
- [x] Schema descartável inspecionado: 68 tabelas, 222 índices, 853 constraints.
- [x] Extensões `pg_trgm` e `unaccent` criadas no banco descartável.
- [x] Container e rede descartáveis removidos.
- [x] Nenhum volume persistente criado.
- [x] Validação de rotas públicas/SEO local executada.
- [x] Scanners de codificação, arquivos proibidos e secrets executados.
- [x] `git diff --check` aprovado.
- [x] `git diff --cached --check` aprovado.

## Exit codes

- [x] Validador PostgreSQL retorna `0` somente para `OK_POSTGRES_DESCARTAVEL`.
- [x] Validador PostgreSQL retorna `1` para falha real de migration/SQL.
- [x] Validador PostgreSQL retorna `2` para pendência operacional.
- [x] Caso atual `SQL_ORDENADO_PSQL` continua retornando `0`.

## Bloqueios

- [x] Produção não acessada.
- [x] VPS não acessada.
- [x] Banco de produção não acessado.
- [x] Efí real não acessada.
- [x] OpenAI/API externa não acessada.
- [x] Nenhum dado real usado.
- [x] Nenhum seed real criado.
- [x] Nenhum dump usado.
- [x] Nenhum importador criado.
- [x] Nenhuma entidade JPA criada.
- [x] Nenhum repository criado.
- [x] Nenhum service de domínio criado.
- [x] Nenhum controller de domínio criado.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.
- [x] Nenhuma Fase 2 ou fase dependente iniciada.

## Gates Pro

- [ ] Revisar e aprovar schema final.
- [ ] Decidir uso final de `pg_trgm` e `unaccent`.
- [ ] Revisar custo de índices.
- [ ] Revisar `CHECK` versus catálogo.
- [ ] Validar sanitizer de auditoria.
- [ ] Validar retenção final de documentos privados.
- [ ] Validar importação futura contra dados legados antes de qualquer promoção.
- [ ] Executar Flyway real quando ferramenta local estiver disponível sem instalação/download.
