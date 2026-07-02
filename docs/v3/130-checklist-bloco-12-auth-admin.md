# Checklist Bloco 12 - auth admin

- [x] Inventario inicial gerado fora do repositorio.
- [x] Nao houve consulta SSH a producao.
- [x] Dependencia Spring Security adicionada.
- [x] JWT/OAuth/social login nao foram criados.
- [x] Endpoints admin auth criados.
- [x] `/api/admin/**` exige autenticacao.
- [x] `/api/public/**` permanece publico.
- [x] Sessao usa cookie HttpOnly e SameSite=Lax.
- [x] Secure fica falso somente em local HTTP.
- [x] CSRF local documentado como `PENDENTE_CSRF_ADMIN_PRODUCAO`.
- [x] CORS com credentials permanece restrito a localhost em local.
- [x] Dados admin sinteticos criados apenas para E2E descartavel.
- [x] Nenhuma credencial real, senha real ou hash real usado.
- [x] Frontend nao usa localStorage/sessionStorage.
- [x] Nenhuma migration criada.
- [x] Nenhum SQL de schema alterado.
- [x] Nenhuma acao administrativa critica criada.
- [x] Nenhuma moderacao real, financeiro/Pix ou importador real criado.
- [x] Nenhum remote, push ou commit executado.

## Complemento Bloco 13

- [x] APP_ENV fail-closed aplicado no arquivo base.
- [x] API desconhecida bloqueada por deny-all.
- [x] Frontend admin usa `credentials: "include"`.
- [x] Login admin nao fica pre-preenchido.
- [x] RESUMO-ENTREGA nao registra testes como `Nenhum` sem metadados.
