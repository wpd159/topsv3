# Checklist Bloco 4 - persistencia JPA base

## Gate inicial

- [x] Inventario inicial criado fora do repositorio.
- [x] Checkpoint local limpo conferido.
- [x] Ultimo commit conferido como `2fc3ccb feat: consolida base v3 local ate bloco 3`.
- [x] `git remote -v` conferido vazio.

## Implementacao

- [x] Dependencias JPA verificadas.
- [x] Dependencias minimas adicionadas somente quando necessario.
- [x] Entidades JPA principais criadas.
- [x] Repositories minimos criados.
- [x] Matriz JPA -> tabela -> repository criada.
- [x] Nenhum controller funcional criado.
- [x] Nenhum endpoint de dominio criado.
- [x] Nenhum service de negocio criado.
- [x] Nenhum importador real criado.
- [x] Nenhum dump lido.
- [x] Nenhum dado real usado.
- [x] Nenhum arquivo real de entrada aberto.
- [x] Nenhuma migration criada.
- [x] Nenhum SQL alterado.
- [x] Nenhuma API externa acessada.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.

## Validacoes

- [x] `scripts/local/validar-persistencia-jpa-estatica.ps1`.
- [x] `scripts/security/verificar-codificacao.ps1`.
- [x] `scripts/security/verificar-arquivos-proibidos.ps1`.
- [x] `scripts/security/verificar-segredos.ps1`.
- [x] `scripts/local/validar-rotas-publicas-seo-local.ps1`.
- [x] `scripts/local/validar-migrations-sql-estatico.ps1`.
- [x] `scripts/local/validar-fonte-importacao-local.ps1`.
- [x] `git diff --check`.
- [x] `git diff --cached --check`.
- [x] `git status --short`.
- [x] `git remote -v`.
- [x] Maven local ou pendencia documentada.
- [x] `git add .`.
- [x] Repeticao de scanners e validacoes apos staging.
- [x] ZIP final criado e validado.

## Endurecimento Bloco 4.1

- [x] Inventario inicial do Bloco 4.1 criado fora do repositorio.
- [x] 41 entidades com construtor protegido explicito.
- [x] 459 getters publicos adicionados.
- [x] Nenhum setter publico em massa criado.
- [x] Campos `jsonb` revisados e anotados com `@JdbcTypeCode(SqlTypes.JSON)`.
- [x] `PENDENTE_JSONB_HIBERNATE_RUNTIME` documentado.
- [x] Campos `BigDecimal` com `precision` e `scale` quando aplicavel.
- [x] Script estatico JPA criado.
- [x] Nenhuma migration criada ou alterada.
- [x] Nenhum SQL alterado.
- [x] Nenhum service, controller, endpoint ou importador real criado.
- [x] Nenhum banco, Docker, Flyway, API externa, dado real, remote, push ou commit executado.

## Resultado esperado

O Bloco 4 fica pronto quando a persistencia base estiver staged, os scanners passarem, o ZIP `BLOCO-4` for criado e nenhuma fase posterior tiver sido iniciada.
