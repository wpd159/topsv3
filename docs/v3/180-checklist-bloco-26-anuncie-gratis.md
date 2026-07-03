# Checklist Bloco 26 - Anuncie gratis

## Checkpoint local

- [x] Validacoes preliminares executadas antes das alteracoes.
- [x] Remote Git permaneceu vazio.
- [x] Checkpoint local pre-Bloco 26 criado quando a identidade Git existente permitiu.
- [x] Push nao executado.
- [x] Remote nao configurado.

## Backend

- [x] Endpoint `POST /api/public/anunciar` criado.
- [x] DTOs de request, response e erro de validacao criados.
- [x] Validacao 400 criada para payload invalido.
- [x] Campos perigosos bloqueados.
- [x] Anuncio nasce `PENDENTE_REVISAO`.
- [x] Moderacao nasce `PENDENTE`.
- [x] Revisao nasce `ABERTA`.
- [x] Documento de busca nasce `NAO_PUBLICAVEL`.
- [x] Classificacao inicial usa apenas `LIVRE`.
- [x] WhatsApp sintetico repetido reutiliza usuario local e nao gera erro 500.
- [x] Nenhuma publicacao automatica.
- [x] Nenhum upload real.
- [x] Nenhum pagamento, credito, Pix ou Efi.
- [x] Nenhum Premium obrigatorio.

## Frontend

- [x] Pagina `/anunciar` funcional local.
- [x] Formulario com dados sinteticos.
- [x] Bloco 26.2 converteu o formulario em wizard progressivo.
- [x] Envio ocorre apenas na revisao final.
- [x] Estado inicial.
- [x] Estado de validacao de erro.
- [x] Estado de sucesso.
- [x] Aviso de upload futuro.
- [x] Sem redesign.
- [x] Sem nova paleta.
- [x] Sem nova tipografia.
- [x] Viewport mobile explicito.
- [x] Sem scroll horizontal em medicao DOM mobile.
- [x] Sem `localStorage`.
- [x] Sem `sessionStorage`.
- [x] Sem `document.body.style.overflow`.
- [x] Sem scroll lock.
- [x] Sem botao flutuante.
- [x] Sem animacao automatica.

## Admin

- [x] Nenhuma nova acao admin criada.
- [x] Admin read-only/moderacao existente consegue visualizar solicitacao local pelo anuncio/revisao.
- [x] Payload de revisao permanece oculto em DTO admin.

## Testes e smokes

- [x] Teste unitario de payload valido.
- [x] Teste de aceite de termos ausente.
- [x] Teste de preco zero.
- [x] Teste de telefone no titulo.
- [x] Teste de rede social no titulo.
- [x] Teste de cidade ausente.
- [x] Teste de campo perigoso.
- [x] Teste de WhatsApp sintetico ja existente.
- [x] Smoke HTTP cria solicitacao local.
- [x] Smoke HTTP confirma que o anuncio nao aparece publicamente.
- [x] Smoke HTTP confirma leitura admin da solicitacao/revisao.

## Prints

- [x] Desktop `/anunciar`: `docs/v3/evidencias/bloco-26/anunciar-desktop-inicial.png`.
- [x] Mobile `/anunciar`: `docs/v3/evidencias/bloco-26/anunciar-mobile-inicial.png`.
- [x] Estado inicial: `docs/v3/evidencias/bloco-26/anunciar-desktop-inicial.png`.
- [x] Validacao de erro: `docs/v3/evidencias/bloco-26/anunciar-validacao-erro.png`.
- [x] Sucesso: `docs/v3/evidencias/bloco-26/anunciar-sucesso.png`.
- [x] Upload futuro: `docs/v3/evidencias/bloco-26/anunciar-upload-futuro.png`.
- [x] Admin mostrando solicitacao local: `docs/v3/evidencias/bloco-26/admin-solicitacao-bloco-26.png`.

## Proibicoes preservadas

- [x] Sem producao.
- [x] Sem VPS.
- [x] Sem banco de producao.
- [x] Sem dados reais.
- [x] Sem dump real.
- [x] Sem migration.
- [x] Sem SQL de schema.
- [x] Sem importador real.
- [x] Sem API externa.
- [x] Sem OpenAI.
- [x] Sem Efi real.
- [x] Sem remote.
- [x] Sem push.
- [x] Sem commit depois do checkpoint local.

## Pendencias

- [ ] Revisao Pro de campos definitivos do cadastro.
- [ ] Upload real de midia em fase futura.
- [ ] Publicacao final apos moderacao em fase futura.
- [ ] Politica final de comunicacao com anunciante em fase futura.
- [ ] Paridade final com wizard de producao antes de homologacao.
