# Plano de migrations Flyway

## Escopo da Fase 1C.1

A Fase 1C.1 prepara a Fase 1D. Este documento não cria migration, SQL executável, schema, tabela, seed, entidade JPA, repository ou service. A Fase 1D será a primeira fase autorizada a criar migrations Flyway reais.

Produção permanece intocada. Nenhum banco é acessado nesta fase.

## Ferramenta oficial

Flyway é a ferramenta oficial de migrations da V3. Migrations devem ser SQL explícito, versionado em Git, revisado antes de execução e reproduzível em ambiente local descartável.

Não haverá:

- migration automática por ORM;
- executor próprio de migrations;
- alteração manual de schema em produção;
- execução de migration no startup operacional comum da aplicação;
- seed com dado real;
- segredo, certificado ou credencial em migration.

## Padrão de nomes

Padrão planejado:

```text
VNNN__descricao_curta_sem_acentos.sql
```

Regras:

- `NNN` usa sequência com três dígitos.
- A descrição usa letras minúsculas, números e `_`.
- Nomes permanecem sem acentos.
- Uma migration deve ter objetivo claro e revisável.
- Migrations aplicadas fora de ambiente local descartável são imutáveis.

Exemplo textual planejado:

```text
V001__extensoes_postgresql.sql
```

Esse exemplo é apenas documentação. Nenhum arquivo SQL é criado na Fase 1C.1.

## Política de imutabilidade

Depois que uma migration for aplicada em qualquer ambiente compartilhado, ela não deve ser editada. Correções devem entrar em nova migration com versão posterior.

Ambiente local descartável pode ser recriado do zero durante desenvolvimento, mas a versão revisada que entrar no pacote da Fase 1D deve ser tratada como imutável a partir da revisão.

## Execução fora do startup comum

O runtime da aplicação não deve executar migration no startup operacional comum. A execução deve ocorrer em fluxo explícito de infraestrutura/local, com logs, código de saída, revisão de alvo e separação de ambiente.

## Rollback operacional

Rollback não deve ser feito por edição manual reversa no banco. A estratégia operacional é:

- backup antes de execução;
- restore testado;
- rollback por restauração de backup quando necessário;
- nova migration corretiva quando o ambiente permitir avanço seguro;
- registro de decisão e incidente.

## Validação de banco vazio

Na Fase 1D, banco vazio deve ser validado por:

- criar banco local descartável;
- aplicar migrations do zero;
- confirmar versão final do Flyway;
- conferir tabelas, índices, constraints e extensões esperadas;
- rodar smoke tests locais autorizados;
- descartar e recriar para verificar repetibilidade.

## Validação de repetibilidade local

Critério mínimo para repetibilidade:

- duas execuções em banco vazio geram o mesmo conjunto de objetos;
- `flyway validate` passa;
- não há dependência de ordem externa não documentada;
- não há dado real, seed obrigatório ou segredo;
- todas as migrations usam apenas recursos aprovados para PostgreSQL local.

## Materialização na Fase 1D

Na Fase 1D, os arquivos `V001` a `V017` foram gerados localmente para auditoria Pro em `backend/src/main/resources/db/migration`.

Esta materialização não equivale a aprovação nem aplicação:

- Flyway não foi executado.
- Nenhum banco foi acessado ou iniciado.
- Nenhuma migration foi aplicada.
- O status da entrega é `AGUARDANDO_REVISAO_PRO`.
- Execução em banco local descartável exige autorização futura.

## Revisão antes de executar

Cada migration da Fase 1D deve passar por revisão antes de execução:

- escopo e objetivo;
- dependências anteriores;
- objetos criados ou alterados;
- risco de lock ou tempo de execução;
- risco financeiro, autenticação, mídia, SEO e importação;
- compatibilidade com Java 17 LTS e PostgreSQL local;
- ausência de dados reais, secrets e seed;
- aderência ao ADR-011 e documentos `24`, `25` e `26`.

## Proteção contra dados reais, seed e secrets

Migrations não podem conter:

- credenciais;
- certificados;
- tokens;
- dumps;
- dados pessoais reais;
- payload financeiro real;
- seed de produção;
- URLs privadas ou chaves de storage reais.

Dados de teste, quando autorizados em fase futura, devem ficar separados de migrations estruturais e nunca representar produção.

## Bloqueios para iniciar a Fase 1D

- Revisar `docs/v3/29-ordem-migrations-v3.md`.
- Fechar decisões pendentes registradas em `docs/v3/30-decisoes-pre-fase-1d.md`.
- Confirmar estratégia técnica de UUID v7 em Java 17.
- Decidir `pg_trgm` e `unaccent` para a migration inicial.
- Definir CHECK constraint versus tabela de catálogo por enum crítico.
- Confirmar que não haverá execução contra produção.
