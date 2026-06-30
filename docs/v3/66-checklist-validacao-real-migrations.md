# Checklist de validação real das migrations - Fase 1D.4

Status da entrega: `OK PARA VALIDAÇÃO LOCAL, OK PARA AUDITORIA TÉCNICA LOCAL, AGUARDANDO_REVISAO_PRO`.

## Checklist executado

- [x] Inventário inicial criado fora do repositório antes das alterações.
- [x] Inventário inicial mantido fora do Git.
- [x] Validação SQL estática executada antes das alterações.
- [x] Docker CLI detectado localmente.
- [x] Docker Desktop local instalado detectado.
- [x] Docker Desktop foi iniciado pelo script na primeira execução da fase.
- [x] Docker daemon ficou disponível localmente.
- [x] Imagem PostgreSQL local encontrada.
- [x] Nenhum pull de imagem foi necessário.
- [x] Nenhum pull de imagem foi executado.
- [x] Flyway CLI não encontrado.
- [x] Imagem Flyway local não encontrada.
- [x] Ausência de Flyway não bloqueou a fase porque foi usado fallback local seguro `SQL_ORDENADO_PSQL`.
- [x] PostgreSQL local descartável criado.
- [x] Nenhum volume persistente criado.
- [x] Migrations `V001` a `V017` aplicadas em ordem.
- [x] Todas as migrations aplicadas sem erro SQL.
- [x] Extensões `pg_trgm` e `unaccent` criadas no banco descartável.
- [x] Schema descartável inspecionado.
- [x] Container descartável removido.
- [x] Rede descartável removida.
- [x] Nenhum container ou rede `topsv3-pg-1d4` remanescente encontrado.
- [x] Nenhum dado real usado.
- [x] Nenhum seed real criado.
- [x] Nenhum dump, backup ou importação usado.
- [x] Nenhum importador criado.
- [x] Nenhuma entidade JPA criada.
- [x] Nenhum repository criado.
- [x] Nenhum service de domínio criado.
- [x] Nenhum controller de domínio criado.
- [x] Nenhuma produção acessada.
- [x] Nenhum VPS acessado.
- [x] Nenhum banco de produção acessado.
- [x] Nenhuma Efí real acessada.
- [x] Nenhuma OpenAI ou API externa acessada.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.
- [x] Nenhuma fase posterior iniciada.

## Resultado técnico

- [x] `VALIDATION_RESULT=OK_POSTGRES_DESCARTAVEL`.
- [x] `METODO_APLICACAO=SQL_ORDENADO_PSQL`.
- [x] `POSTGRES_EXECUTADO=True`.
- [x] `FLYWAY_EXECUTADO=False`.
- [x] `SQL_ORDENADO_EXECUTADO=True`.
- [x] `MIGRATIONS_APLICADAS=True`.
- [x] `BANCO_DESCARTADO=True`.
- [x] Tabelas em `public`: 68.
- [x] Índices em `public`: 222.
- [x] Constraints em `public`: 853.
- [x] Extensões de busca: `pg_trgm`, `unaccent`.

## Gates preservados

- [ ] Não tratar o schema como aprovado antes da revisão Pro.
- [ ] Não aplicar migrations em banco persistente.
- [ ] Não executar Flyway contra banco persistente.
- [ ] Não iniciar importador.
- [ ] Não criar backend de domínio sobre o schema.
- [ ] Não iniciar Fase 2 ou fase dependente.
- [ ] Não acessar produção, VPS, banco de produção, Efí real, OpenAI ou API externa.

## Complemento 1D.5

- [x] Pacote final de auditoria consolidado.
- [x] Nenhuma migration nova criada.
- [x] Nenhum SQL de schema alterado.
- [x] Relatório final `docs/v3/67-relatorio-final-fase-1d.md` criado.
- [x] Checklist final `docs/v3/68-checklist-final-fase-1d.md` criado.
- [x] Exit codes do validador PostgreSQL padronizados para sucesso, falha real e pendência operacional.
