# Checklist Bloco 3 - backend de dominio

## Escopo

- [x] Workspace local `C:\topsv3` usado.
- [x] Inventario inicial criado antes das alteracoes.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.
- [x] Nenhum acesso a VPS, producao, banco de producao ou Efi real.
- [x] Nenhuma API externa, OpenAI, download ou instalacao executada.
- [x] Nenhum dump, dado real, arquivo real de entrada ou importacao real usado.

## Codigo

- [x] Pacotes de dominio criados em `backend/src/main/java/br/com/topsdojob/v3/domain`.
- [x] Enums de dominio criados a partir dos `CHECK` das migrations.
- [x] Records Java 17 criados para tabelas principais.
- [x] Referencia estrutural criada para `stg_*`.
- [x] `UUID` usado para chaves e referencias.
- [x] `OffsetDateTime` usado para `timestamptz`.
- [x] `LocalDate` usado para `date`.
- [x] `BigDecimal` usado para dinheiro, ranking e coordenadas.
- [x] Nenhuma anotacao JPA criada.
- [x] Nenhum repository criado.
- [x] Nenhum service de negocio criado.
- [x] Nenhum controller de dominio criado.
- [x] Nenhum SQL criado ou alterado.
- [x] Nenhuma migration criada ou alterada.

## Decisoes documentadas

- [x] `PENDENTE_JPA_JAKARTA_PERSISTENCE` documentado.
- [x] `PENDENTE_REPOSITORIES_SPRING_DATA_JPA` documentado.
- [x] Documento privado continua nao publicavel.
- [x] Acesso a documento privado continua auditavel.
- [x] `retencao_ate` continua nullable.
- [x] Politica de retencao de documento preserva `ENQUANTO_HOUVER_ANUNCIO`.
- [x] Auditoria continua sanitizada.
- [x] Pix Efi continua sem integracao real.
- [x] Metricas e cliques WhatsApp continuam preservaveis sem limite artificial.
- [x] Frontend continua nao sendo fonte de seguranca.

## Validacoes esperadas

- [x] `javac --release 17` dos novos arquivos de dominio.
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
- [x] `git add .`.
- [x] Repeticao das validacoes apos staging.
- [x] ZIP final de revisao na Area de Trabalho.

## Resultado esperado

O Bloco 3 fica pronto quando:

- os arquivos seguros estiverem staged;
- os scanners obrigatorios passarem;
- o ZIP `BLOCO-3` for criado;
- nenhuma fase posterior for iniciada;
- as pendencias JPA/repository permanecerem documentadas.
