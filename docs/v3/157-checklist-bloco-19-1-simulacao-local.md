# Checklist Bloco 19.1 - simulacao local

- [x] Inventario inicial gerado antes das alteracoes.
- [x] Backend exige `app.env=local`.
- [x] `app.env=nao_configurado` retorna `403`.
- [x] `app.env=staging` retorna `403`.
- [x] Fora de local nao altera status do outbox.
- [x] Fora de local nao marca `PROCESSADO`.
- [x] Fora de local nao registra auditoria de simulacao.
- [x] Simulacao em `local` continua funcionando para `ADMIN`.
- [x] `MODERADOR` continua bloqueado.
- [x] Sem sessao continua protegido por `401`.
- [x] OpenAPI documenta uso exclusivamente local.
- [x] Documentacao registra que `PROCESSADO` por simulacao nao equivale a envio real.
- [x] Nenhuma migration ou SQL de schema criada.
- [x] Nenhum envio externo, worker, scheduler, retry real ou API externa criado.
