# Checklist Bloco 15 - admin read-only detalhado

- [x] Inventario inicial executado antes das alteracoes.
- [x] Nao houve consulta SSH a producao.
- [x] Health publico reduzido para `status`, `app` e `requestId`.
- [x] `app.env` e `efiPixMockMode` permanecem apenas em status admin restrito.
- [x] Endpoints detalhados usam somente `GET`.
- [x] `ADMIN` acessa todos os endpoints detalhados.
- [x] `MODERADOR` acessa anuncios, midia e revisoes.
- [x] `COMERCIAL` acessa apenas anuncios limitados.
- [x] `USUARIO` nao acessa admin.
- [x] Paginacao `page`/`size` criada com limite maximo.
- [x] Filtros simples criados sem query nativa.
- [x] DTOs nao expoem documento privado, storage, contato bruto, senha/hash/token, financeiro sensivel ou payload completo.
- [x] Frontend admin usa `credentials: "include"`.
- [x] Frontend admin segue sem localStorage/sessionStorage.
- [x] Frontend admin segue sem botoes funcionais de acao critica.
- [x] Dados sinteticos locais cobrem LIVRE, BLOQUEADO, PENDENTE_REVISAO, midia pendente e revisao aberta.
- [x] OpenAPI atualizado.
- [x] Nenhuma migration ou SQL de schema criada.
- [x] Nenhuma fase posterior iniciada.

## Nota posterior - Bloco 16

- [x] O read-only do Bloco 15 foi preservado como base.
- [x] Somente acoes locais minimas de moderacao foram adicionadas posteriormente.
