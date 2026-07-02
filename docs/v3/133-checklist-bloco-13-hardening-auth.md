# Checklist Bloco 13 - hardening auth

- [x] Inventario inicial gerado fora do repositorio.
- [x] Nao houve consulta SSH a producao.
- [x] `application.yml` usa `APP_ENV:nao_configurado`.
- [x] `application-local.yml` preserva default local somente para profile local.
- [x] `SecurityConfig` removeu `anyRequest().permitAll()`.
- [x] `/api/**` desconhecida fica bloqueada por deny-all.
- [x] `/api/admin/**` exige autenticacao.
- [x] `/api/public/**` permanece publico.
- [x] Frontend admin usa `credentials: "include"`.
- [x] Frontend admin nao usa localStorage/sessionStorage.
- [x] Frontend admin nao traz credencial pre-preenchida.
- [x] Empacotador registra testes como `nao informado` quando metadados nao forem fornecidos.
- [x] Nenhuma migration criada.
- [x] Nenhum SQL de schema alterado.
- [x] Nenhuma acao administrativa critica criada.
- [x] Nenhuma moderacao real criada.
- [x] Nenhum financeiro/Pix criado.
- [x] Nenhum importador real criado.
- [x] Nenhum remote, push ou commit executado.

## Complemento Bloco 14

- [x] `EFI_PIX_MOCK_MODE` base passou a defaultar `false`.
- [x] `@EnableMethodSecurity` habilitado para RBAC de endpoints admin read-only.
- [x] Endpoints read-only usam somente `GET`.
- [x] DTOs read-only nao expoem campos sensiveis.
- [x] Frontend admin read-only usa `credentials: "include"`.
