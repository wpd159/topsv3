# Checklist de validação PostgreSQL descartável - Fase 1D.3

Status da entrega: `OK PARA AUDITORIA TÉCNICA LOCAL, AGUARDANDO_REVISAO_PRO`.

## Checklist executado

- [x] Inventário inicial criado fora do repositório antes das alterações.
- [x] Inventário inicial mantido fora do Git.
- [x] Migrations existentes revisadas apenas no escopo de SQL, FK, constraint e ordem.
- [x] Validador SQL estático executado antes das alterações.
- [x] Script `scripts/local/validar-migrations-postgres-descartavel.ps1` criado.
- [x] Script de PostgreSQL descartável executado localmente.
- [x] Pendência `PENDENTE_VALIDACAO_POSTGRES_LOCAL` registrada porque Docker daemon/Flyway não estavam disponíveis para validação sem instalação ou download.
- [x] Banco local descartável criado ou pendência registrada.
- [x] Flyway executado apenas contra banco local descartável ou pendência registrada.
- [x] Migrations aplicadas em banco vazio ou pendência registrada.
- [x] Nenhuma imagem Docker foi baixada.
- [x] Nenhuma ferramenta foi instalada.
- [x] Nenhum container PostgreSQL foi criado nesta máquina.
- [x] Nenhum volume persistente foi criado.
- [x] Nenhum banco persistente foi criado.
- [x] Nenhuma migration foi aplicada nesta máquina.
- [x] Flyway não foi executado nesta máquina.
- [x] `V005` recebeu constraint de consistência entre `tipo` e `finalidade` para stories.
- [x] `V008` recebeu FK composta para consistência entre `pagamento_evento.provedor` e `pagamento.provedor`.
- [x] Documento privado continua não publicável.
- [x] `documento_usuario.retencao_ate` continua nullable.
- [x] `auditoria_evento.antes_json` e `auditoria_evento.depois_json` continuam dependentes de sanitizer futuro da aplicação.
- [x] Nenhum dado real, seed, dump, backup ou importação foi usado.
- [x] Nenhuma entidade JPA de domínio foi criada.
- [x] Nenhum repository de domínio foi criado.
- [x] Nenhum service de negócio foi criado.
- [x] Nenhum controller de domínio foi criado.
- [x] Nenhum importador foi criado.
- [x] Nenhuma produção, VPS, banco de produção, Efí real, OpenAI ou API externa foi acessada.
- [x] Nenhum remote foi configurado.
- [x] Nenhum push foi executado.
- [x] Nenhum commit foi executado.
- [x] Fase dependente do schema não foi iniciada.
- [x] Scanners de codificação, arquivos proibidos e segredos executados.
- [x] Validação local de rotas públicas e SEO executada.
- [x] `git diff --cached --check` aprovado.
- [x] ZIP final criado e validado.

## Checklist pendente para execução local futura

- [ ] Iniciar/fornecer Docker daemon local sem acessar produção.
- [ ] Garantir imagem PostgreSQL local já existente, sem `docker pull` automático.
- [ ] Garantir Flyway CLI ou imagem Flyway local já existente, sem instalação automática.
- [ ] Executar `scripts/local/validar-migrations-postgres-descartavel.ps1`.
- [ ] Confirmar `VALIDATION_RESULT=OK_POSTGRES_DESCARTAVEL`.
- [ ] Confirmar migrations em `flyway_schema_history` no banco descartável.
- [ ] Confirmar remoção do container e da rede descartável.
- [ ] Confirmar que nenhum volume persistente foi criado.
- [ ] Anexar relatório da execução local à auditoria Pro.

## Gates preservados

- [ ] Não tratar migrations como aprovadas antes da revisão Pro.
- [ ] Não aplicar migrations em banco persistente.
- [ ] Não criar domínio Java/JPA sobre o schema ainda.
- [ ] Não iniciar importador.
- [ ] Não iniciar Fase 2 ou fase dependente.
- [ ] Não acessar produção, VPS, banco de produção, Efí real, OpenAI ou API externa.

## Complemento 1D.4

- [x] Validação real em PostgreSQL local descartável executada na Fase 1D.4.
- [x] Docker Desktop local iniciado pelo script quando o daemon estava indisponível.
- [x] Imagem PostgreSQL local `postgres:16` usada sem pull.
- [x] Flyway não estava disponível, mas fallback `SQL_ORDENADO_PSQL` foi usado sem download.
- [x] Migrations `V001` a `V017` aplicadas em ordem sem erro SQL.
- [x] Container e rede descartáveis removidos.
